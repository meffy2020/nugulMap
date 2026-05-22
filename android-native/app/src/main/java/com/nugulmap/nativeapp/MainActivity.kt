package com.nugulmap.nativeapp

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nugulmap.nativeapp.ui.map.MapScreen
import com.nugulmap.nativeapp.ui.theme.NugulMapTheme

class MainActivity : ComponentActivity() {
    private var oauthCallbackUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthCallbackUri = intent?.data
        setContent {
            NugulMapTheme {
                MapScreen(oauthCallbackUri = oauthCallbackUri)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        oauthCallbackUri = intent.data
    }
}
