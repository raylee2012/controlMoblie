package com.controlmoblie

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
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
import com.controlmoblie.overlay.PermissionHelper
import com.controlmoblie.service.VoiceControlService

class MainActivity : ComponentActivity() {

    private var voiceService: VoiceControlService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ControlScreen(
                    onStartService = { startVoiceService() },
                    onOpenAccessibility = { openAccessibilitySettings() }
                )
            }
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unbindService(serviceConnection) } catch (e: Exception) {}
    }

    @Composable
    private fun ControlScreen(
        onStartService: () -> Unit,
        onOpenAccessibility: () -> Unit
    ) {
        var hasOverlay by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(this@MainActivity)) }
        var hasAudio by remember { mutableStateOf(PermissionHelper.hasRecordPermission(this@MainActivity)) }

        val overlayLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            hasOverlay = PermissionHelper.hasOverlayPermission(this@MainActivity)
        }

        val audioLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasAudio = granted
        }

        val asrAvailable = remember { PermissionHelper.isSpeechRecognizerAvailable(this@MainActivity) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Control Mobile", style = MaterialTheme.typography.headlineMedium)

            if (!asrAvailable) {
                Text("此设备不支持语音识别", color = MaterialTheme.colorScheme.error)
            }

            PermissionItem("悬浮窗权限", hasOverlay, "需要在后台显示控制面板") {
                overlayLauncher.launch(PermissionHelper.createOverlaySettingsIntent(this@MainActivity))
            }
            PermissionItem("录音权限", hasAudio, "用于语音识别") {
                audioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }

            Button(
                onClick = { onOpenAccessibility() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("开启无障碍服务")
            }

            if (hasOverlay && hasAudio && asrAvailable) {
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
