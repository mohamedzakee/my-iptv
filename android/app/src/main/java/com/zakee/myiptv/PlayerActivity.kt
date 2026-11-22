package com.zakee.myiptv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.load

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_INDEX = "index"
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }

    private lateinit var playerView: PlayerView
    private lateinit var info: View
    private lateinit var message: TextView
    private var player: ExoPlayer? = null

    private val channels get() = PlaylistRepo.channels
    private var index = 0
    private var streamIndex = 0
    private var forceHls = false

    private val handler = Handler(Looper.getMainLooper())
    private val hideInfo = Runnable { info.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (channels.isEmpty()) { finish(); return }
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        info = findViewById(R.id.info)
        message = findViewById(R.id.message)
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, channels.size - 1)
    }

    override fun onStart() {
        super.onStart()
        if (channels.isEmpty()) return
        player = ExoPlayer.Builder(this).build().also { p ->
            playerView.player = p
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) message.visibility = View.GONE
                }
                override fun onPlayerError(error: PlaybackException) = handleError(error)
            })
        }
        play(index)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    private fun play(i: Int) {
        index = (i + channels.size) % channels.size
        streamIndex = 0
        forceHls = false
        showInfo()
        startStream()
    }

    private fun startStream() {
        val p = player ?: return
        val ch = channels[index]
        val s = ch.streams[streamIndex]
        val ds = OkHttpDataSource.Factory(PlaylistRepo.http).setUserAgent(s.userAgent ?: DEFAULT_UA)
        s.referrer?.let { ds.setDefaultRequestProperties(mapOf("Referer" to it)) }
        val item = MediaItem.Builder().setUri(s.url).apply {
            if (forceHls || s.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        p.setMediaSource(DefaultMediaSourceFactory(this).setDataSourceFactory(ds).createMediaSource(item))
        p.prepare()
        p.playWhenReady = true
    }

    private fun handleError(error: PlaybackException) {
        val p = player ?: return
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            p.seekToDefaultPosition(); p.prepare(); return
        }
        val ch = channels[index]
        val url = ch.streams[streamIndex].url
        when {
            // Some live links have no file extension; retry them as HLS once.
            !forceHls && !url.contains(".m3u8", ignoreCase = true) -> forceHls = true
            streamIndex + 1 < ch.streams.size -> { streamIndex++; forceHls = false; showMessage(getString(R.string.trying_backup)) }
            else -> { showMessage(getString(R.string.not_available)); return }
        }
        startStream()
    }

    private fun showInfo() {
        val ch = channels[index]
        findViewById<TextView>(R.id.infoName).text = ch.name
        findViewById<TextView>(R.id.infoSub).text = "${ch.group}   ·   ▲ ▼ change channel"
        findViewById<ImageView>(R.id.infoLogo).load(ch.logo) { error(R.drawable.ic_tv) }
        message.visibility = View.GONE
        info.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        handler.postDelayed(hideInfo, 4000)
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> play(index - 1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> play(index + 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> showInfo()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
