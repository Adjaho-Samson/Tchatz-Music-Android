package com.tchatz.klassio

import android.app.Application
import com.tchatz.klassio.data.AppContainer
import com.tchatz.klassio.notify.NewDocumentWorker

class KlassioApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NewDocumentWorker.schedule(this)
    }
}
