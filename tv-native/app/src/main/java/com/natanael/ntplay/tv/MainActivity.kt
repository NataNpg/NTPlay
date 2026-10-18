package com.natanael.ntplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.view.KeyEvent

class MainActivity : ComponentActivity() {
    private var state: AppState? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NtTheme {
                val st = remember { AppState(applicationContext).also { state = it } }
                LaunchedEffect(Unit) { st.start() }
                BackHandler(enabled = st.canBack && st.play == null && !st.showLists && !st.showSearch) { st.back() }
                App(st)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val st = state
        val p = st?.play
        if (st != null && p != null) {
            val up = keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
            val down = keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
            if (up || down) {
                val n = if (up) st.next(p) else st.prev(p)
                if (n != null) st.play = n
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
