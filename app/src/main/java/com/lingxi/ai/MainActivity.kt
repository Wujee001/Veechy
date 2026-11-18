package com.lingxi.ai

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.lingxi.ai.ui.App
import com.lingxi.ai.ui.theme.LingXiTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LingXiTheme {
                App()
            }
        }
    }
}
