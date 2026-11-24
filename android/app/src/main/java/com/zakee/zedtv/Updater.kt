package com.zakee.zedtv

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** Checks the GitHub release for a newer build and installs it (no adb needed). */
object Updater {
    private const val API = "https://api.github.com/repos/mohamedzakee/zedtv/releases/tags/app"

    data class Release(val build: Int, val apkUrl: String)

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        try {
            val o = JSONObject(Repo.fetchText(API))
            val build = Regex("""build (\d+)""").find(o.optString("name"))?.groupValues?.get(1)?.toInt()
            val assets = o.optJSONArray("assets")
            if (build == null || assets == null || assets.length() == 0) null
            else Release(build, assets.getJSONObject(0).getString("browser_download_url"))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(ctx: Context, rel: Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "zedtv.apk")
        Repo.http.newCall(Request.Builder().url(rel.apkUrl).build()).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            val body = res.body!!
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done * 100 / total).toInt())
                    }
                }
            }
        }
        out
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
