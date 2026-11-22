package com.zakee.myiptv

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var rows: RecyclerView
    private lateinit var ambient: ImageView
    private lateinit var heroName: TextView
    private lateinit var heroSub: TextView
    private lateinit var status: TextView
    private var lastLoad = 0L
    private var lastAmbient: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        rows = findViewById(R.id.rows)
        ambient = findViewById(R.id.ambient)
        heroName = findViewById(R.id.heroName)
        heroSub = findViewById(R.id.heroSub)
        status = findViewById(R.id.status)

        rows.layoutManager = LinearLayoutManager(this)
        rows.itemAnimator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Real frosted look on Android 12+; older TVs just get a dimmed backdrop.
            ambient.setRenderEffect(RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP))
        } else {
            ambient.alpha = 0.18f
        }

        val cached = PlaylistRepo.cached(this)
        if (cached.isNotEmpty()) show(cached)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (System.currentTimeMillis() - lastLoad > 6 * 60 * 60 * 1000L) refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                val list = PlaylistRepo.download(this@MainActivity)
                lastLoad = System.currentTimeMillis()
                if (list.isNotEmpty() && list.map { it.streams } != PlaylistRepo.channels.map { it.streams }) show(list)
            } catch (e: Exception) {
                if (PlaylistRepo.channels.isEmpty()) {
                    status.visibility = View.VISIBLE
                    status.setText(R.string.load_failed)
                }
            }
        }
    }

    private fun show(list: List<Channel>) {
        val hadFocus = rows.hasFocus()
        PlaylistRepo.channels = list
        status.visibility = View.GONE
        val grouped = list.groupBy { it.group }.map { (g, chs) -> Row(g, chs) }
        rows.adapter = RowAdapter(grouped, ::focusChannel, ::openPlayer)
        list.firstOrNull()?.let { focusChannel(it) }
        if (!hadFocus) rows.post { firstCard()?.requestFocus() }
    }

    private fun firstCard(): View? {
        val row = rows.findViewHolderForAdapterPosition(0) as? RowAdapter.VH ?: return null
        return (row.list.findViewHolderForAdapterPosition(0) as? ChannelAdapter.VH)?.card
    }

    private fun focusChannel(ch: Channel) {
        heroName.text = ch.name
        heroSub.text = "${ch.group}  ·  ${getString(R.string.live)}"
        if (ch.logo != lastAmbient) {
            lastAmbient = ch.logo
            ambient.load(ch.logo) { crossfade(400) }
        }
    }

    private fun openPlayer(ch: Channel) {
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_INDEX, PlaylistRepo.channels.indexOf(ch)))
    }
}
