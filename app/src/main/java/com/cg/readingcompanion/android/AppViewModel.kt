package com.cg.readingcompanion.android

import android.app.Application
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*

data class AppUiState(
    val context: ReadingContext = ReadingContext(),
    val turns: List<ChatTurn> = emptyList(),
    val partial: String = "",
    val status: String = "点这里开始说话",
    val address: String = "",
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val serverRunning: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private val asr = TencentAsrClient()
    private val llm = LlmClient()
    private val edgeTts = EdgeTtsClient()
    private val recorder = AudioRecorder()
    private var ttsJob: kotlinx.coroutines.Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var ttsReady = false
    private var responseId = UUID.randomUUID().toString()

    private lateinit var tts: TextToSpeech

    private val _state = MutableStateFlow(AppUiState(address = addressForDisplay()))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ServerHub.context.collect { ctx -> update { it.copy(context = ctx) } }
        }
        viewModelScope.launch {
            ServerHub.running.collect { running -> update { it.copy(serverRunning = running) } }
        }
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts.language = Locale.SIMPLIFIED_CHINESE
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = update { it.copy(isSpeaking = true) }
                    override fun onDone(utteranceId: String?) = update { it.copy(isSpeaking = false) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = update { it.copy(isSpeaking = false) }
                })
            }
        }
    }

    private fun update(transform: (AppUiState) -> AppUiState) {
        _state.value = transform(_state.value)
    }

    fun saveSettings(
        apiKey: String,
        model: String,
        secretId: String,
        secretKey: String,
        region: String,
        token: String,
        port: Int,
    ) {
        store.apiKey = apiKey
        store.model = model
        store.tencentSecretId = secretId
        store.tencentSecretKey = secretKey
        store.tencentRegion = region.ifBlank { "ap-guangzhou" }
        store.token = token
        store.port = port
        LocalServerService.start(getApplication())
        update { it.copy(address = addressForDisplay(), serverRunning = true) }
    }

    fun config(): LlmConfig = store.llmConfig()
    fun token(): String = store.token
    fun port(): Int = store.port
    fun tencentSecretId(): String = store.tencentSecretId
    fun tencentSecretKey(): String = store.tencentSecretKey
    fun tencentRegion(): String = store.tencentRegion
    fun apiKey(): String = store.apiKey
    fun model(): String = store.model

    fun toggleRecording() {
        if (_state.value.isThinking || _state.value.isSpeaking || _state.value.isTranscribing) return
        if (_state.value.isRecording) endRecording() else beginRecording()
    }

    private fun beginRecording() {
        if (!recorder.start()) {
            update { it.copy(status = "麦克风启动失败") }
            return
        }
        update { it.copy(isRecording = true, status = "录音中，点一下发送", partial = "") }
    }

    private fun endRecording() {
        if (!_state.value.isRecording) return
        update { it.copy(isRecording = false, isTranscribing = true, status = "识别中…") }
        viewModelScope.launch {
            val pcm = withContext(Dispatchers.IO) { recorder.stop() }
            if (pcm.size < 16_000) {
                update { it.copy(isTranscribing = false, status = "说话时间太短") }
                return@launch
            }
            val result = asr.transcribe(
                WavWriter.pcm16ToWav(pcm),
                tencentSecretId(), tencentSecretKey(), tencentRegion()
            )
            result.onSuccess { text ->
                update { it.copy(isTranscribing = false, status = "点这里开始说话") }
                ask(text)
            }.onFailure { error ->
                update {
                    it.copy(
                        isTranscribing = false,
                        status = if (error is EmptySpeechException) "点这里开始说话"
                        else "识别失败：${error.message}"
                    )
                }
            }
        }
    }

    fun ask(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        val userTurn = ChatTurn("user", cleaned)
        val assistant = ChatTurn("assistant", "")
        val turns = _state.value.turns + userTurn + assistant
        val requestId = UUID.randomUUID().toString()
        responseId = requestId
        update {
            it.copy(turns = turns, isThinking = true, partial = "", status = "在想…")
        }
        llm.stream(
            turns = turns.dropLast(1),
            context = _state.value.context,
            config = config(),
            onDelta = { delta ->
                if (responseId != requestId) return@stream
                update { current ->
                    val list = current.turns.toMutableList()
                    val index = list.lastIndex
                    if (index >= 0) list[index] = list[index].copy(text = list[index].text + delta)
                    current.copy(turns = list)
                }
            },
            onDone = { result ->
                if (responseId != requestId) return@stream
                result.onSuccess {
                    val answer = _state.value.turns.lastOrNull()?.text.orEmpty()
                    update { it.copy(isThinking = false, status = if (answer.isBlank()) "点这里开始说话" else "说话中") }
                    if (answer.isNotBlank()) speak(answer)
                }.onFailure { error ->
                    llm.cancel()
                    update {
                        val list = it.turns.toMutableList()
                        if (list.isNotEmpty()) list[list.lastIndex] = ChatTurn("assistant", "请求失败：${error.message}")
                        it.copy(turns = list, isThinking = false, status = "请求失败")
                    }
                }
            }
        )
    }

    private fun speak(text: String) {
        ttsJob?.cancel()
        releasePlayer()
        ttsJob = viewModelScope.launch {
            val file = java.io.File(getApplication<Application>().cacheDir,
                "edge-tts-${UUID.randomUUID()}.mp3")
            val result = edgeTts.synthesize(text, "zh-CN-XiaoxiaoNeural", file)
            if (result.isSuccess) {
                withContext(Dispatchers.Main) {
                    runCatching {
                        val player = MediaPlayer()
                        player.setDataSource(file.absolutePath)
                        player.setOnCompletionListener {
                            releasePlayer()
                            update { it.copy(isSpeaking = false, status = "点这里开始说话") }
                        }
                        player.setOnErrorListener { _, _, _ ->
                            releasePlayer()
                            systemSpeak(text)
                            true
                        }
                        player.prepare()
                        player.start()
                        mediaPlayer = player
                        update { it.copy(isSpeaking = true, status = "说话中") }
                    }.onFailure {
                        systemSpeak(text)
                    }
                }
            } else {
                withContext(Dispatchers.Main) { systemSpeak(text) }
            }
        }
    }

    private fun systemSpeak(text: String) {
        if (!ttsReady) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
        update { it.copy(status = "说话中") }
    }

    private fun releasePlayer() {
        mediaPlayer?.let {
            runCatching { it.stop() }
            it.release()
        }
        mediaPlayer = null
    }

    fun previewTts() {
        speak("你好，我是你的读书陪读。")
    }

    fun stopResponding() {
        responseId = UUID.randomUUID().toString()
        llm.cancel()
        ttsJob?.cancel()
        ttsJob = null
        releasePlayer()
        if (ttsReady) tts.stop()
        update {
            it.copy(
                isThinking = false,
                isSpeaking = false,
                status = "点这里开始说话",
                turns = it.turns.filterNot { turn -> turn.role == "assistant" && turn.text.isBlank() }
            )
        }
    }

    override fun onCleared() {
        llm.cancel()
        ttsJob?.cancel()
        releasePlayer()
        recorder.stop()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onCleared()
    }

    private fun addressForDisplay(): String {
        val ip = localIpv4() ?: "127.0.0.1"
        return "http://$ip:${store.port}/context"
    }

    private fun localIpv4(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
