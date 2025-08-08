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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.nikn.portagoose.ui.theme.PortaGooseTheme
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var currentScreen by mutableStateOf("feed") // "feed", "how", etc.
    private var reloadTrigger by mutableStateOf(0)

    // Store WebView and Context for permission callback
    private var webViewForScreenshot: WebView? = null
    private var contextForScreenshot: Context? = null

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("PortaGooseApp", "Storage permission granted after request.")
                webViewForScreenshot?.let { wv ->
                    contextForScreenshot?.let { ctx ->
                        takeScreenshotInternal(wv, ctx)
                    }
                }
            } else {
                Log.d("PortaGooseApp", "Storage permission denied after request.")
                Toast.makeText(this, "Storage permission denied. Cannot save screenshot.", Toast.LENGTH_LONG).show()
            }
            // Clear stored references
            webViewForScreenshot = null
            contextForScreenshot = null
        }

    // Handler to be passed to FeedWebView
    private val handleScreenshotRequest: (WebView, Context) -> Unit = { webView, context ->
        Log.d("PortaGooseApp", "Screenshot request received by MainActivity.")
        takeScreenshotIfNeeded(webView, context)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortaGooseTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (currentScreen == "feed") "PortaGoose" else "How the Holy Goose Works") },
                            navigationIcon = {
                                if (currentScreen != "feed") {
                                    IconButton(onClick = {
                                        currentScreen = "feed"
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
                                    Log.d("PortaGooseApp", "Navigated to how.")
                                }) { Text("How?") }
                                IconButton(onClick = {
                                    val aboutMessage = "This is a simple Android app to show if the Holy Goose has not moved.\n" +
                                            "Also available at https://goose.izkuipers.nl.\n" +
                                            "Made by Nik Nikovsky, version 1.0.2" // Updated version
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
                            else -> "https://goose.izkuipers.nl/feed"
                        }
                        val webViewKey = urlForWebView + if (currentScreen == "feed") "_rt$reloadTrigger" else ""

                        key(webViewKey) {
                            FeedWebView(
                                url = urlForWebView,
                                reloadTrigger = if (currentScreen == "feed") reloadTrigger else 0,
                                onLongPressScreenshot = handleScreenshotRequest // Pass the updated handler
                            )
                        }
                    }
                }
            }
        }
    }

    private fun takeScreenshotIfNeeded(view: WebView, context: Context) {
        Log.d("PortaGooseApp", "takeScreenshotIfNeeded: View width=${view.width}, height=${view.height}")
        if (view.width <= 0 || view.height <= 0) {
            Toast.makeText(context, "View is not ready for screenshot.", Toast.LENGTH_SHORT).show()
            Log.e("PortaGooseApp", "takeScreenshotIfNeeded: View not ready (width or height is 0).")
            return
        }

        // For pre-Q devices, check and request permission if needed.
        // For Q+, MediaStore is used and doesn't need this explicit permission for own writes.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("PortaGooseApp", "Storage permission already granted for pre-Q device.")
                    takeScreenshotInternal(view, context) // Pass the WebView directly
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    Log.d("PortaGooseApp", "Showing rationale for storage permission (pre-Q).")
                    this.webViewForScreenshot = view // Store for callback
                    this.contextForScreenshot = context
                    AlertDialog.Builder(this) // Use Activity context for Dialog
                        .setTitle("Permission Needed")
                        .setMessage("This app needs storage access to save screenshots on older Android versions. Please grant the permission.")
                        .setPositiveButton("OK") { _, _ ->
                            requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                            Toast.makeText(context, "Permission denied. Cannot save screenshot.", Toast.LENGTH_SHORT).show()
                            this.webViewForScreenshot = null // Clear on cancel
                            this.contextForScreenshot = null
                        }
                        .show()
                }
                else -> {
                    Log.d("PortaGooseApp", "Requesting storage permission (pre-Q).")
                    this.webViewForScreenshot = view // Store for callback
                    this.contextForScreenshot = context
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else {
            // On Android Q and above, proceed directly with MediaStore
            Log.d("PortaGooseApp", "Android Q+ detected, proceeding with MediaStore.")
            takeScreenshotInternal(view, context) // Pass the WebView directly
        }
    }

    // Renamed to avoid confusion, this is the core saving logic
    // Now takes the specific WebView to capture
    private fun takeScreenshotInternal(webViewToCapture: WebView, context: Context) {
        Log.d("PortaGooseApp", "takeScreenshotInternal: Capturing bitmap from WebView.")

        // Ensure the WebView has valid dimensions
        if (webViewToCapture.width <= 0 || webViewToCapture.height <= 0) {
            Log.e("PortaGooseApp", "takeScreenshotInternal: WebView has invalid dimensions (${webViewToCapture.width}x${webViewToCapture.height}). Cannot capture.")
            Toast.makeText(context, "Error: WebView not ready for capture.", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = Bitmap.createBitmap(webViewToCapture.width, webViewToCapture.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        webViewToCapture.draw(canvas) // Draw the WebView's content onto the bitmap

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PortaGoose_Screenshot_$timestamp.png"
        Log.d("PortaGooseApp", "takeScreenshotInternal: Attempting to save $fileName")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android Q (API 29) and above
                val resolver = context.contentResolver
                val relativePath = Environment.DIRECTORY_PICTURES + File.separator + "PortaGoose"
                Log.d("PortaGooseApp", "takeScreenshotInternal (API Q+): Saving to relative path: $relativePath")

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                var imageUri: Uri? = null
                try {
                    imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (imageUri == null) {
                        Log.e("PortaGooseApp", "takeScreenshotInternal (API Q+): MediaStore insert failed, imageUri is null.")
                        Toast.makeText(context, "Error saving: MediaStore URI null.", Toast.LENGTH_LONG).show()
                        return
                    }
                    Log.d("PortaGooseApp", "takeScreenshotInternal (API Q+): MediaStore insert successful. URI: $imageUri")

                    resolver.openOutputStream(imageUri).use { outputStream: OutputStream? ->
                        if (outputStream == null) {
                            Log.e("PortaGooseApp", "takeScreenshotInternal (API Q+): resolver.openOutputStream(imageUri) returned null.")
                            Toast.makeText(context, "Error saving: Output stream null.", Toast.LENGTH_LONG).show()
                            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                                try { resolver.update(imageUri, contentValues, null, null) }
                                catch (e: Exception) { Log.e("PortaGooseApp", "Error clearing IS_PENDING on failed stream: $imageUri", e)}
                            }
                            return
                        }
                        Log.d("PortaGooseApp", "takeScreenshotInternal (API Q+): Successfully opened output stream.")
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        outputStream.flush()
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(imageUri, contentValues, null, null)
                            Log.d("PortaGooseApp", "takeScreenshotInternal (API Q): Cleared IS_PENDING for $imageUri")
                        }
                        Toast.makeText(context, "Screenshot saved to Pictures/PortaGoose", Toast.LENGTH_LONG).show()
                        Log.i("PortaGooseApp", "takeScreenshotInternal (API Q+): Screenshot saved as $fileName to $imageUri")
                    }
                } catch (e: Exception) {
                    Log.e("PortaGooseApp", "takeScreenshotInternal (API Q+): Error during MediaStore operation for URI '$imageUri'.", e)
                    Toast.makeText(context, "Error saving screenshot: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && imageUri != null) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        try { resolver.update(imageUri, contentValues, null, null) }
                        catch (updateEx: Exception) { Log.e("PortaGooseApp", "Error clearing IS_PENDING on exception: $imageUri", updateEx)}
                    }
                }
            } else {
                // Use legacy file paths for pre-Q devices (Android versions below 10 / API 29)
                Log.d("PortaGooseApp", "takeScreenshotInternal (pre-API Q): Using legacy storage.")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "PortaGoose")
                if (!appDir.exists() && !appDir.mkdirs()) {
                    Log.e("PortaGooseApp", "takeScreenshotInternal (pre-API Q): Failed to create directory ${appDir.absolutePath}.")
                    Toast.makeText(context, "Error saving: Could not create directory.", Toast.LENGTH_LONG).show()
                    return
                }
                val imageFile = File(appDir, fileName)
                FileOutputStream(imageFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                    Toast.makeText(context, "Screenshot saved to Pictures/PortaGoose", Toast.LENGTH_LONG).show()
                    Log.i("PortaGooseApp", "takeScreenshotInternal (pre-API Q): Screenshot saved as $fileName to ${imageFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.e("PortaGooseApp", "takeScreenshotInternal: General error saving screenshot.", e)
            Toast.makeText(context, "Failed to save screenshot: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            // Optional: Recycle bitmap if no longer needed
            // if (!bitmap.isRecycled) {
            //     bitmap.recycle()
            // }
        }
    }
}

@Composable
fun FeedWebView(
    url: String,
    reloadTrigger: Int,
    onLongPressScreenshot: (webView: WebView, context: Context) -> Unit
) {
    val context = LocalContext.current
    var webViewInstance: WebView? by remember { mutableStateOf(null) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        Log.d("PortaGooseApp", "WebView page loading started: $url")
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("PortaGooseApp", "WebView page loading finished: $url")
                        if(view != null) webViewInstance = view
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true

                setOnLongClickListener {
                    Log.d("PortaGooseApp", "WebView long press detected.")
                    webViewInstance?.let { wv ->
                        onLongPressScreenshot(wv, context)
                    }
                    true
                }
                Log.d("PortaGooseApp", "WebView created. Initial URL: $url, Reload trigger: $reloadTrigger (at creation)")
                loadUrl(url)
            }
        },
        update = { webView ->
            Log.d("PortaGooseApp", "WebView update called. Current URL: ${webView.url}, New URL: $url, Reload trigger: $reloadTrigger")
            if (webView.url != url) {
                webView.loadUrl(url)
            }
            webViewInstance = webView
        },
        modifier = Modifier
            .fillMaxSize()
        // Removed the pointerInput variant as setOnLongClickListener on WebView is more direct.
    )
}
