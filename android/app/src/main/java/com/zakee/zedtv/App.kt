package com.zakee.zedtv

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Keep a crash log on the TV so problems can be seen in the Info screen.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                File(filesDir, "crash.log").writeText(
                    "ZED.TV ${BuildConfig.VERSION_NAME}\n${java.util.Date()}\n\n${e.stackTraceToString().take(4000)}"
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** Small, bounded image caches so cheap TV boxes don't run out of memory. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(if (isLowRam(this)) 0.10 else 0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("logos")).maxSizeBytes(40L * 1024 * 1024).build() }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()

    companion object {
        fun isLowRam(ctx: Context) =
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }
}
