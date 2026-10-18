package com.natanael.ntplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NtTheme {
                val st = remember { AppState(applicationContext) }
                LaunchedEffect(Unit) { st.start() }
                BackHandler(enabled = st.canBack && st.play == null && !st.showLists && !st.showSearch) { st.back() }
                App(st)
            }
        }
    }
}
