package com.perdolique.poleparkla

import android.app.Application

class PoleParklaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

