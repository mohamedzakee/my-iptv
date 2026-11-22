package com.zakee.myiptv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Single source of truth for channels, guide and stream health, shared by both screens. */
object Repo {
    private const val BASE = "https://raw.githubusercontent.com/mohamedzakee/my-iptv/main/"
    const val PLAYLIST_URL = BASE + "tv.m3u"
    const val GUIDE_URL = BASE + "epg.json"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile var channels: List<Channel> = emptyList()
    @Volatile var guide: Guide = Guide.EMPTY

    /** Channel id -> reachable from this TV's connection (absent = not checked yet). */
    private val _health = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val health: StateFlow<Map<String, Boolean>> = _health

    /** Stream url -> worked last time we tried it. */
    val streamHealth = ConcurrentHashMap<String, Boolean>()

    fun setHealth(id: String, ok: Boolean) = _health.update { it + (id to ok) }

    /** Known-good streams first, known-bad last. */
    fun orderedStreams(ch: Channel): List<Stream> =
        ch.streams.sortedBy { when (streamHealth[it.url]) { true -> 0; null -> 1; false -> 2 } }

    fun byNumber(n: Int) = channels.firstOrNull { it.number == n }

    // ---- loading -------------------------------------------------------------------------

    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    fun cachedChannels(ctx: Context): List<Channel> =
        runCatching { M3u.parse(file(ctx, "tv.m3u").readText()) }.getOrDefault(emptyList())

    fun cachedGuide(ctx: Context): Guide =
        runCatching { Guide.parse(file(ctx, "epg.json").readText()) }.getOrDefault(Guide.EMPTY)

    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code} for $url")
            it.body!!.string()
        }
    }

    suspend fun downloadChannels(ctx: Context): List<Channel> {
        val text = fetchText(PLAYLIST_URL + "?t=" + System.currentTimeMillis() / 60000)
        val list = M3u.parse(text)
        if (list.isNotEmpty()) withContext(Dispatchers.IO) { file(ctx, "tv.m3u").writeText(text) }
        return list
    }

    suspend fun downloadGuide(ctx: Context): Guide {
        val text = fetchText(GUIDE_URL + "?t=" + System.currentTimeMillis() / 600000)
        val g = Guide.parse(text)
        withContext(Dispatchers.IO) { file(ctx, "epg.json").writeText(text) }
        return g
    }

    // ---- diagnostics ---------------------------------------------------------------------

    private val stamp = SimpleDateFormat("dd MMM HH:mm", Locale.US)

    @Synchronized
    fun logFailure(ctx: Context, ch: Channel, reason: String) {
        val f = file(ctx, "failures.log")
        val lines = (if (f.exists()) f.readLines() else emptyList()) + "${stamp.format(Date())}  ${ch.name}: $reason"
        f.writeText(lines.takeLast(60).joinToString("\n"))
    }

    fun failures(ctx: Context): List<String> =
        file(ctx, "failures.log").takeIf { it.exists() }?.readLines()?.reversed() ?: emptyList()

    fun crashLog(ctx: Context): String? = file(ctx, "crash.log").takeIf { it.exists() }?.readText()

    fun clearCrashLog(ctx: Context) { file(ctx, "crash.log").delete() }
}
