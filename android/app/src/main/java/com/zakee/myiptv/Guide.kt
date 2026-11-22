package com.zakee.myiptv

import org.json.JSONObject

/** Now/next TV guide built nightly by the repo's "Update TV guide" workflow (epg.json). */
class Guide(private val programmes: Map<String, List<Programme>>) {

    val isEmpty get() = programmes.isEmpty()

    fun nowNext(channelId: String, now: Long = System.currentTimeMillis() / 1000): Pair<Programme?, Programme?> {
        val list = programmes[channelId] ?: return null to null
        val i = list.indexOfFirst { now >= it.start && now < it.stop }
        return if (i >= 0) list[i] to list.getOrNull(i + 1) else null to list.firstOrNull { it.start > now }
    }

    companion object {
        val EMPTY = Guide(emptyMap())

        fun parse(json: String): Guide {
            val channels = JSONObject(json).optJSONObject("channels") ?: return EMPTY
            val map = HashMap<String, List<Programme>>()
            for (id in channels.keys()) {
                val arr = channels.getJSONArray(id)
                map[id] = (0 until arr.length()).map {
                    val p = arr.getJSONArray(it)
                    Programme(p.getLong(0), p.getLong(1), p.getString(2))
                }.sortedBy { it.start }
            }
            return Guide(map)
        }
    }
}
