package com.adel.subha

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.adel.subha.audio.Feedback
import com.adel.subha.data.AppState
import com.adel.subha.ui.AdhkarScreen
import com.adel.subha.ui.BeadsScreen
import com.adel.subha.ui.MainScreen
import com.adel.subha.ui.SettingsScreen
import com.adel.subha.ui.SubhaTheme

enum class Screen { MAIN, ADHKAR, BEADS, SETTINGS }

class MainActivity : ComponentActivity() {
    private lateinit var feedback: Feedback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val state = AppState(applicationContext)
        feedback = Feedback(applicationContext)
        setContent { SubhaTheme(state.background) { App(state, feedback) } }
    }

    override fun onDestroy() {
        feedback.release()
        super.onDestroy()
    }
}

@Composable
private fun App(state: AppState, fb: Feedback) {
    val stack = remember { mutableStateListOf(Screen.MAIN) }
    val top = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }

    val view = LocalView.current
    val keep = state.keepOn && top == Screen.MAIN
    DisposableEffect(keep) {
        view.keepScreenOn = keep
        onDispose { view.keepScreenOn = false }
    }

    when (top) {
        Screen.MAIN -> MainScreen(state, fb, onAdhkar = { go(Screen.ADHKAR) }, onBeads = { go(Screen.BEADS) }, onSettings = { go(Screen.SETTINGS) })
        Screen.ADHKAR -> AdhkarScreen(state, onBack = { back() })
        Screen.BEADS -> BeadsScreen(state, onBack = { back() })
        Screen.SETTINGS -> SettingsScreen(state, onBack = { back() }, onBeads = { go(Screen.BEADS) }, onAdhkar = { go(Screen.ADHKAR) })
    }
}
