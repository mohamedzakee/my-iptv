package com.zakee.myiptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load

data class Row(val title: String, val channels: List<Channel>)

/** Payload for partial rebinds (guide/health changes) so the focused card keeps focus. */
const val PAYLOAD_LIVE = "live"

fun TextView.showNow(ch: Channel, dead: Boolean) {
    val now = Repo.guide.nowNext(ch.id).first
    text = when {
        dead -> context.getString(R.string.offline)
        now != null -> now.title
        else -> ""
    }
    visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
}

/** Vertical list of categories; each one is a horizontal row of glass cards (Apple TV style). */
class RowAdapter(
    private val rows: List<Row>,
    private val onFocus: (Channel) -> Unit,
    private val onClick: (Channel) -> Unit,
) : RecyclerView.Adapter<RowAdapter.VH>() {
    private val pool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 40) }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.rowTitle)
        val list: RecyclerView = view.findViewById(R.id.rowList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))
        vh.list.layoutManager = LinearLayoutManager(parent.context, RecyclerView.HORIZONTAL, false)
            .apply { initialPrefetchItemCount = 6 }
        vh.list.setRecycledViewPool(pool)
        vh.list.itemAnimator = null
        return vh
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.title.text = row.title
        holder.list.adapter = ChannelAdapter(row.channels, onFocus, onClick)
    }

    /** Refresh "now playing" and offline state on visible cards without losing focus. */
    fun refreshLive(rv: RecyclerView) {
        for (i in 0 until rv.childCount) {
            val vh = rv.getChildViewHolder(rv.getChildAt(i)) as? VH ?: continue
            vh.list.adapter?.let { it.notifyItemRangeChanged(0, it.itemCount, PAYLOAD_LIVE) }
        }
    }
}

class ChannelAdapter(
    private val items: List<Channel>,
    private val onFocus: (Channel) -> Unit,
    private val onClick: (Channel) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val logo: ImageView = view.findViewById(R.id.logo)
        val number: TextView = view.findViewById(R.id.number)
        val name: TextView = view.findViewById(R.id.name)
        val now: TextView = view.findViewById(R.id.now)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_LIVE)) bindLive(holder, items[position]) else super.onBindViewHolder(holder, position, payloads)
    }

    private fun bindLive(holder: VH, ch: Channel) {
        val dead = Repo.health.value[ch.id] == false
        holder.card.alpha = if (dead) 0.4f else 1f
        holder.now.showNow(ch, dead)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = items[position]
        holder.name.text = ch.name
        holder.number.text = ch.number.toString()
        holder.logo.load(ch.logo) {
            placeholder(R.drawable.ic_tv)
            error(R.drawable.ic_tv)
        }
        bindLive(holder, ch)
        holder.card.setOnClickListener { onClick(ch) }
        holder.card.setOnFocusChangeListener { _, has ->
            val item = holder.itemView
            val s = if (has) 1.12f else 1f
            item.animate().scaleX(s).scaleY(s).translationY(if (has) -6f else 0f)
                .setInterpolator(DecelerateInterpolator()).setDuration(180).start()
            item.translationZ = if (has) 24f else 0f
            holder.card.elevation = if (has) 18f else 0f
            holder.name.animate().alpha(if (has) 1f else 0.6f).setDuration(180).start()
            if (has) onFocus(ch)
        }
    }
}

/** Channel list shown inside the player (press ◀). */
class PanelAdapter(
    private val items: List<Channel>,
    private val current: () -> Int,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<PanelAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.pNumber)
        val logo: ImageView = view.findViewById(R.id.pLogo)
        val name: TextView = view.findViewById(R.id.pName)
        val now: TextView = view.findViewById(R.id.pNow)
        val playing: View = view.findViewById(R.id.pPlaying)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_panel_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = items[position]
        val dead = Repo.health.value[ch.id] == false
        holder.number.text = ch.number.toString()
        holder.name.text = ch.name
        holder.now.showNow(ch, dead)
        holder.itemView.alpha = if (dead) 0.45f else 1f
        holder.playing.visibility = if (position == current()) View.VISIBLE else View.INVISIBLE
        holder.logo.load(ch.logo) { error(R.drawable.ic_tv) }
        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }
}
