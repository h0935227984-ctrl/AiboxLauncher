package com.aibox.launcher

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 固定淺色(白色)。想改回深色,把 MODE_NIGHT_NO 改成 MODE_NIGHT_YES
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
