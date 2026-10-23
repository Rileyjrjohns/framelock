package dev.framelock.app

import android.app.Application

class FramelockApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph
    }
}
