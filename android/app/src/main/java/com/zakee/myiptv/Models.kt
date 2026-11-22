package com.zakee.myiptv

data class Stream(val url: String, val userAgent: String?, val referrer: String?)

data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val group: String,
    val logo: String?,
    val streams: List<Stream>,
)

/** A TV programme; times are epoch seconds. */
data class Programme(val start: Long, val stop: Long, val title: String) {
    fun progress(now: Long): Float =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}
