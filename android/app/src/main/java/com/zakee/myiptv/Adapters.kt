package com.zakee.myiptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class GroupAdapter(
    private val groups: List<String>,
    private val onFocus: (Int) -> Unit,
) : RecyclerView.Adapter<GroupAdapter.VH>() {
    var selected = 0
        set(value) { val old = field; field = value; notifyItemChanged(old); notifyItemChanged(value) }

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false) as TextView)

    override fun getItemCount() = groups.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.text.text = groups[position]
        holder.text.isSelected = position == selected
        val pick = View.OnFocusChangeListener { _, has -> if (has) onFocus(holder.bindingAdapterPosition) }
        holder.text.onFocusChangeListener = pick
        holder.text.setOnClickListener { onFocus(holder.bindingAdapterPosition) }
    }
}

class ChannelAdapter(
    private var items: List<Channel>,
    private val onClick: (Channel) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
    }

    fun submit(list: List<Channel>) { items = list; notifyDataSetChanged() }

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
        holder.itemView.setOnClickListener { onClick(ch) }
        holder.itemView.setOnFocusChangeListener { v, has ->
            val s = if (has) 1.08f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            v.elevation = if (has) 12f else 0f
        }
    }
}
