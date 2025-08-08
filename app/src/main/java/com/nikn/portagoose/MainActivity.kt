package com.nikn.portagoose

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key // Import the key composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.nikn.portagoose.ui.theme.PortaGooseTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var webViewForScreenshot: WebView? = null
    private var contextForScreenshot: Context? = null
    private var onScreenshotPermissionGrantedCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortaGooseTheme {
                var currentScreen by remember { mutableStateOf("feed") }
                // webViewUrl is now primarily controlled by currentScreen,
                // but kept for potential direct URL manipulations if ever needed.
                var webViewUrl by remember { mutableStateOf("https://goose.izkuipers.nl/feed") }
                var reloadTrigger by remember { mutableStateOf(0) }

                val requestPermissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted: Boolean ->
                        if (isGranted) {
                            Log.d("PortaGooseApp", "WRITE_EXTERNAL_STORAGE permission granted.")
                            onScreenshotPermissionGrantedCallback?.invoke()
                        } else {
                            Log.d("PortaGooseApp", "WRITE_EXTERNAL_STORAGE permission denied.")
                            Toast.makeText(this, "Storage Permission Denied. Cannot save screenshot.", Toast.LENGTH_LONG).show()
                        }
                    }

                val handleScreenshotRequest: (WebView, Context) -> Unit = { webviewInstance, ctx ->
                    Log.d("PortaGooseApp", "handleScreenshotRequest called.")
                    this.webViewForScreenshot = webviewInstance
                    this.contextForScreenshot = ctx
                    this.onScreenshotPermissionGrantedCallback = {
                        this.webViewForScreenshot?.let { wv ->
                            this.contextForScreenshot?.let { c ->
                                if (wv.width > 0 && wv.height > 0) {
                                    Log.d("PortaGooseApp", "Permission granted or not needed, proceeding with screenshot.")
                                    takeScreenshot(wv, c)
                                } else {
                                    Log.w("PortaGooseApp", "Screenshot aborted: View not ready (width/height is 0).")
                                    Toast.makeText(c, "Cannot take screenshot: WebView not fully loaded.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        when {
                            ContextCompat.checkSelfPermission(
                                ctx,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED -> {
                                Log.d("PortaGooseApp", "Storage permission already granted for API < 29.")
                                onScreenshotPermissionGrantedCallback?.invoke()
                            }
                            shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                                Log.d("PortaGooseApp", "Showing rationale for storage permission.")
                                // In a real app, show a proper dialog explaining why you need the permission.
                                AlertDialog.Builder(this)
                                    .setTitle("Permission Needed")
                                    .setMessage("This app needs storage access to save screenshots. Please grant the permission.")
                                    .setPositiveButton("OK") { _, _ ->
                                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                    .setNegativeButton("Cancel") { dialog, _ ->
                                        dialog.dismiss()
                                        Toast.makeText(ctx, "Permission denied. Cannot save screenshot.", Toast.LENGTH_SHORT).show()
                                    }
                                    .show()
                            }
                            else -> {
                                Log.d("PortaGooseApp", "Requesting storage permission for API < 29.")
                                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        }
                    } else {
                        Log.d("PortaGooseApp", "Android 10+ (API 29+), MediaStore will be used. No direct WRITE_EXTERNAL_STORAGE needed for Pictures dir.")
                        onScreenshotPermissionGrantedCallback?.invoke()
                    }
                }

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
                                        currentScreen = "feed"
                                        // webViewUrl = "https://goose.izkuipers.nl/feed" // Update if needed
                                        reloadTrigger = 0 // Resetting trigger
                                        Log.d("PortaGooseApp", "Navigated back to feed.")
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                if (currentScreen == "feed") {
                                    IconButton(onClick = {
                                        reloadTrigger++
                                        Log.d("PortaGooseApp", "Refresh clicked. reloadTrigger: $reloadTrigger")
                                    }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                                    }
                                }
                                IconButton(onClick = {
                                    currentScreen = "how"
                                    // webViewUrl = "https://goose.izkuipers.nl/how" // Update if needed
                                    Log.d("PortaGooseApp", "Navigated to how.")
                                }) { Text("How?") }
                                IconButton(onClick = {
                                    val aboutMessage = "This is a simple Android app to show if the Holy Goose has not moved.\n" +
                                            "Also available at https://goose.izkuipers.nl.\n" +
                                            "Made by Nik Nikovsky, version 1.0.3" // Updated version
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("About")
                                        .setMessage(aboutMessage)
                                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                                        .show()
                                    Log.d("PortaGooseApp", "About dialog shown.")
                                }) { Text("About") }
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
                        val urlForWebView = when (currentScreen) {
                            "feed" -> "https://goose.izkuipers.nl/feed"
                            "how" -> "https://goose.izkuipers.nl/how"
                            else -> "https://goose.izkuipers.nl/feed" // Default
                        }
                        // The key helps ensure that if the fundamental URL changes (feed vs how),
                        // or if reloadTrigger changes for the feed, it's treated as a distinct instance by Compose
                        // for state within AndroidView if necessary (like webViewInstance).
                        val webViewKey = urlForWebView + if (currentScreen == "feed") "_rt$reloadTrigger" else ""


                        key(webViewKey) { // Use key Composable to wrap AndroidView for forced re-creation/update
                            FeedWebView(
                                url = urlForWebView,
                                reloadTrigger = if (currentScreen == "feed") reloadTrigger else 0,
                                onLongPressScreenshot = handleScreenshotRequest
                            )
                        }
                    }
                }
            }
        }
    }

    private fun takeScreenshot(view: WebView, context: Context) {
        Log.d("PortaGooseApp", "takeScreenshot: View width=${view.width}, height=${view.height}")
        if (view.width <= 0 || view.height <= 0) {
            Toast.makeText(context, "View is not ready for screenshot.", Toast.LENGTH_SHORT).show()
            Log.e("PortaGooseApp", "takeScreenshot: View not ready (width or height is 0).")
            return
        }

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PortaGoose_Screenshot_$timestamp.png"
        Log.d("PortaGooseApp", "takeScreenshot: Attempting to save $fileName")

        var fos: FileOutputStream? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "PortaGoose")
                }
                val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri == null) {
                    Log.e("PortaGooseApp", "takeScreenshot: MediaStore failed to insert image for API ${Build.VERSION.SDK_INT}.")
                    Toast.makeText(context, "Error saving screenshot: MediaStore URI was null.", Toast.LENGTH_LONG).show()
                    return
                }
                fos =
                    resolver.openOutputStream(imageUri) as FileOutputStream? // No need to cast if signature is correct
                Log.d("PortaGooseApp", "takeScreenshot: Using MediaStore for API ${Build.VERSION.SDK_INT}. URI: $imageUri")
            } else {
                val imagesDirFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PortaGoose")
                if (!imagesDirFile.exists() && !imagesDirFile.mkdirs()) {
                    Log.e("PortaGooseApp", "takeScreenshot: Failed to create directory ${imagesDirFile.absolutePath} for API ${Build.VERSION.SDK_INT}.")
                    Toast.makeText(context, "Error saving screenshot: Could not create directory.", Toast.LENGTH_LONG).show()
                    return
                }
                val imageFile = File(imagesDirFile, fileName)
                fos = FileOutputStream(imageFile)
                Log.d("PortaGooseApp", "takeScreenshot: Using legacy storage for API ${Build.VERSION.SDK_INT}. Path: ${imageFile.absolutePath}")
            }

            fos?.use { outputStream -> // fos can be null if resolver.openOutputStream(imageUri) returns null
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                Toast.makeText(context, "Screenshot saved to Pictures/PortaGoose", Toast.LENGTH_LONG).show()
                Log.i("PortaGooseApp", "takeScreenshot: Screenshot saved successfully as $fileName.")
            } ?: run {
                Log.e("PortaGooseApp", "takeScreenshot: FileOutputStream was null before compress. Image URI might have been invalid or unopenable.")
                Toast.makeText(context, "Error saving screenshot: Output stream is null.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PortaGooseApp", "takeScreenshot: Error saving screenshot.", e)
            Toast.makeText(context, "Error saving screenshot: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun FeedWebView(
    url: String, // Current base URL to load
    reloadTrigger: Int, // Changed only for the feed screen to trigger reloads
    onLongPressScreenshot: (WebView, Context) -> Unit
) {
    val context = LocalContext.current
    // This webViewInstance is useful for the screenshot functionality.
    // It will be updated in the factory and update blocks.
    var webViewInstance: WebView? by remember { mutableStateOf(null) }

    // Use SideEffect for logging recompositions/updates to this specific composable
    SideEffect {
        Log.d("PortaGooseApp", "FeedWebView recomposed/updated: url='$url', reloadTrigger=$reloadTrigger")
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(url, reloadTrigger) { // Re-trigger pointerInput if URL or reloadTrigger changes
                detectTapGestures(
                    onLongPress = {
                        Log.d("PortaGooseApp", "onLongPress detected for URL: $url")
                        webViewInstance?.let { webview ->
                            // Check width/height again here as it's closer to the user action
                            if (webview.width > 0 && webview.height > 0) {
                                onLongPressScreenshot(webview, context)
                            } else {
                                Log.w("PortaGooseApp", "onLongPress: WebView not ready for screenshot (width/height is 0). URL: $url")
                                Toast.makeText(context, "Cannot take screenshot: WebView not fully rendered.", Toast.LENGTH_SHORT).show()
                            }
                        } ?: run {
                            Log.w("PortaGooseApp", "onLongPress: webViewInstance is null. URL: $url")
                            Toast.makeText(context, "WebView not available for screenshot.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
        factory = { ctx ->
            Log.d("PortaGooseApp", "FeedWebView Factory: Creating WebView for URL: $url")
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, webViewUrl: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, webViewUrl, favicon)
                        Log.d("PortaGooseApp", "WebView onPageStarted: $webViewUrl")
                    }

                    override fun onPageFinished(view: WebView?, webViewUrl: String?) {
                        super.onPageFinished(view, webViewUrl)
                        Log.d("PortaGooseApp", "WebView onPageFinished: $webViewUrl. WebView actual dimensions: ${view?.width}x${view?.height}")
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        Log.e("PortaGooseApp", "WebView Error: $errorCode, $description, URL: $failingUrl")
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false // Hides the +/- zoom buttons

                // Determine initial URL to load
                val initialUrlToLoad = if (url.contains("feed") && reloadTrigger > 0) {
                    val separator = if (url.contains("?")) "&" else "?"
                    "$url${separator}_rel=$reloadTrigger"
                } else {
                    url
                }
                Log.d("PortaGooseApp", "FeedWebView Factory: Initial load URL: $initialUrlToLoad")
                loadUrl(initialUrlToLoad)
                webViewInstance = this // Assign instance here
            }
        },
        update = { webView ->
            webViewInstance = webView // Keep instance updated
            Log.d("PortaGooseApp", "FeedWebView Update: current WebView URL='${webView.url}', target prop URL='$url', reloadTrigger=$reloadTrigger")

            val baseFeedUrl = "https://goose.izkuipers.nl/feed" // Define for clarity
            var urlToLoad = url // Default to the URL prop

            if (url == baseFeedUrl && reloadTrigger > 0) {
                // If it's the feed screen and a reload is triggered, construct the unique URL
                val separator = if (baseFeedUrl.contains("?")) "&" else "?"
                urlToLoad = "$baseFeedUrl${separator}_rel=$reloadTrigger"
                Log.d("PortaGooseApp", "FeedWebView Update: Reload triggered for feed. New urlToLoad: $urlToLoad")
            }

            // Only load if the newly determined urlToLoad is different from what WebView is currently showing
            if (webView.url != urlToLoad) {
                Log.d("PortaGooseApp", "FeedWebView Update: Loading new URL: $urlToLoad (current: ${webView.url})")
                webView.loadUrl(urlToLoad)
            } else {
                Log.d("PortaGooseApp", "FeedWebView Update: No URL change needed. Current: ${webView.url}, Target: $urlToLoad")
            }
        }
    )
}

@Preview(showBackground = true, device = "spec:orientation=landscape,width=1280dp,height=800dp") // Example landscape preview
@Composable
fun DefaultPreview() {
    PortaGooseTheme {
        // Simulate MainActivity's structure for preview
        var currentScreen by remember { mutableStateOf("feed") }
        var reloadTrigger by remember { mutableStateOf(0) }
        val urlForWebView = when (currentScreen) {
            "feed" -> "https://goose.izkuipers.nl/feed"
            "how" -> "https://goose.izkuipers.nl/how"
            else -> "https://goose.izkuipers.nl/feed"
        }
        val webViewKey = urlForWebView + if (currentScreen == "feed") "_rt$reloadTrigger" else ""

        key(webViewKey) {
            FeedWebView(
                url = urlForWebView,
                reloadTrigger = if (currentScreen == "feed") reloadTrigger else 0,
                onLongPressScreenshot = { wv, ctx -> Log.d("Preview", "Screenshot requested in preview") }
            )
        }
        // To make preview interactive, you'd need more complex state hoisting or a mini-scaffold.
        // For now, this just shows the initial state of FeedWebView.
    }
}

