package com.mbaliga.csapp

import android.app.Application
import com.mbaliga.csapp.di.AppContainer
import com.mbaliga.csapp.work.PollScheduler

class CsAppApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PollScheduler.scheduleIfNotAlreadyScheduled(this)
    }
}
