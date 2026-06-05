package com.lagradost.quicknovel.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.util.BackupUtils
import com.lagradost.quicknovel.util.LocalSyncServer
import com.lagradost.quicknovel.util.NetworkUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

class WifiSyncActivity : ComponentActivity() {

    private var syncServer: LocalSyncServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickNovelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WifiSyncScreen(
                        onBackPressed = { finish() },
                        onStartServer = { onSyncSuccess, onError ->
                            startServer(onSyncSuccess, onError)
                        },
                        onStopServer = { stopServer() },
                        onSendPayload = { hostUrl, onResult ->
                            sendPayload(hostUrl, onResult)
                        }
                    )
                }
            }
        }
    }

    private fun startServer(onSyncSuccess: () -> Unit, onError: (Exception) -> Unit): String? {
        val ip = NetworkUtils.getLocalIpAddress(this) ?: return null
        stopServer()

        val server = LocalSyncServer(this, onSyncSuccess, onError)
        syncServer = server
        val port = server.start()
        return "http://$ip:$port/sync"
    }

    private fun stopServer() {
        syncServer?.stop()
        syncServer = null
    }

    private fun sendPayload(hostUrl: String, onResult: (Boolean, String) -> Unit) {
        thread(name = "WifiSyncSendPayloadThread") {
            val cacheFile = File(cacheDir, "outgoing_sync.json")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            try {
                // Checkpoint database and serialize settings to a temporary JSON file
                BackupUtils.backupToFile(this, cacheFile)

                // Build OkHttpClient and bypass VPN by binding to Wi-Fi socket factory
                val clientBuilder = OkHttpClient.Builder()
                val wifiNetwork = NetworkUtils.getWifiNetwork(this)
                if (wifiNetwork != null) {
                    clientBuilder.socketFactory(wifiNetwork.socketFactory)
                }
                val client = clientBuilder.build()

                val requestBody = cacheFile.asRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(hostUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        try {
                            cacheFile.delete()
                        } catch (ex: Exception) {}
                        onResult(false, e.message ?: "Failed to connect to host device.")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            cacheFile.delete()
                        } catch (ex: Exception) {}
                        if (response.isSuccessful) {
                            onResult(true, "Data successfully transferred.")
                        } else {
                            onResult(false, "Host returned error code: ${response.code}")
                        }
                    }
                })
            } catch (e: Exception) {
                try {
                    cacheFile.delete()
                } catch (ex: Exception) {}
                onResult(false, e.message ?: "Failed to generate sync package.")
            }
        }
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSyncScreen(
    onBackPressed: () -> Unit,
    onStartServer: (onSyncSuccess: () -> Unit, onError: (Exception) -> Unit) -> String?,
    onStopServer: () -> Unit,
    onSendPayload: (hostUrl: String, onResult: (Boolean, String) -> Unit) -> Unit
) {
    var activeMode by remember { mutableStateOf("selection") } // "selection", "host", "client"
    var hostConnectionUrl by remember { mutableStateOf<String?>(null) }
    var syncStatusMessage by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Trigger success confirmation pop-up
    val triggerSuccess = {
        isSyncing = false
        showSuccessDialog = true
    }

    val triggerError = { e: Exception ->
        isSyncing = false
        syncStatusMessage = e.message ?: "An unexpected sync error occurred."
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(context.getString(R.string.auto_backup_disclaimer_title)) },
            text = { Text(context.getString(R.string.wifi_sync_restart_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        val activity = context as? WifiSyncActivity
                        activity?.finishAffinity()
                    }
                ) {
                    Text("Restart App")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Wi-Fi Sync") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (activeMode) {
                "selection" -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Choose Device Role",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "To sync your reading progress, one device must act as the Host (Receiver) and the other as the Client (Sender).",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Host Option Card
                        Card(
                            onClick = {
                                val url = onStartServer(triggerSuccess, triggerError)
                                if (url != null) {
                                    hostConnectionUrl = url
                                    activeMode = "host"
                                } else {
                                    Toast.makeText(context, "Wi-Fi is not connected.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .glassCard(
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    strokeWidth = 1.dp
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Receive Sync (Host)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Generates QR code to receive progress from another device.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        }

                        // Client Option Card
                        Card(
                            onClick = { activeMode = "client" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .glassCard(
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    strokeWidth = 1.dp
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Send Sync (Client)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Scan QR code and upload current library settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
                "host" -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ready to Receive Sync", fontWeight = FontWeight.Bold, fontSize = 20.sp)

                        hostConnectionUrl?.let { url ->
                            val qrBitmap = remember(url) { generateQrCodeBitmap(url, 512) }
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .background(Color.White, shape = RoundedCornerShape(16.dp))
                                        .padding(16.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "Sync Connection QR"
                                    )
                                }
                            }

                            Text(
                                text = "Or enter this URL on client device:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(url.substringBefore("/sync"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text("Waiting for connection on local network...", fontSize = 14.sp)

                        if (syncStatusMessage.isNotEmpty()) {
                            Text(syncStatusMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        }

                        Button(
                            onClick = {
                                onStopServer()
                                activeMode = "selection"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Stop Server")
                        }
                    }
                }
                "client" -> {
                    var manualUrl by remember { mutableStateOf("") }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Library Sync", fontWeight = FontWeight.Bold, fontSize = 20.sp)

                        Button(
                            onClick = {
                                val scanner = GmsBarcodeScanning.getClient(context)
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        val scannedUrl = barcode.rawValue ?: return@addOnSuccessListener
                                        if (scannedUrl.startsWith("http")) {
                                            isSyncing = true
                                            onSendPayload(scannedUrl) { success, message ->
                                                isSyncing = false
                                                syncStatusMessage = message
                                            }
                                        } else {
                                            Toast.makeText(context, "Invalid QR code", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "Scanning failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            },
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Host QR Code")
                        }

                        Text("— OR —", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))

                        TextField(
                            value = manualUrl,
                            onValueChange = { manualUrl = it },
                            placeholder = { Text("e.g. 192.168.1.5:8080") },
                            label = { Text("Enter Host IP & Port") },
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (manualUrl.isNotBlank()) {
                                    val finalUrl = if (manualUrl.startsWith("http")) manualUrl else "http://$manualUrl/sync"
                                    isSyncing = true
                                    onSendPayload(finalUrl) { success, message ->
                                        isSyncing = false
                                        syncStatusMessage = message
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter an address", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSyncing && manualUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect & Sync")
                        }

                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Text("Compressing & sending library payload...")
                        }

                        if (syncStatusMessage.isNotEmpty()) {
                            Text(syncStatusMessage, textAlign = TextAlign.Center, color = if (syncStatusMessage.contains("success", ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }

                        Button(
                            onClick = { activeMode = "selection" },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws the QR Code Bitmap pixels in memory using ZXing.
 */
fun generateQrCodeBitmap(content: String, size: Int): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.MARGIN, 1)
        }
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
