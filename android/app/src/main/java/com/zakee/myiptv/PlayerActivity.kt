package com.zakee.myiptv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ID = "channel_id"
        private const val START_TIMEOUT_MS = 8_000L   // no picture by then -> try the backup
        private const val STALL_TIMEOUT_MS = 15_000L  // stuck buffering mid-show -> reconnect
    }

    private lateinit var playerView: PlayerView
    private lateinit var info: View
    private lateinit var scrim: View
    private lateinit var message: TextView
    private lateinit var panel: View
    private lateinit var panelList: RecyclerView
    private lateinit var numbers: NumberEntry
    private var player: ExoPlayer? = null

    private val channels get() = Repo.channels
    private var index = 0
    private var streams: List<Stream> = emptyList()
    private var streamIndex = 0
    private var forceHls = false
    private var reconnects = 0
    private var starting = false

    private val handler = Handler(Looper.getMainLooper())
    private val time = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val hideInfo = Runnable {
        info.animate().alpha(0f).setDuration(300).withEndAction { info.visibility = View.GONE }.start()
        scrim.animate().alpha(0f).setDuration(300).start()
    }
    private val startTimeout = Runnable { fail("no picture after ${START_TIMEOUT_MS / 1000}s") }
    private val stallTimeout = Runnable {
        if (reconnects++ < 1) startStream() else fail("kept buffering")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (channels.isEmpty()) { finish(); return }
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        info = findViewById(R.id.info)
        scrim = findViewById(R.id.scrim)
        message = findViewById(R.id.message)
        panel = findViewById(R.id.panel)
        panelList = findViewById(R.id.panelList)
        numbers = NumberEntry(findViewById(R.id.numberEntry)) { n ->
            Repo.byNumber(n)?.let { play(channels.indexOf(it)) }
        }
        val id = intent.getStringExtra(EXTRA_ID)
        index = channels.indexOfFirst { it.id == id }.coerceAtLeast(0)

        panelList.layoutManager = LinearLayoutManager(this)
        panelList.itemAnimator = null
        panelList.adapter = PanelAdapter(channels, { index }) { i -> closePanel(); play(i) }
    }

    override fun onStart() {
        super.onStart()
        if (channels.isEmpty()) return
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_500, 3_000).build()
        player = ExoPlayer.Builder(this).setLoadControl(load).build().also { p ->
            playerView.player = p
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            starting = false
                            handler.removeCallbacks(startTimeout)
                            handler.removeCallbacks(stallTimeout)
                            message.visibility = View.GONE
                            streams.getOrNull(streamIndex)?.let { Repo.streamHealth[it.url] = true }
                            Repo.setHealth(channels[index].id, true)
                        }
                        Player.STATE_BUFFERING -> if (!starting) {
                            handler.removeCallbacks(stallTimeout)
                            handler.postDelayed(stallTimeout, STALL_TIMEOUT_MS)
                        }
                        else -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        p.seekToDefaultPosition(); p.prepare(); return
                    }
                    fail(error.errorCodeName)
                }
            })
        }
        play(index)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        if (::numbers.isInitialized) numbers.cancel()
        player?.release()
        player = null
    }

    private fun play(i: Int) {
        if (channels.isEmpty()) return
        index = (i + channels.size) % channels.size
        val ch = channels[index]
        streams = Repo.orderedStreams(ch)
        streamIndex = 0
        forceHls = false
        reconnects = 0
        Prefs.setLastChannel(this, ch.id)
        panelList.adapter?.notifyDataSetChanged()
        showInfo()
        startStream()
    }

    private fun startStream() {
        val p = player ?: return
        val s = streams.getOrNull(streamIndex) ?: return
        val ds = OkHttpDataSource.Factory(Repo.http).setUserAgent(s.userAgent ?: HealthCheck.DEFAULT_UA)
        s.referrer?.let { ds.setDefaultRequestProperties(mapOf("Referer" to it)) }
        val item = MediaItem.Builder().setUri(s.url).apply {
            if (forceHls || s.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        handler.removeCallbacks(stallTimeout)
        handler.removeCallbacks(startTimeout)
        starting = true
        handler.postDelayed(startTimeout, START_TIMEOUT_MS)
        p.setMediaSource(DefaultMediaSourceFactory(this).setDataSourceFactory(ds).createMediaSource(item))
        p.prepare()
        p.playWhenReady = true
    }

    /** Current stream failed: retry as HLS once, then move to the next backup, then give up. */
    private fun fail(reason: String) {
        starting = false
        handler.removeCallbacks(startTimeout)
        handler.removeCallbacks(stallTimeout)
        val ch = channels.getOrNull(index) ?: return
        val s = streams.getOrNull(streamIndex) ?: return
        when {
            !forceHls && !s.url.contains(".m3u8", ignoreCase = true) && reason != "kept buffering" -> forceHls = true
            streamIndex + 1 < streams.size -> {
                Repo.streamHealth[s.url] = false
                streamIndex++; forceHls = false; reconnects = 0
                showMessage(getString(R.string.trying_backup))
            }
            else -> {
                Repo.streamHealth[s.url] = false
                Repo.setHealth(ch.id, false)
                Repo.logFailure(this, ch, reason)
                player?.stop()
                showMessage(getString(R.string.not_available))
                return
            }
        }
        startStream()
    }

    private fun showInfo() {
        val ch = channels[index]
        findViewById<TextView>(R.id.infoName).text = ch.name
        findViewById<TextView>(R.id.infoSub).text = "${ch.number}  ·  ${ch.group}"
        findViewById<ImageView>(R.id.infoLogo).load(ch.logo) { error(R.drawable.ic_tv) }
        val (now, next) = Repo.guide.nowNext(ch.id)
        val nowView = findViewById<TextView>(R.id.infoNow)
        val nextView = findViewById<TextView>(R.id.infoNext)
        val bar = findViewById<ProgressBar>(R.id.infoProgress)
        if (now != null) {
            nowView.text = getString(R.string.now_fmt, now.title, time.format(Date(now.start * 1000)), time.format(Date(now.stop * 1000)))
            bar.progress = (now.progress(System.currentTimeMillis() / 1000) * 100).toInt()
            nowView.visibility = View.VISIBLE; bar.visibility = View.VISIBLE
        } else {
            nowView.visibility = View.GONE; bar.visibility = View.GONE
        }
        nextView.text = next?.let { getString(R.string.next_fmt, time.format(Date(it.start * 1000)), it.title) } ?: ""
        nextView.visibility = if (next == null) View.GONE else View.VISIBLE
        message.visibility = View.GONE
        info.visibility = View.VISIBLE
        info.animate().cancel(); info.alpha = 1f
        scrim.animate().cancel(); scrim.alpha = 1f
        handler.removeCallbacks(hideInfo)
        handler.postDelayed(hideInfo, 5000)
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }

    // ---- channel panel -------------------------------------------------------------------

    private val panelOpen get() = panel.visibility == View.VISIBLE

    private fun openPanel() {
        handler.removeCallbacks(hideInfo); hideInfo.run()
        panel.visibility = View.VISIBLE
        panel.translationX = -panel.width.toFloat().coerceAtLeast(400f)
        panel.animate().translationX(0f).setDuration(220).start()
        panelList.adapter?.notifyDataSetChanged()
        panelList.scrollToPosition(index)
        panelList.post { panelList.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus() }
    }

    private fun closePanel() {
        panel.animate().translationX(-panel.width.toFloat()).setDuration(180)
            .withEndAction { panel.visibility = View.GONE }.start()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (numbers.handle(keyCode)) return true
        if (panelOpen) {
            return when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_RIGHT -> { closePanel(); true }
                else -> super.onKeyDown(keyCode, event)
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> play(index - 1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> play(index + 1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE -> openPanel()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_DPAD_RIGHT -> showInfo()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
