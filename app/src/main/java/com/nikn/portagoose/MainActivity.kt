package com.nikn.portagoose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.nikn.portagoose.ui.theme.PortaGooseTheme
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button // Unused import, can be removed if not used elsewhere
import android.content.pm.PackageManager // <-- ADDED: Needed for PackageManager.PackageInfoFlags
import android.os.Build // <-- ADDED: Needed for Build.VERSION.SDK_INT


@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortaGooseTheme {
                // Use mutableStateOf to manage which screen is currently shown
                var currentScreen by remember { mutableStateOf("feed") } // "feed" or "how"
                var webViewUrl by remember { mutableStateOf("") } // Store URL for WebView

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when (currentScreen) {
                                        "feed" -> "PortaGoose"
                                        "how" -> "How it Works" // Title for the "How?" screen
                                        else -> "PortaGoose"
                                    }
                                )
                            },
                            navigationIcon = {
                                if (currentScreen == "how") { // Show back button only on "How?" screen
                                    IconButton(onClick = { currentScreen = "feed" }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                // "How?" button
                                IconButton(onClick = {
                                    webViewUrl = "https://goose.izkuipers.nl/how"
                                    currentScreen = "how" // Change screen state to "how"
                                }) {
                                    Text("How?")
                                }

                                // "About" button
                                IconButton(onClick = {
                                    val aboutMessage = "This is a simple Android app to show if the Holy Goose has not moved.\n" +
                                            "Also available at https://goose.izkuipers.nl.\n" +
                                            "Made by Nik Nikovsky, version 1.0.1"

                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("About")
                                        .setMessage(aboutMessage) // Set the custom message
                                        .setPositiveButton("OK") { dialog, _ ->
                                            dialog.dismiss()
                                        }
                                        .show()
                                }) {
                                    Text("About")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors( // Apply colors
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                                actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        )
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Conditional display of WebView based on currentScreen state
                        when (currentScreen) {
                            "feed" -> FeedWebView(url = "https://goose.izkuipers.nl/feed")
                            "how" -> FeedWebView(url = webViewUrl) // Use the dynamic URL
                        }
                    }
                }
            }
        }
    }


    // --- Composable for displaying the WebView (keep this as is with the full settings) ---
    @Composable
    fun FeedWebView(url: String) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true

                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    settings.domStorageEnabled = true
                    loadUrl(url)
                }
            },
            update = { webView ->
                // This 'update' block ensures the WebView reloads if the URL changes
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            }
        )
    }

    // --- Preview Composable ---
    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        PortaGooseTheme {
            // For preview, show the feed
            FeedWebView(url = "https://goose.izkuipers.nl/feed")
        }
    }
}