package com.zakee.zedtv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Tests streams from the TV's own connection (the nightly GitHub check runs in the US and can't
 * see Sri Lankan geo-blocks). Channels with no reachable stream are shown faded as "Offline".
 */
object HealthCheck {
    const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    private val client: OkHttpClient by lazy {
        Repo.http.newBuilder().callTimeout(7, TimeUnit.SECONDS).build()
    }

    suspend fun run(channels: List<Channel>) = coroutineScope {
        val gate = Semaphore(8)
        channels.map { ch ->
            async(Dispatchers.IO) {
                var ok = false
                for (s in ch.streams) {
                    val good = gate.withPermit { probe(s) }
                    Repo.streamHealth[s.url] = good
                    if (good) { ok = true; break }
                }
                Repo.setHealth(ch.id, ok)
            }
        }.awaitAll()
    }

    fun probe(s: Stream): Boolean = try {
        val req = Request.Builder().url(s.url).header("User-Agent", s.userAgent ?: DEFAULT_UA)
            .apply { s.referrer?.let { header("Referer", it) } }.build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return false
            val buf = ByteArray(2048)
            val n = res.body?.byteStream()?.read(buf) ?: -1
            val isPlaylist = s.url.contains(".m3u8", true) ||
                res.header("Content-Type").orEmpty().contains("mpegurl", true)
            n > 0 && (!isPlaylist || String(buf, 0, n).contains("#EXTM3U"))
        }
    } catch (e: Exception) {
        false
    }
}
