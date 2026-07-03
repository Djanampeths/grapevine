package com.grapevine.grapevine

import android.app.Application
import android.content.Context

class GrapevineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        GrapevineNotifications.createChannel(this)
    }
}

object AppContext {
    private lateinit var appContext: Context
    fun init(context: Context) { appContext = context.applicationContext }
    fun get(): Context = appContext
}
