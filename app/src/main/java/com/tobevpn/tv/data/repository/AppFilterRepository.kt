package com.tobevpn.tv.data.repository

import com.tobevpn.tv.data.local.PrefsDataStore
import com.tobevpn.tv.domain.model.AppFilterMode
import com.tobevpn.tv.domain.model.AppFilterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFilterRepository @Inject constructor(
    private val prefs: PrefsDataStore,
) {
    private val writeMutex = Mutex()
    private val writeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun observeMode(): Flow<AppFilterMode> = prefs.appFilterMode.map { parseMode(it) }

    fun observeSelectedPackages(): Flow<Set<String>> =
        prefs.appFilterPackages
            .map { it ?: emptySet() }
            .distinctUntilChanged()

    fun observeState(): Flow<AppFilterState> =
        observeMode().combine(observeSelectedPackages()) { mode, set ->
            AppFilterState(mode = mode, selectedPackages = set)
        }

    suspend fun getSnapshot(): AppFilterState {
        val mode = parseMode(prefs.getAppFilterMode())
        val selected = writeMutex.withLock { prefs.getAppFilterPackages() ?: emptySet() }
        return AppFilterState(mode = mode, selectedPackages = selected)
    }

    fun setMode(mode: AppFilterMode) {
        writeScope.launch { prefs.setAppFilterMode(mode.name) }
    }

    fun toggle(packageName: String) {
        writeScope.launch {
            writeMutex.withLock {
                val current = prefs.getAppFilterPackages() ?: emptySet()
                val updated = if (packageName in current) current - packageName else current + packageName
                prefs.setAppFilterPackages(updated)
            }
        }
    }

    fun setSelected(packageNames: Collection<String>) {
        writeScope.launch {
            writeMutex.withLock { prefs.setAppFilterPackages(normalizePackages(packageNames)) }
        }
    }

    fun clearAll() {
        writeScope.launch {
            writeMutex.withLock { prefs.setAppFilterPackages(emptySet()) }
        }
    }

    private fun normalizePackages(packageNames: Collection<String>): Set<String> =
        packageNames.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    private fun parseMode(raw: String?): AppFilterMode = when (raw) {
        AppFilterMode.WHITELIST.name -> AppFilterMode.WHITELIST
        AppFilterMode.BLACKLIST.name -> AppFilterMode.BLACKLIST
        else -> AppFilterMode.OFF
    }
}
