package com.zakee.myiptv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var groupsView: RecyclerView
    private lateinit var channelsView: RecyclerView
    private lateinit var groupTitle: TextView
    private lateinit var status: TextView

    private var groups: List<String> = emptyList()
    private var groupAdapter: GroupAdapter? = null
    private val channelAdapter = ChannelAdapter(emptyList()) { openPlayer(it) }
    private var lastLoad = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        groupsView = findViewById(R.id.groups)
        channelsView = findViewById(R.id.channels)
        groupTitle = findViewById(R.id.groupTitle)
        status = findViewById(R.id.status)

        groupsView.layoutManager = LinearLayoutManager(this)
        groupsView.itemAnimator = null // keeps D-pad focus steady when the selection changes
        val cols = if (resources.configuration.screenWidthDp >= 900) 4 else 3
        channelsView.layoutManager = GridLayoutManager(this, cols)
        channelsView.adapter = channelAdapter

        val cached = PlaylistRepo.cached(this)
        if (cached.isNotEmpty()) show(cached)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Pick up the nightly update if the app was left open.
        if (System.currentTimeMillis() - lastLoad > 6 * 60 * 60 * 1000L) refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                val list = PlaylistRepo.download(this@MainActivity)
                lastLoad = System.currentTimeMillis()
                if (list.isNotEmpty()) show(list)
            } catch (e: Exception) {
                if (PlaylistRepo.channels.isEmpty()) {
                    status.visibility = View.VISIBLE
                    status.setText(R.string.load_failed)
                }
            }
        }
    }

    private fun show(list: List<Channel>) {
        val keepGroup = groupAdapter?.let { groups.getOrNull(it.selected) }
        PlaylistRepo.channels = list
        groups = list.map { it.group }.distinct()
        status.visibility = View.GONE
        val start = groups.indexOf(keepGroup).coerceAtLeast(0)
        groupAdapter = GroupAdapter(groups) { selectGroup(it) }.also { it.selected = start }
        groupsView.adapter = groupAdapter
        selectGroup(start)
        if (keepGroup == null) groupsView.post { groupsView.findViewHolderForAdapterPosition(start)?.itemView?.requestFocus() }
    }

    private fun selectGroup(index: Int) {
        if (index !in groups.indices) return
        groupAdapter?.selected = index
        val g = groups[index]
        val items = PlaylistRepo.channels.filter { it.group == g }
        groupTitle.text = "$g  ·  ${items.size} channels"
        channelAdapter.submit(items)
        channelsView.scrollToPosition(0)
    }

    private fun openPlayer(ch: Channel) {
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_INDEX, PlaylistRepo.channels.indexOf(ch)))
    }
}
