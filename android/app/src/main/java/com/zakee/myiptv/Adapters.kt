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

/** Vertical list of categories; each one is a horizontal row of glass cards (Apple TV style). */
class RowAdapter(
    private val rows: List<Row>,
    private val onFocus: (Channel) -> Unit,
    private val onClick: (Channel) -> Unit,
) : RecyclerView.Adapter<RowAdapter.VH>() {
    private val pool = RecyclerView.RecycledViewPool()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.rowTitle)
        val list: RecyclerView = view.findViewById(R.id.rowList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))
        vh.list.layoutManager = LinearLayoutManager(parent.context, RecyclerView.HORIZONTAL, false)
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
}

class ChannelAdapter(
    private val items: List<Channel>,
    private val onFocus: (Channel) -> Unit,
    private val onClick: (Channel) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = items[position]
        holder.name.text = ch.name
        holder.logo.load(ch.logo) {
            crossfade(true)
            placeholder(R.drawable.ic_tv)
            error(R.drawable.ic_tv)
        }
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
