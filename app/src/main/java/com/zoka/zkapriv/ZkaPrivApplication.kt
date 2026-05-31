package com.zoka.zkapriv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZkaPrivApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("ZkaPrivApp", "Application created")
    }
}