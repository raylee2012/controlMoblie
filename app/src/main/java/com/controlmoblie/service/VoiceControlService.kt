package com.controlmoblie.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.controlmoblie.asr.AsrEvent
import com.controlmoblie.asr.SpeechRecognizerManager
import com.controlmoblie.execution.ExecutionEngine
import com.controlmoblie.llm.InstructionParser
import com.controlmoblie.llm.LlmEngine
import com.controlmoblie.model.Action
import com.controlmoblie.overlay.ControlOverlay
import com.controlmoblie.overlay.OverlayState
import com.controlmoblie.util.ScreenReader
import kotlinx.coroutines.*

class VoiceControlService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var asrManager: SpeechRecognizerManager
    private lateinit var llmEngine: LlmEngine
    private lateinit var parser: InstructionParser
    private lateinit var executionEngine: ExecutionEngine
    private lateinit var overlay: ControlOverlay
    private var accessibilityService: ControlAccessibilityService? = null
    private var isRunning = false

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "voice_control_channel"

    override fun onCreate() {
        super.onCreate()
        asrManager = SpeechRecognizerManager(this)
        llmEngine = LlmEngine(this)
        parser = InstructionParser()
        overlay = ControlOverlay(this)
        executionEngine = ExecutionEngine(accessibilityService)

        overlay.setOnToggleListener { toggleListening() }
        overlay.setOnStopListener { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        if (!isRunning) {
            isRunning = true
            overlay.show()
            overlay.updateState(OverlayState.IDLE)
            startListening()
        }

        return START_STICKY
    }

    fun setAccessibilityService(service: ControlAccessibilityService?) {
        accessibilityService = service
    }

    private fun startListening() {
        overlay.updateState(OverlayState.LISTENING)
        serviceScope.launch {
            asrManager.events.collect { event ->
                when (event) {
                    is AsrEvent.PartialResult -> {
                        overlay.updateState(OverlayState.LISTENING, text = event.text)
                    }
                    is AsrEvent.FinalResult -> {
                        val userText = event.text
                        overlay.updateState(OverlayState.PROCESSING, text = userText)
                        processVoiceCommand(userText)
                    }
                    is AsrEvent.Error -> {
                        overlay.updateState(OverlayState.ERROR, result = event.message)
                        delay(1500)
                        if (isRunning) startListening()
                    }
                    is AsrEvent.Ready -> {}
                }
            }
        }
        asrManager.startListening()
    }

    private suspend fun processVoiceCommand(userText: String) {
        val screenState = accessibilityService?.lastScreenState
        val screenContext = if (screenState != null) {
            ScreenReader.buildScreenContext(screenState)
        } else ""

        val prompt = parser.buildPrompt(userText, screenContext)

        try {
            val llmOutput = withTimeout(5000) { llmEngine.infer(prompt) }
            val result = parser.parse(llmOutput)

            if (result.error != null) {
                overlay.updateState(OverlayState.ERROR, text = userText, result = result.error)
                delay(1500)
                if (isRunning) startListening()
                return
            }

            val action = result.action
            if (action == null) {
                overlay.updateState(OverlayState.ERROR, text = userText, result = "无法解析指令")
                delay(1500)
                if (isRunning) startListening()
                return
            }

            overlay.updateState(OverlayState.EXECUTING, text = userText)
            executionEngine.execute(action) { execResult ->
                serviceScope.launch {
                    if (execResult.success) {
                        overlay.updateState(OverlayState.IDLE, text = userText, result = execResult.message)
                    } else {
                        overlay.updateState(OverlayState.ERROR, text = userText, result = execResult.message)
                    }
                    delay(1000)
                    if (isRunning) startListening()
                }
            }

        } catch (e: TimeoutCancellationException) {
            overlay.updateState(OverlayState.ERROR, text = userText, result = "推理超时")
            delay(1500)
            if (isRunning) startListening()
        } catch (e: Exception) {
            overlay.updateState(OverlayState.ERROR, text = userText, result = "处理失败: ${e.message}")
            delay(1500)
            if (isRunning) startListening()
        }
    }

    private fun toggleListening() {
        if (isRunning) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun stopListening() {
        isRunning = false
        asrManager.stopListening()
        overlay.updateState(OverlayState.IDLE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        asrManager.stopListening()
        overlay.dismiss()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "语音控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "语音控制后台服务"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Control Mobile")
            .setContentText("语音控制运行中")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
