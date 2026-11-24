package com.zakee.myiptv

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    private lateinit var rows: RecyclerView
    private lateinit var ambient: ImageView
    private lateinit var heroName: TextView
    private lateinit var heroSub: TextView
    private lateinit var heroNow: TextView
    private lateinit var heroNext: TextView
    private lateinit var status: TextView
    private lateinit var updateBtn: TextView
    private lateinit var numbers: NumberEntry

    private var shownChannels: List<Channel>? = null
    private var rowAdapter: RowAdapter? = null
    private var focused: Channel? = null
    private var lastAmbient: String? = null
    private var installing = false
    private val time = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        rows = findViewById(R.id.rows)
        ambient = findViewById(R.id.ambient)
        heroName = findViewById(R.id.heroName)
        heroSub = findViewById(R.id.heroSub)
        heroNow = findViewById(R.id.heroNow)
        heroNext = findViewById(R.id.heroNext)
        status = findViewById(R.id.status)
        updateBtn = findViewById(R.id.updateBtn)
        numbers = NumberEntry(findViewById(R.id.numberEntry)) { n -> Repo.byNumber(n)?.let { openPlayer(it) } }

        // Apple TV style: the focused row slides up to a fixed spot near the top, so the
        // last row always ends up fully on screen with space below it.
        rows.layoutManager = object : LinearLayoutManager(this) {
            override fun requestChildRectangleOnScreen(
                parent: RecyclerView, child: View, rect: android.graphics.Rect, immediate: Boolean, focusedChildVisible: Boolean,
            ): Boolean {
                val dy = getDecoratedTop(child) - parent.paddingTop
                if (dy == 0) return false
                if (immediate) parent.scrollBy(0, dy) else parent.smoothScrollBy(0, dy)
                return true
            }
        }
        rows.itemAnimator = null
        // Empty space after the last row (as part of the content, so nothing needs to draw outside the list).
        val bottomSpace = (80 * resources.displayMetrics.density).toInt()
        rows.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out: android.graphics.Rect, v: View, parent: RecyclerView, state: RecyclerView.State) {
                if (parent.getChildAdapterPosition(v) == state.itemCount - 1) out.bottom = bottomSpace
            }
        })
        rows.setItemViewCacheSize(8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !App.isLowRam(this)) {
            ambient.setRenderEffect(RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP))
        } else {
            ambient.visibility = View.GONE // blur is costly on older / low-memory TVs
        }

        updateBtn.setOnClickListener { vm.state.value.update?.let { installUpdate(it) } }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { render(it) } }
                launch { vm.health.collect { rowAdapter?.refreshLive(rows) } }
            }
        }
        if (savedInstanceState == null) Repo.crashLog(this)?.let { showCrash(it) }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshIfStale()
        rowAdapter?.refreshLive(rows)
        focused?.let { showHero(it) }
    }

    private fun render(s: MainViewModel.UiState) {
        status.visibility = if (s.channels.isEmpty()) View.VISIBLE else View.GONE
        status.setText(if (s.error) R.string.load_failed else R.string.loading)
        updateBtn.visibility = if (s.update != null) View.VISIBLE else View.GONE

        if (s.channels.isNotEmpty() && s.channels !== shownChannels) {
            val hadFocus = rows.hasFocus()
            shownChannels = s.channels
            val grouped = s.channels.groupBy { it.group }.map { (g, chs) -> Row(g, chs) }
            rowAdapter = RowAdapter(grouped, ::showHero, ::openPlayer)
            rows.adapter = rowAdapter
            showHero(focused?.let { f -> s.channels.firstOrNull { it.id == f.id } } ?: s.channels.first())
            if (!hadFocus) rows.post { firstCard()?.requestFocus() }
            maybeResumeLastChannel(s.channels)
        } else {
            rowAdapter?.refreshLive(rows)
            focused?.let { showHero(it) }
        }
    }

    /** Opening the app goes straight back to the last channel, like a normal TV. */
    private fun maybeResumeLastChannel(list: List<Channel>) {
        if (vm.autoResumeDone) return
        vm.autoResumeDone = true
        val id = Prefs.lastChannel(this) ?: return
        list.firstOrNull { it.id == id }?.let { openPlayer(it) }
    }

    private fun firstCard(): View? {
        val row = rows.findViewHolderForAdapterPosition(0) as? RowAdapter.VH ?: return null
        return (row.list.findViewHolderForAdapterPosition(0) as? ChannelAdapter.VH)?.card
    }

    private fun showHero(ch: Channel) {
        focused = ch
        heroName.text = ch.name
        val dead = Repo.health.value[ch.id] == false
        heroSub.text = "${ch.number}  ·  ${ch.group}  ·  ${getString(if (dead) R.string.offline else R.string.live)}"
        val (now, next) = Repo.guide.nowNext(ch.id)
        heroNow.text = now?.let { getString(R.string.now_fmt, it.title, time.format(Date(it.start * 1000)), time.format(Date(it.stop * 1000))) }
            ?: getString(R.string.press_ok)
        heroNext.text = next?.let { getString(R.string.next_fmt, time.format(Date(it.start * 1000)), it.title) } ?: ""
        heroNext.visibility = if (next == null) View.GONE else View.VISIBLE
        if (ambient.visibility == View.VISIBLE && ch.logo != lastAmbient) {
            lastAmbient = ch.logo
            ambient.load(ch.logo) { crossfade(400) }
        }
    }

    private fun openPlayer(ch: Channel) {
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_ID, ch.id))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (numbers.handle(keyCode)) return true
        // Info screen lives on the remote's Menu / Info button instead of an on-screen button.
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) { showDiagnostics(); return true }
        return super.onKeyDown(keyCode, event)
    }

    // ---- update / diagnostics ------------------------------------------------------------

    private fun installUpdate(rel: Updater.Release) {
        if (installing) return
        installing = true
        lifecycleScope.launch {
            try {
                val apk = Updater.download(this@MainActivity, rel) { p ->
                    runOnUiThread { updateBtn.text = getString(R.string.downloading, p) }
                }
                updateBtn.setText(R.string.update_available)
                Updater.install(this@MainActivity, apk)
            } catch (e: Exception) {
                updateBtn.setText(R.string.update_failed)
            } finally {
                installing = false
            }
        }
    }

    private fun showDiagnostics() {
        val health = Repo.health.value
        val offline = Repo.channels.filter { health[it.id] == false }.joinToString { it.name }
        val failures = Repo.failures(this).take(15).joinToString("\n").ifEmpty { "None" }
        val msg = buildString {
            appendLine("Version ${BuildConfig.VERSION_NAME}")
            appendLine("${Repo.channels.size} channels, ${health.count { it.value }} reachable from this TV")
            appendLine("TV guide: ${if (Repo.guide.isEmpty) "not loaded" else "loaded"}")
            appendLine()
            appendLine("Offline now: ${offline.ifEmpty { "none" }}")
            appendLine()
            appendLine("Recent playback problems:")
            append(failures)
        }
        AlertDialog.Builder(this, R.style.ZedDialog).setTitle(R.string.info).setMessage(msg)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showCrash(log: String) {
        AlertDialog.Builder(this, R.style.ZedDialog).setTitle(R.string.crashed)
            .setMessage(log.take(1500))
            .setPositiveButton(android.R.string.ok) { _, _ -> Repo.clearCrashLog(this) }
            .setOnDismissListener { Repo.clearCrashLog(this) }
            .show()
    }
}
