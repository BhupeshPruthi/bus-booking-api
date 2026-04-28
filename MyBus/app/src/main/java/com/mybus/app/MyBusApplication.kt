package com.mybus.app

import android.app.Application
import com.mybus.app.data.remote.ApiBaseUrlHolder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class MyBusApplication : Application() {

    @Inject
    lateinit var apiBaseUrlHolder: ApiBaseUrlHolder

    override fun onCreate() {
        super.onCreate()
        runBlocking(Dispatchers.IO) {
            apiBaseUrlHolder.load()
        }
    }
}
