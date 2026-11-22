package com.zakee.myiptv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class Stream(val url: String, val userAgent: String?, val referrer: String?)

data class Channel(
    val id: String,
    val name: String,
    val group: String,
    val logo: String?,
    val streams: MutableList<Stream> = mutableListOf(),
)

/** Parses the M3U playlist; streams with the same tvg-id become one channel with backups. */
object M3u {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val channels = LinkedHashMap<String, Channel>()
        var info: String? = null
        var ua: String? = null
        var ref: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> { info = line; ua = null; ref = null }
                line.startsWith("#EXTVLCOPT:http-user-agent=") -> ua = line.substringAfter("=")
                line.startsWith("#EXTVLCOPT:http-referrer=") -> ref = line.substringAfter("=")
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val inf = info ?: continue
                    val attrs = attrRegex.findAll(inf).associate { it.groupValues[1] to it.groupValues[2] }
                    val title = if (inf.contains("\",")) inf.substringAfterLast("\",") else inf.substringAfterLast(",")
                    val name = title.replace(Regex("""\s*[(\[].*?[)\]]"""), "").trim()
                    val id = attrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: name
                    val channel = channels.getOrPut(id) {
                        Channel(id, name, attrs["group-title"] ?: "Other", attrs["tvg-logo"]?.takeIf { it.isNotBlank() })
                    }
                    channel.streams.add(Stream(line, ua, ref))
                    info = null
                }
            }
        }
        return channels.values.toList()
    }
}

/** Downloads the playlist and keeps a copy so the app opens fast and works if GitHub is slow. */
object PlaylistRepo {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Channels currently shown; the player reads from here. */
    var channels: List<Channel> = emptyList()

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "tv.m3u")

    fun cached(ctx: Context): List<Channel> {
        val f = cacheFile(ctx)
        return if (f.exists()) M3u.parse(f.readText()) else emptyList()
    }

    suspend fun download(ctx: Context): List<Channel> = withContext(Dispatchers.IO) {
        val url = ctx.getString(R.string.playlist_url) + "?t=" + System.currentTimeMillis() / 60000
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            val text = res.body!!.string()
            val list = M3u.parse(text)
            if (list.isNotEmpty()) cacheFile(ctx).writeText(text)
            list
        }
    }
}
