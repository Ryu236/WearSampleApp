package com.ryu236.wearsampleapp

import android.app.Application
import timber.log.Timber.DebugTree
import timber.log.Timber


class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(DebugTree())
    }
}