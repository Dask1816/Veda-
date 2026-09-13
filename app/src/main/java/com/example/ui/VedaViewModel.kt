package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.action.DeviceActionManager
import com.example.audio.AudioPlaybackQueue
import com.example.audio.AudioRecordManager
import com.example.audio.SpeechRecognizerManager
import com.example.data.GeminiLiveService
import com.example.data.models.AssistantState
import com.example.data.models.ContactInfo
import com.example.data.models.DeviceActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VedaViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VedaViewModel"
        private const val PREFS_NAME = "veda_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }

    private val prefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _vedaTranscript = MutableStateFlow("")
    val vedaTranscript: StateFlow<String> = _vedaTranscript.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("Auto (Multi)")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()

    private val _actionFeedback = MutableStateFlow<DeviceActionResult?>(null)
    val actionFeedback: StateFlow<DeviceActionResult?> = _actionFeedback.asStateFlow()

    private val _contactDisambiguation = MutableStateFlow<List<ContactInfo>?>(null)
    val contactDisambiguation: StateFlow<List<ContactInfo>?> = _contactDisambiguation.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<String>>(emptyList())
    val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

    private val _isSpeakerTestRunning = MutableStateFlow(false)
    val isSpeakerTestRunning: StateFlow<Boolean> = _isSpeakerTestRunning.asStateFlow()

    private val _speakerTestResult = MutableStateFlow<String?>(null)
    val speakerTestResult: StateFlow<String?> = _speakerTestResult.asStateFlow()

    private val _apiKey = MutableStateFlow(loadInitialApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val deviceActionManager: DeviceActionManager = DeviceActionManager(application.applicationContext)

    private val audioPlaybackQueue: AudioPlaybackQueue = AudioPlaybackQueue(
        sampleRate = 24000,
        onPlaybackStateChange = { isPlaying ->
            viewModelScope.launch(Dispatchers.Main) {
                if (isPlaying) {
                    _assistantState.value = AssistantState.SPEAKING
                    audioRecordManager.isVedaSpeaking = true
                } else {
                    if (_assistantState.value == AssistantState.SPEAKING) {
                        _assistantState.value = AssistantState.LISTENING
                        audioRecordManager.isVedaSpeaking = false
                    }
                }
            }
        },
        onAudioAmplitude = { amp ->
            if (_assistantState.value == AssistantState.SPEAKING) {
                _audioLevel.value = amp
            }
        },
        onLog = { addLog("[AudioOut] $it") }
    )

    private val audioRecordManager: AudioRecordManager = AudioRecordManager(
        context = application.applicationContext,
        sampleRate = 16000,
        onAudioChunk = { base64Chunk, _ ->
            geminiLiveService.sendAudioChunk(base64Chunk)
        },
        onAmplitude = { amp ->
            if (_assistantState.value == AssistantState.LISTENING) {
                _audioLevel.value = amp
            }
        },
        onUserInterruption = {
            handleUserInterruption()
        },
        onLog = { addLog("[AudioIn] $it") }
    )

    private val speechRecognizerManager: SpeechRecognizerManager = SpeechRecognizerManager(
        context = application.applicationContext,
        onPartialResult = { partial ->
            viewModelScope.launch(Dispatchers.Main) {
                _userTranscript.value = partial
                detectLanguageFromText(partial)
                if (_assistantState.value == AssistantState.SPEAKING) {
                    handleUserInterruption()
                }
            }
        },
        onFinalResult = { final ->
            viewModelScope.launch(Dispatchers.Main) {
                _userTranscript.value = final
                detectLanguageFromText(final)
                addLog("[User Speech] \"$final\"")

                // If in fallback mode or speech received, send voice turn to fallback engine
                geminiLiveService.processVoiceTurnFallback(final, null)
            }
        },
        onRmsChanged = { rms ->
            if (_assistantState.value == AssistantState.LISTENING && rms > 0) {
                _audioLevel.value = (rms / 10f).coerceIn(0f, 1f)
            }
        },
        onError = { err ->
            addLog("[STT] $err")
        }
    )

    private val geminiLiveService: GeminiLiveService = GeminiLiveService(
        apiKeyProvider = { _apiKey.value },
        onAudioChunkReceived = { pcmBytes, sampleRate ->
            viewModelScope.launch(Dispatchers.Main) {
                _assistantState.value = AssistantState.SPEAKING
                audioRecordManager.isVedaSpeaking = true
            }
            audioPlaybackQueue.enqueueAudioChunk(pcmBytes, sampleRate)
        },
        onTextChunkReceived = { text ->
            viewModelScope.launch(Dispatchers.Main) {
                _vedaTranscript.value = (_vedaTranscript.value + text).takeLast(400)
                detectLanguageFromText(text)
            }
        },
        onTurnCompleted = {
            audioPlaybackQueue.notifyTurnComplete()
        },
        onInterrupted = {
            handleUserInterruption()
        },
        onToolCallReceived = { callId, name, args ->
            executeToolCall(callId, name, args)
        },
        onStatusChange = { status, connected ->
            addLog("[Gemini] $status")
            if (!connected && _assistantState.value != AssistantState.IDLE) {
                // Kept active if resilient engine is running
            }
        },
        onError = { err ->
            viewModelScope.launch(Dispatchers.Main) {
                _errorMessage.value = err
                _assistantState.value = AssistantState.ERROR
                addLog("[Error] $err")
            }
        },
        onLog = { addLog(it) }
    )

    init {
        audioPlaybackQueue.start()
        addLog("Veda initialized and ready")
    }

    private fun loadInitialApiKey(): String {
        val saved = prefs.getString(KEY_GEMINI_API_KEY, null)
        if (!saved.isNullOrBlank()) return saved

        return try {
            val configKey = BuildConfig.GEMINI_API_KEY
            if (configKey.isNotBlank() && configKey != "MY_GEMINI_API_KEY") {
                configKey
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun saveApiKey(newKey: String) {
        val clean = newKey.trim()
        _apiKey.value = clean
        prefs.edit().putString(KEY_GEMINI_API_KEY, clean).apply()
        addLog("API key saved (length: ${clean.length})")
        _errorMessage.value = null
    }

    fun togglePower() {
        when (_assistantState.value) {
            AssistantState.IDLE, AssistantState.ERROR -> startAssistant()
            else -> stopAssistant()
        }
    }

    fun startAssistant() {
        val key = _apiKey.value.trim()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            _errorMessage.value = "Gemini API key is required. Tap the key icon to configure it."
            _assistantState.value = AssistantState.ERROR
            addLog("Cannot start: API key missing")
            return
        }

        _errorMessage.value = null
        _assistantState.value = AssistantState.CONNECTING
        addLog("Starting Veda session...")

        geminiLiveService.connect()

        val recordStarted = audioRecordManager.startRecording()
        if (recordStarted) {
            _assistantState.value = AssistantState.LISTENING
            speechRecognizerManager.startListening()
            addLog("Listening to your voice...")
        } else {
            _errorMessage.value = "Microphone permission required for voice interaction."
            _assistantState.value = AssistantState.ERROR
        }
    }

    fun stopAssistant() {
        addLog("Stopping Veda session...")
        audioPlaybackQueue.interrupt()
        audioRecordManager.stopRecording()
        speechRecognizerManager.stopListening()
        geminiLiveService.disconnect()
        _assistantState.value = AssistantState.IDLE
        _audioLevel.value = 0f
    }

    fun handleUserInterruption() {
        viewModelScope.launch(Dispatchers.Main) {
            Log.d(TAG, "User interrupted Veda! Stopping playback immediately.")
            audioPlaybackQueue.interrupt()
            audioRecordManager.isVedaSpeaking = false
            _assistantState.value = AssistantState.LISTENING
            _vedaTranscript.value = ""
            addLog("[Interruption] Veda speech cancelled; listening to user")
        }
    }

    fun sendPrompt(promptText: String) {
        if (_assistantState.value == AssistantState.IDLE) {
            startAssistant()
        }
        _userTranscript.value = promptText
        detectLanguageFromText(promptText)
        addLog("[Quick Prompt] \"$promptText\"")
        geminiLiveService.processVoiceTurnFallback(promptText, null)
    }

    fun runSpeakerDiagnostic() {
        viewModelScope.launch {
            _isSpeakerTestRunning.value = true
            _speakerTestResult.value = "Testing 440Hz audio tone..."
            addLog("[Diagnostic] Running speaker test tone...")

            val success = audioPlaybackQueue.playDiagnosticTone(440f, 1200)
            _isSpeakerTestRunning.value = false
            if (success) {
                _speakerTestResult.value = "Speaker OK! 440Hz tone played successfully."
                addLog("[Diagnostic] Speaker test PASSED!")
            } else {
                _speakerTestResult.value = "Speaker test failed! Check volume & permissions."
                addLog("[Diagnostic] Speaker test FAILED!")
            }
        }
    }

    private fun executeToolCall(callId: String, name: String, args: Map<String, Any?>) {
        viewModelScope.launch(Dispatchers.Main) {
            addLog("Executing device action: $name with args $args")
            val result: DeviceActionResult = when (name) {
                "openWhatsApp" -> deviceActionManager.openWhatsApp()
                "openApp" -> {
                    val app = args["appName"]?.toString() ?: "App"
                    deviceActionManager.openApp(app)
                }
                "openUrl" -> {
                    val url = args["url"]?.toString() ?: ""
                    deviceActionManager.openUrl(url)
                }
                "makeCall" -> {
                    val phone = args["phoneNumber"]?.toString() ?: ""
                    deviceActionManager.makeCall(phone)
                }
                "callContact" -> {
                    val contact = args["contactName"]?.toString() ?: ""
                    val callRes = deviceActionManager.callContact(contact)
                    if (callRes.error == "Multiple matching contacts found" && callRes.data is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        _contactDisambiguation.value = callRes.data as List<ContactInfo>
                    }
                    callRes
                }
                else -> DeviceActionResult(false, name, "Unsupported action: $name", error = "Unknown action")
            }

            _actionFeedback.value = result
            addLog("[Action Result] ${result.message}")

            val toolOutput = JSONObject().apply {
                put("success", result.success)
                put("message", result.message)
                if (result.error != null) put("error", result.error)
            }
            geminiLiveService.sendToolResponse(callId, name, toolOutput)
        }
    }

    fun selectContactForCall(contact: ContactInfo) {
        _contactDisambiguation.value = null
        val callRes = deviceActionManager.makeCall(contact.phoneNumber)
        _actionFeedback.value = DeviceActionResult(
            callRes.success,
            "callContact",
            "Calling ${contact.name} (${contact.phoneNumber}).",
            data = contact
        )
        addLog("Calling selected contact: ${contact.name} (${contact.phoneNumber})")
    }

    fun dismissContactDisambiguation() {
        _contactDisambiguation.value = null
    }

    fun dismissActionFeedback() {
        _actionFeedback.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun detectLanguageFromText(text: String) {
        if (text.isBlank()) return
        val detected = when {
            // Devanagari (Hindi, Marathi)
            text.any { it in '\u0900'..'\u097F' } -> {
                if (text.contains("आहे") || text.contains("करा") || text.contains("नाही")) "Marathi"
                else "Hindi"
            }
            // Tamil
            text.any { it in '\u0B80'..'\u0BFF' } -> "Tamil"
            // Telugu
            text.any { it in '\u0C00'..'\u0C7F' } -> "Telugu"
            // Bengali
            text.any { it in '\u0980'..'\u09FF' } -> "Bengali"
            // Gujarati
            text.any { it in '\u0A80'..'\u0AFF' } -> "Gujarati"
            // Kannada
            text.any { it in '\u0C80'..'\u0CFF' } -> "Kannada"
            // Malayalam
            text.any { it in '\u0D00'..'\u0D7F' } -> "Malayalam"
            // Punjabi (Gurmukhi)
            text.any { it in '\u0A00'..'\u0A7F' } -> "Punjabi"
            // Hinglish keywords
            text.lowercase().contains("khalo") || text.lowercase().contains("kholo") ||
            text.lowercase().contains("karo") || text.lowercase().contains("batao") ||
            text.lowercase().contains("kaise") || text.lowercase().contains("hai") ||
            text.lowercase().contains("kya") || text.lowercase().contains("aap") -> "Hinglish"
            else -> "English"
        }
        _detectedLanguage.value = detected
    }

    private fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$time] $message"
        val current = _diagnosticLogs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        _diagnosticLogs.value = current
    }

    override fun onCleared() {
        super.onCleared()
        stopAssistant()
        audioPlaybackQueue.release()
        speechRecognizerManager.destroy()
    }
}
