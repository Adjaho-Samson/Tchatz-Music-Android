package com.tchatz.klassio.data

import android.content.Context
import com.tchatz.klassio.data.local.KlassioDatabase
import com.tchatz.klassio.data.prefs.SessionStore
import com.tchatz.klassio.data.remote.KlassioApi
import com.tchatz.klassio.data.repo.KlassioRepository

/** Injection de dépendances simple (pas de framework externe, APK léger). */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val api: KlassioApi = KlassioApi()
    val store: SessionStore = SessionStore(appContext)
    private val database: KlassioDatabase = KlassioDatabase.build(appContext)

    val repository: KlassioRepository = KlassioRepository(appContext, api, database, store)
}
