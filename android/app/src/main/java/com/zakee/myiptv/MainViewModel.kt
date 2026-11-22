package com.zakee.myiptv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val channels: List<Channel> = emptyList(),
        val loading: Boolean = true,
        val error: Boolean = false,
        val guideVersion: Int = 0,
        val update: Updater.Release? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    val health: StateFlow<Map<String, Boolean>> = Repo.health

    var autoResumeDone = false
    private var lastRefresh = 0L
    private val ctx get() = getApplication<Application>()

    init {
        val cached = Repo.cachedChannels(ctx)
        Repo.guide = Repo.cachedGuide(ctx)
        if (cached.isNotEmpty()) {
            Repo.channels = cached
            _state.value = UiState(channels = cached, loading = false)
        }
        refresh()
        viewModelScope.launch {
            val rel = Updater.latest()
            if (rel != null && rel.build > BuildConfig.VERSION_CODE) _state.update { it.copy(update = rel) }
        }
    }

    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastRefresh > 6 * 60 * 60 * 1000L) refresh()
    }

    private fun refresh() {
        lastRefresh = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val list = Repo.downloadChannels(ctx)
                if (list.isNotEmpty() && list != Repo.channels) {
                    Repo.channels = list
                    _state.update { it.copy(channels = list, loading = false, error = false) }
                } else {
                    _state.update { it.copy(loading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = it.channels.isEmpty()) }
            }
            preloadLogos(Repo.channels)
            launch { HealthCheck.run(Repo.channels) }
            try {
                Repo.guide = Repo.downloadGuide(ctx)
                _state.update { it.copy(guideVersion = it.guideVersion + 1) }
            } catch (_: Exception) {
            }
        }
    }

    private fun preloadLogos(list: List<Channel>) {
        val loader = ctx.imageLoader
        list.mapNotNull { it.logo }.distinct().forEach { loader.enqueue(ImageRequest.Builder(ctx).data(it).build()) }
    }
}
