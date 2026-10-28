package com.tchatz.music
import android.app.Application
import androidx.room.Room
import com.tchatz.music.data.*
import kotlinx.coroutines.*
class MusicApp:Application() {
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 val db by lazy { Room.databaseBuilder(this,MusicDatabase::class.java,"tchatz.db").build() }
 val preferences by lazy { Preferences(this) }
}
