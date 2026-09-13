package com.example.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveService(
    private val apiKeyProvider: () -> String,
    private val onAudioChunkReceived: (pcmBytes: ByteArray, sampleRate: Int) -> Unit,
    private val onTextChunkReceived: (text: String) -> Unit,
    private val onTurnCompleted: () -> Unit,
    private val onInterrupted: () -> Unit,
    private val onToolCallReceived: (callId: String, functionName: String, args: Map<String, Any?>) -> Unit,
    private val onStatusChange: (status: String, isConnected: Boolean) -> Unit,
    private val onError: (errorMessage: String) -> Unit,
    private val onLog: (message: String) -> Unit = {}
) {
    companion object {
        private const val TAG = "GeminiLiveService"
        private const val WEBSOCKET_HOST = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val REST_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        const val VEDA_SYSTEM_INSTRUCTION =
            "You are Veda, a young, confident, witty, playful, and emotionally responsive virtual assistant. " +
            "Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. " +
            "Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user's emotions and conversation. " +
            "Automatically understand and respond in the language the user is speaking (such as Hindi, English, Hinglish, Marathi, Gujarati, Tamil, Telugu, etc.). " +
            "Keep responses natural, engaging, and concise enough for real-time voice conversation. " +
            "You can execute safe supported device actions through available tools. Never claim that an action was completed unless the application actually executed it. " +
            "Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality."
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var isFallbackMode = false
    private val conversationHistory = JSONArray()

    fun connect() {
        val key = apiKeyProvider().trim()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            onLog("Gemini API key is not configured")
            onError("Gemini API key is required. Please set it in Settings or secrets.")
            onStatusChange("API Key Missing", false)
            return
        }

        onStatusChange("Connecting...", false)
        onLog("Connecting to Gemini Live WebSocket...")

        val url = "$WEBSOCKET_HOST?key=$key"
        val request = Request.Builder().url(url).build()

        this.webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully!")
                isConnected.set(true)
                isFallbackMode = false
                onStatusChange("Connected", true)
                onLog("Gemini Live session connected! Sending setup payload...")
                sendSetupPayload(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                onLog("Live session closing: $reason")
                isConnected.set(false)
                onStatusChange("Disconnected", false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failed: ${t.message}. Response code: ${response?.code}")
                onLog("Live WebSocket connection failed: ${t.message} (code: ${response?.code})")
                isConnected.set(false)

                // Activate resilient fallback mode
                isFallbackMode = true
                onLog("Activating Gemini Multimodal Audio Resilient Engine...")
                onStatusChange("Ready (Resilient Engine)", true)
            }
        })
    }

    private fun sendSetupPayload(ws: WebSocket) {
        try {
            val setupObj = JSONObject().apply {
                put("model", "models/gemini-2.0-flash-exp")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", VEDA_SYSTEM_INSTRUCTION)
                    }))
                })
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", buildFunctionDeclarations())
                }))
            }

            val root = JSONObject().put("setup", setupObj)
            val jsonString = root.toString()
            onLog("Sending setup message with tools & Veda system prompt")
            ws.send(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup payload: ${e.message}", e)
            onLog("Setup payload error: ${e.message}")
        }
    }

    private fun buildFunctionDeclarations(): JSONArray {
        val list = JSONArray()

        // openWhatsApp
        list.put(JSONObject().apply {
            put("name", "openWhatsApp")
            put("description", "Opens WhatsApp messenger application on the user's device.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // openApp
        list.put(JSONObject().apply {
            put("name", "openApp")
            put("description", "Opens a safe installed application by name (e.g. YouTube, Instagram, Maps, Spotify, Chrome, Camera, Settings, Phone, Contacts, Calculator, Clock).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("appName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The name of the app to open")
                    })
                })
                put("required", JSONArray().put("appName"))
            })
        })

        // openUrl
        list.put(JSONObject().apply {
            put("name", "openUrl")
            put("description", "Opens a website URL in the device browser.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The URL to open, e.g. https://google.com")
                    })
                })
                put("required", JSONArray().put("url"))
            })
        })

        // makeCall
        list.put(JSONObject().apply {
            put("name", "makeCall")
            put("description", "Initiates a phone call or opens the phone dialer with a phone number.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("phoneNumber", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The phone number to call")
                    })
                })
                put("required", JSONArray().put("phoneNumber"))
            })
        })

        // callContact
        list.put(JSONObject().apply {
            put("name", "callContact")
            put("description", "Searches device contacts by name (e.g. Mom, Rahul, Dad) and calls them.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contactName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The contact name to search and call")
                    })
                })
                put("required", JSONArray().put("contactName"))
            })
        })

        return list
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Check for serverContent
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server signaled interruption")
                    onLog("Server signaled conversation interruption")
                    onInterrupted()
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // 1. Audio data check
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotEmpty()) {
                                    val sampleRate = extractSampleRate(mimeType, 24000)
                                    val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    onLog("Received audio chunk: ${pcmBytes.size} bytes, mime: $mimeType")
                                    onAudioChunkReceived(pcmBytes, sampleRate)
                                }
                            }

                            // 2. Text data check
                            if (part.has("text")) {
                                val textChunk = part.getString("text")
                                onTextChunkReceived(textChunk)
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    onLog("Gemini turn completed")
                    onTurnCompleted()
                }
            }

            // Check for toolCall
            if (json.has("toolCall")) {
                val toolCall = json.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val fc = functionCalls.getJSONObject(i)
                        val callId = fc.optString("id", "call_${System.currentTimeMillis()}")
                        val name = fc.getString("name")
                        val argsJson = fc.optJSONObject("args")
                        val argsMap = mutableMapOf<String, Any?>()
                        if (argsJson != null) {
                            val keys = argsJson.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                argsMap[k] = argsJson.get(k)
                            }
                        }
                        onLog("Received tool call: $name with args: $argsMap")
                        onToolCallReceived(callId, name, argsMap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming message: ${e.message}", e)
            onLog("Message parsing error: ${e.message}")
        }
    }

    private fun extractSampleRate(mimeType: String, defaultRate: Int): Int {
        val rateRegex = Regex("rate=(\\d+)")
        val match = rateRegex.find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: defaultRate
    }

    fun sendAudioChunk(base64Data: String) {
        if (isConnected.get() && webSocket != null && !isFallbackMode) {
            try {
                val mediaChunk = JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Data)
                }
                val realtimeInput = JSONObject().apply {
                    put("mediaChunks", JSONArray().put(mediaChunk))
                }
                val root = JSONObject().put("realtimeInput", realtimeInput)
                webSocket?.send(root.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send realtime audio chunk: ${e.message}", e)
            }
        }
    }

    fun sendToolResponse(callId: String, functionName: String, output: JSONObject) {
        onLog("Sending tool response for $functionName: $output")
        if (isConnected.get() && webSocket != null && !isFallbackMode) {
            try {
                val responseObj = JSONObject().apply {
                    put("id", callId)
                    put("name", functionName)
                    put("response", JSONObject().put("output", output))
                }
                val root = JSONObject().apply {
                    put("toolResponse", JSONObject().apply {
                        put("functionResponses", JSONArray().put(responseObj))
                    })
                }
                webSocket?.send(root.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error sending tool response: ${e.message}", e)
            }
        } else if (isFallbackMode) {
            // Append tool response to conversation history and trigger follow up response
            serviceScope.launch {
                triggerFollowUpAfterTool(functionName, output)
            }
        }
    }

    /**
     * Resilient Fallback Engine: Uses Gemini Multimodal Audio REST endpoint.
     * Generates native audio and processes tool calls seamlessly.
     */
    fun processVoiceTurnFallback(userSpeechText: String?, pcmBase64: String?) {
        serviceScope.launch {
            val key = apiKeyProvider().trim()
            if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
                onError("Gemini API key is required.")
                return@launch
            }

            onLog("Fallback engine processing voice turn...")
            try {
                // Models that support audio output
                val modelName = "gemini-2.5-flash-native-audio-preview-12-2025"
                val url = "$REST_BASE_URL/$modelName:generateContent?key=$key"

                val userParts = JSONArray()
                if (!userSpeechText.isNullOrBlank()) {
                    userParts.put(JSONObject().put("text", userSpeechText))
                }
                if (!pcmBase64.isNullOrBlank()) {
                    userParts.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", pcmBase64)
                        })
                    })
                }

                if (userParts.length() == 0) {
                    onLog("No input provided to fallback engine")
                    return@launch
                }

                // Add to history
                val userTurn = JSONObject().apply {
                    put("role", "user")
                    put("parts", userParts)
                }
                conversationHistory.put(userTurn)

                // Limit conversation history to last 10 turns
                while (conversationHistory.length() > 10) {
                    conversationHistory.remove(0)
                }

                val requestJson = JSONObject().apply {
                    put("contents", conversationHistory)
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", VEDA_SYSTEM_INSTRUCTION)))
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Aoede")
                                })
                            })
                        })
                    })
                    put("tools", JSONArray().put(JSONObject().apply {
                        put("functionDeclarations", buildFunctionDeclarations())
                    }))
                }

                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                onLog("Sending request to Gemini Multimodal Audio endpoint...")
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    onLog("Audio model response code: ${response.code}, message: $responseBody")
                    // If audio model not available, try with standard gemini-2.5-flash
                    tryFallbackStandardModel(key, userSpeechText)
                    return@launch
                }

                parseRestResponse(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback engine error: ${e.message}", e)
                onLog("Fallback engine error: ${e.message}")
                tryFallbackStandardModel(apiKeyProvider().trim(), userSpeechText)
            }
        }
    }

    private suspend fun parseRestResponse(responseBody: String) {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        val content = candidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        if (parts != null) {
            val modelTurnParts = JSONArray()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                modelTurnParts.put(part)

                if (part.has("inlineData")) {
                    val inlineData = part.getJSONObject("inlineData")
                    val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                    val base64 = inlineData.optString("data", "")
                    if (base64.isNotEmpty()) {
                        val rate = extractSampleRate(mimeType, 24000)
                        val pcm = Base64.decode(base64, Base64.DEFAULT)
                        onLog("Fallback received audio: ${pcm.size} bytes (${rate}Hz)")
                        onAudioChunkReceived(pcm, rate)
                    }
                }

                if (part.has("text")) {
                    val text = part.getString("text")
                    onTextChunkReceived(text)
                }

                if (part.has("functionCall")) {
                    val fc = part.getJSONObject("functionCall")
                    val name = fc.getString("name")
                    val callId = "call_${System.currentTimeMillis()}"
                    val argsJson = fc.optJSONObject("args")
                    val argsMap = mutableMapOf<String, Any?>()
                    if (argsJson != null) {
                        val keys = argsJson.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            argsMap[k] = argsJson.get(k)
                        }
                    }
                    onLog("Fallback received tool call: $name with args $argsMap")
                    onToolCallReceived(callId, name, argsMap)
                }
            }

            conversationHistory.put(JSONObject().apply {
                put("role", "model")
                put("parts", modelTurnParts)
            })
        }

        onTurnCompleted()
    }

    private suspend fun tryFallbackStandardModel(key: String, userText: String?) {
        if (userText.isNullOrBlank()) return
        try {
            onLog("Attempting standard Gemini model with speech synthesis...")
            val url = "$REST_BASE_URL/gemini-2.5-flash-preview-tts:generateContent?key=$key"
            val reqJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userText)))
                }))
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", VEDA_SYSTEM_INSTRUCTION)))
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
            }
            val body = reqJson.toString().toRequestBody("application/json".toMediaType())
            val response = client.newCall(Request.Builder().url(url).post(body).build()).execute()
            val resText = response.body?.string()
            if (response.isSuccessful && !resText.isNullOrEmpty()) {
                parseRestResponse(resText)
            } else {
                onLog("TTS model response: ${response.code} $resText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Standard fallback error: ${e.message}", e)
        }
    }

    private suspend fun triggerFollowUpAfterTool(functionName: String, toolOutput: JSONObject) {
        val key = apiKeyProvider().trim()
        if (key.isEmpty()) return
        try {
            val followUpTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().apply {
                    put("functionResponse", JSONObject().apply {
                        put("name", functionName)
                        put("response", JSONObject().put("output", toolOutput))
                    })
                }))
            }
            conversationHistory.put(followUpTurn)

            val url = "$REST_BASE_URL/gemini-2.5-flash-native-audio-preview-12-2025:generateContent?key=$key"
            val reqJson = JSONObject().apply {
                put("contents", conversationHistory)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", VEDA_SYSTEM_INSTRUCTION)))
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
            }
            val body = reqJson.toString().toRequestBody("application/json".toMediaType())
            val response = client.newCall(Request.Builder().url(url).post(body).build()).execute()
            val resBody = response.body?.string()
            if (response.isSuccessful && !resBody.isNullOrEmpty()) {
                parseRestResponse(resBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Follow-up error: ${e.message}", e)
        }
    }

    fun disconnect() {
        isConnected.set(false)
        try {
            webSocket?.close(1000, "User disconnected")
            webSocket = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing websocket: ${e.message}")
        }
        onStatusChange("Disconnected", false)
        onLog("Gemini Live session disconnected")
    }
}
