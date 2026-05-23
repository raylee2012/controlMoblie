package com.controlmoblie.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.controlmoblie.asr.AsrEvent
import com.controlmoblie.asr.SpeechRecognizerManager
import com.controlmoblie.asr.SenseVoiceModelManager
import com.controlmoblie.execution.ExecutionEngine
import com.controlmoblie.llm.InstructionParser
import com.controlmoblie.llm.LlmEngine
import com.controlmoblie.overlay.ControlOverlay
import com.controlmoblie.overlay.OverlayState
import com.controlmoblie.tts.TtsModelManager
import com.controlmoblie.tts.TtsSpeaker
import com.controlmoblie.util.ScreenReader
import kotlinx.coroutines.*

class VoiceControlService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var asrManager: SpeechRecognizerManager? = null
    private lateinit var llmEngine: LlmEngine
    private lateinit var parser: InstructionParser
    private lateinit var executionEngine: ExecutionEngine
    private lateinit var overlay: ControlOverlay
    private lateinit var ttsSpeaker: TtsSpeaker
    private var isRunning = false
    private var listenJob: Job? = null

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "voice_control_channel"

    companion object {
        private const val TAG = "VoiceControlService"
    }

    override fun onCreate() {
        super.onCreate()
        llmEngine = LlmEngine(this)
        parser = InstructionParser()
        overlay = ControlOverlay(this)
        executionEngine = ExecutionEngine()
        ttsSpeaker = TtsSpeaker(this)

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
            initAndStart()
        }

        return START_STICKY
    }

    private fun initAndStart() {
        try {
            if (!SenseVoiceModelManager.isModelReady(this)) {
                overlay.updateState(OverlayState.ERROR, result = "语音模型未下载")
                return
            }
            val modelPath = SenseVoiceModelManager.getModelPath(this)
            val manager = SpeechRecognizerManager(modelPath)
            if (!manager.init()) {
                overlay.updateState(OverlayState.ERROR, result = "语音模型加载失败")
                return
            }
            asrManager = manager
            loadLlmModel()
            loadTtsModel()
            startListening()
        } catch (e: Exception) {
            Log.e(TAG, "initAndStart failed", e)
            overlay.updateState(OverlayState.ERROR, result = "初始化失败: ${e.message}")
        }
    }

    private fun loadLlmModel() {
        serviceScope.launch {
            if (llmEngine.isDownloaded && !llmEngine.isModelLoaded) {
                overlay.updateState(OverlayState.PROCESSING, result = "加载推理模型...")
                val success = llmEngine.loadModel()
                if (success) {
                    Log.d(TAG, "LLM model loaded successfully")
                } else {
                    Log.w(TAG, "LLM model load failed, using simulateInference fallback")
                }
            }
        }
    }

    private fun loadTtsModel() {
        serviceScope.launch {
            if (TtsModelManager.isModelReady(this@VoiceControlService) && !ttsSpeaker.isModelReady) {
                overlay.updateState(OverlayState.PROCESSING, result = "加载语音合成...")
                val success = ttsSpeaker.init()
                if (success) {
                    Log.d(TAG, "TTS model loaded successfully")
                } else {
                    Log.w(TAG, "TTS model load failed")
                }
            }
        }
    }

    private suspend fun speakResult(text: String) {
        if (text.isBlank()) return
        if (!ttsSpeaker.isModelReady) {
            delay(1000)
            if (isRunning) startListening()
            return
        }
        asrManager?.stopListening()
        ttsSpeaker.speak(text) {
            serviceScope.launch {
                delay(500)
                if (isRunning) startListening()
            }
        }
    }

    private fun startListening() {
        val manager = asrManager ?: return
        overlay.updateState(OverlayState.LISTENING)
        listenJob?.cancel()
        listenJob = serviceScope.launch {
            manager.events.collect { event ->
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
        manager.startListening()
    }

    private suspend fun processVoiceCommand(userText: String) {
        val screenState = ControlAccessibilityService.instance?.lastScreenState
        val screenContext = if (screenState != null) {
            ScreenReader.buildScreenContext(screenState)
        } else ""

        val prompt = parser.buildPrompt(userText, screenContext)

        try {
            val llmOutput = withTimeout(5000) { llmEngine.infer(prompt, userText) }
            val result = parser.parse(llmOutput)

            if (result.error != null) {
                overlay.updateState(OverlayState.ERROR, text = userText, result = result.error)
                speakResult(result.error)
                return
            }

            val action = result.action
            if (action == null) {
                overlay.updateState(OverlayState.ERROR, text = userText, result = "无法解析指令")
                speakResult("无法解析指令")
                return
            }

            overlay.updateState(OverlayState.EXECUTING, text = userText)
            executionEngine.execute(action) { execResult ->
                serviceScope.launch {
                    val speakText = if (execResult.success) execResult.message else execResult.message
                    overlay.updateState(
                        if (execResult.success) OverlayState.IDLE else OverlayState.ERROR,
                        text = userText,
                        result = execResult.message
                    )
                    if (execResult.success && action is com.controlmoblie.model.Action.Wait) {
                        delay(1000)
                        if (isRunning) startListening()
                    } else {
                        speakResult(speakText)
                    }
                }
            }

        } catch (e: TimeoutCancellationException) {
            overlay.updateState(OverlayState.ERROR, text = userText, result = "推理超时")
            speakResult("推理超时")
        } catch (e: Exception) {
            overlay.updateState(OverlayState.ERROR, text = userText, result = "处理失败: ${e.message}")
            speakResult("处理失败")
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
        listenJob?.cancel()
        listenJob = null
        asrManager?.stopListening()
        overlay.updateState(OverlayState.IDLE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        asrManager?.release()
        asrManager = null
        llmEngine.unload()
        ttsSpeaker.release()
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