package com.controlmoblie

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.controlmoblie.asr.VoskModelManager
import com.controlmoblie.overlay.PermissionHelper
import com.controlmoblie.service.VoiceControlService
import com.controlmoblie.tts.TtsModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ControlScreen(
                    onStartService = { startVoiceService() }
                )
            }
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceControlService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    @Composable
    private fun ControlScreen(
        onStartService: () -> Unit
    ) {
        var hasOverlay by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(this@MainActivity)) }
        var hasAudio by remember { mutableStateOf(PermissionHelper.hasRecordPermission(this@MainActivity)) }
        var hasAccessibility by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(this@MainActivity)) }
        var voskModelReady by remember { mutableStateOf(VoskModelManager.isModelReady(this@MainActivity)) }
        var downloadProgress by remember { mutableStateOf(-1f) }
        var isDownloading by remember { mutableStateOf(false) }
        var ttsModelReady by remember { mutableStateOf(TtsModelManager.isModelReady(this@MainActivity)) }
        var ttsDownloadProgress by remember { mutableStateOf(-1f) }
        var ttsDownloading by remember { mutableStateOf(false) }

        val overlayLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            hasOverlay = PermissionHelper.hasOverlayPermission(this@MainActivity)
            hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(this@MainActivity)
        }

        val audioLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasAudio = granted
        }

        val accessibilityLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(this@MainActivity)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Control Mobile", style = MaterialTheme.typography.headlineMedium)

            PermissionItem("悬浮窗权限", hasOverlay, "需要在后台显示控制面板") {
                overlayLauncher.launch(PermissionHelper.createOverlaySettingsIntent(this@MainActivity))
            }
            PermissionItem("录音权限", hasAudio, "用于语音识别") {
                audioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
            PermissionItem("无障碍服务", hasAccessibility, "需要执行点击、滑动等操作") {
                accessibilityLauncher.launch(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { if (downloadProgress >= 0f) downloadProgress else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (downloadProgress >= 0f) "下载语音模型中... ${(downloadProgress * 100).toInt()}%"
                    else "准备下载...",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!voskModelReady && !isDownloading) {
                OutlinedButton(
                    onClick = {
                        if (!hasAudio) return@OutlinedButton
                        isDownloading = true
                        downloadProgress = 0f
                        this@MainActivity.lifecycleScope.launch(Dispatchers.Main) {
                            val success = VoskModelManager.downloadAndExtract(this@MainActivity) { progress ->
                                downloadProgress = progress
                            }
                            voskModelReady = success
                            isDownloading = false
                            downloadProgress = -1f
                            if (!success) {
                                downloadProgress = -1f
                            }
                        }
                    },
                    enabled = hasAudio && !isDownloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("下载语音识别模型 (~42MB)")
                }
            }

            if (voskModelReady && !isDownloading) {
                Text("语音模型 ✓", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }

            if (ttsDownloading) {
                LinearProgressIndicator(
                    progress = { if (ttsDownloadProgress >= 0f) ttsDownloadProgress else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (ttsDownloadProgress >= 0f) "下载语音合成模型中... ${(ttsDownloadProgress * 100).toInt()}%"
                    else "准备下载...",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!ttsModelReady && !ttsDownloading) {
                OutlinedButton(
                    onClick = {
                        ttsDownloading = true
                        ttsDownloadProgress = 0f
                        this@MainActivity.lifecycleScope.launch(Dispatchers.Main) {
                            val success = TtsModelManager.downloadAndExtract(this@MainActivity) { progress ->
                                ttsDownloadProgress = progress
                            }
                            ttsModelReady = success
                            ttsDownloading = false
                            ttsDownloadProgress = -1f
                        }
                    },
                    enabled = !ttsDownloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("下载语音合成模型 (~120MB)")
                }
            }

            if (ttsModelReady && !ttsDownloading) {
                Text("语音合成 ✓", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }

            if (hasOverlay && hasAudio && voskModelReady) {
                Button(
                    onClick = onStartService,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("启动语音控制")
                }
            }
        }
    }

    @Composable
    private fun PermissionItem(
        title: String,
        granted: Boolean,
        description: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Text("✓", color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onClick) { Text("授权") }
            }
        }
    }
}