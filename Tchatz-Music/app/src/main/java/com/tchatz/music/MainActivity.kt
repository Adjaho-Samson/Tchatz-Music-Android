package com.tchatz.music
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tchatz.music.ui.TchatzRoot
class MainActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  installSplashScreen(); super.onCreate(savedInstanceState); enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(Color.TRANSPARENT))
  setContent { TchatzRoot() }
 }
}
