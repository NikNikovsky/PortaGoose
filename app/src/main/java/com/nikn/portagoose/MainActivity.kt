package com.nikn.portagoose

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.nikn.portagoose.ui.theme.PortaGooseTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortaGooseTheme {
                var currentScreen by remember { mutableStateOf("feed") } // "feed" or "how"
                var webViewUrl by remember { mutableStateOf("https://goose.izkuipers.nl/feed") } // Initialize with feed URL
                var reloadTrigger by remember { mutableStateOf(0) } // Used to trigger WebView reload

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when (currentScreen) {
                                        "feed" -> "PortaGoose"
                                        "how" -> "How it Works"
                                        else -> "PortaGoose"
                                    }
                                )
                            },
                            navigationIcon = {
                                if (currentScreen == "how") {
                                    IconButton(onClick = {
                                        // When going back from "How?", reset to the feed URL and screen
                                        webViewUrl = "https://goose.izkuipers.nl/feed"
                                        currentScreen = "feed"
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                // Refresh button (only shown on the feed screen)
                                if (currentScreen == "feed") {
                                    IconButton(onClick = {
                                        // Trigger a reload by changing the reloadTrigger state
                                        reloadTrigger++
                                    }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                                    }
                                }

                                // "How?" button
                                IconButton(onClick = {
                                    webViewUrl = "https://goose.izkuipers.nl/how"
                                    currentScreen = "how"
                                }) {
                                    Text("How?")
                                }

                                // "About" button
                                IconButton(onClick = {
                                    val aboutMessage = "This is a simple Android app to show if the Holy Goose has not moved.\n" +
                                            "Also available at https://goose.izkuipers.nl.\n" +
                                            "Made by Nik Nikovsky, version 1.0.2" // Consider updating version if you release this

                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("About")
                                        .setMessage(aboutMessage)
                                        .setPositiveButton("OK") { dialog, _ ->
                                            dialog.dismiss()
                                        }
                                        .show()
                                }) {
                                    Text("About")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
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
                        when (currentScreen) {
                            "feed" -> FeedWebView(url = webViewUrl, reloadTrigger = reloadTrigger)
                            "how" -> FeedWebView(url = webViewUrl, reloadTrigger = 0) // How screen doesn't need refresh trigger
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun FeedWebView(url: String, reloadTrigger: Int) {
        val context = LocalContext.current
        var webViewInstance: WebView? by remember { mutableStateOf(null) }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            webViewInstance?.let { webview ->
                                if (webview.width > 0 && webview.height > 0) {
                                    takeScreenshot(webview, context)
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            "Cannot take screenshot yet, view not ready.",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                            }
                        }
                    )
                },
            factory = { ctx ->
                WebView(ctx).apply {
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
                    webViewInstance = this
                }
            },
            update = { webView ->
                webViewInstance = webView

                val finalTargetUrl: String

                // The 'url' parameter already tells us if we're supposed to be showing the feed or how page.
                // Let's use constants for better readability and maintainability
                val feedScreenUrl = "https://goose.izkuipers.nl/feed"
                // val howScreenUrl = "https://goose.izkuipers.nl/how" // if needed for other logic

                if (url == feedScreenUrl) { // If the intended URL for this WebView is the feed
                    finalTargetUrl = if (reloadTrigger > 0) {
                        // Append reload trigger to the base feed URL
                        if (feedScreenUrl.contains("?")) "$feedScreenUrl&_rel=$reloadTrigger" else "$feedScreenUrl?_rel=$reloadTrigger"
                    } else {
                        feedScreenUrl // Initial load of feed or navigation back to feed without immediate refresh
                    }
                } else {
                    // For any other URL (e.g., "how" screen), just use the URL passed to this Composable
                    finalTargetUrl = url
                }

                // Load the URL only if it's different from what the WebView is currently showing OR
                // if it's the feed screen and a reload was triggered (even if the base URL appears the same, the _rel makes it different)
                if (webView.url != finalTargetUrl) {
                    webView.loadUrl(finalTargetUrl)
                }
            }
        )
    }

    private fun takeScreenshot(view: WebView, context: Context) {
        if (view.width <= 0 || view.height <= 0) {
            Toast.makeText(context, "View is not ready for screenshot.", Toast.LENGTH_SHORT).show()
            return
        }
        // Create a Bitmap with the same dimensions as the WebView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PortaGoose_Screenshot_$timestamp.png"

        var fos: FileOutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "PortaGoose")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) } as FileOutputStream?
            } else {
                // For older versions, ensure the directory exists
                // Note: WRITE_EXTERNAL_STORAGE permission with maxSdkVersion="28" would be needed in AndroidManifest.xml
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + File.separator + "PortaGoose"
                val imageDirFile = File(imagesDir)
                if (!imageDirFile.exists()) {
                    imageDirFile.mkdirs()
                }
                val imageFile = File(imagesDir, fileName)
                fos = FileOutputStream(imageFile)
                // Optionally, trigger media scanner for older versions if image doesn't appear in gallery:
                // MediaScannerConnection.scanFile(context, arrayOf(imageFile.toString()), null, null)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(context, "Screenshot saved to Pictures/PortaGoose", Toast.LENGTH_LONG).show()
            } ?: run {
                Toast.makeText(context, "Error saving screenshot: Could not get output stream.", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error saving screenshot: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        PortaGooseTheme {
            // For preview, show the feed with no reload trigger initially
            FeedWebView(url = "https://goose.izkuipers.nl/feed", reloadTrigger = 0)
        }
    }
}
