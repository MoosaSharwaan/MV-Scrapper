package com.mvscrapper.downloader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User settings, saved on the device. Defaults follow the blueprint's rules. */
object Prefs {
    const val MIN_PARALLEL = 1
    const val MAX_PARALLEL = 5

    private lateinit var sp: SharedPreferences

    private val _parallel = MutableStateFlow(2)          // R3
    private val _wifiOnly = MutableStateFlow(true)       // R2
    private val _autoUpdate = MutableStateFlow(true)     // R5
    private val _noticeAccepted = MutableStateFlow(false) // R8

    val parallel: StateFlow<Int> = _parallel
    val wifiOnly: StateFlow<Boolean> = _wifiOnly
    val autoUpdate: StateFlow<Boolean> = _autoUpdate
    val noticeAccepted: StateFlow<Boolean> = _noticeAccepted

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _parallel.value = sp.getInt("parallel", 2).coerceIn(MIN_PARALLEL, MAX_PARALLEL)
        _wifiOnly.value = sp.getBoolean("wifi_only", true)
        _autoUpdate.value = sp.getBoolean("auto_update", true)
        _noticeAccepted.value = sp.getBoolean("notice_accepted", false)
    }

    fun setParallel(n: Int) {
        val v = n.coerceIn(MIN_PARALLEL, MAX_PARALLEL)
        _parallel.value = v
        sp.edit().putInt("parallel", v).apply()
        Downloads.pump() // a higher limit starts waiting items right away
    }

    fun setWifiOnly(on: Boolean) {
        _wifiOnly.value = on
        sp.edit().putBoolean("wifi_only", on).apply()
        Downloads.pump()
    }

    fun setAutoUpdate(on: Boolean) {
        _autoUpdate.value = on
        sp.edit().putBoolean("auto_update", on).apply()
    }

    fun acceptNotice() {
        _noticeAccepted.value = true
        sp.edit().putBoolean("notice_accepted", true).apply()
    }
}
