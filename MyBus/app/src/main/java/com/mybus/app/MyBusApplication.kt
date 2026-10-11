package com.mybus.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mybus.app.data.remote.ApiBaseUrlHolder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MyBusApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var apiBaseUrlHolder: ApiBaseUrlHolder

    @Inject
    lateinit var dns: Dns

    override fun onCreate() {
        super.onCreate()
        runBlocking(Dispatchers.IO) {
            apiBaseUrlHolder.load()
        }
    }

    // Images are served from the API host too, so they need the same DNS fallback.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { OkHttpClient.Builder().dns(dns).build() }
            .build()
}
