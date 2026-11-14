package com.drivesync.app

import android.app.Application
import com.drivesync.app.di.AppContainer

class DriveSyncApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
