package com.example.live

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.decodeBase64
import java.util.concurrent.TimeUnit

enum class ZoyaState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

class LiveSessionManager(
    private val context: Context,
    private val toolEngine: ToolExecutionEngine,
    private val onAudioOut: (ByteArray) -> Unit,
    private val onInterrupt: () -> Unit = {}
) {
    private val _zoyaState = MutableStateFlow(ZoyaState.IDLE)
    val zoyaState: StateFlow<ZoyaState> = _zoyaState.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val json = Json { ignoreUnknownKeys = true }
    
    // Tools definition
    private val toolsJson = buildJsonObject {
        putJsonArray("functionDeclarations") {
            add(buildJsonObject {
                put("name", "openApp")
                put("description", "Open an application package, like WhatsApp or YouTube")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("packageName") {
                            put("type", "STRING")
                            put("description", "A generic name of the app to launch (e.g. 'WhatsApp', 'YouTube', 'Settings', 'Calculator')")
                        }
                    }
                    putJsonArray("required") { add("packageName") }
                }
            })
            add(buildJsonObject {
                put("name", "searchAndCallContact")
                put("description", "Search for a contact name on the device and call them. Can optionally open dialer instead of calling immediately, or use a specific SIM card slot.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("contactName") {
                            put("type", "STRING")
                            put("description", "The EXACT name of the contact as spoken by the user. NEVER guess or invent numbers. If the user says a name, use exactly that name.")
                        }
                        putJsonObject("useDialer") {
                            put("type", "BOOLEAN")
                            put("description", "Set to true if user wants to open dial pad / keyboard so they can see the number before calling")
                        }
                        putJsonObject("simSlot") {
                            put("type", "INTEGER")
                            put("description", "1 for SIM 1, 2 for SIM 2 if user specified. Null if default.")
                        }
                    }
                    putJsonArray("required") { add("contactName") }
                }
            })
            add(buildJsonObject {
                put("name", "sendWhatsAppMessage")
                put("description", "Send a WhatsApp message to a specific contact with some text.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("contactName") {
                            put("type", "STRING")
                            put("description", "The EXACT name of the contact as spoken by the user. NEVER guess or invent numbers. If the user says a name, use exactly that name.")
                        }
                        putJsonObject("message") {
                            put("type", "STRING")
                        }
                    }
                    putJsonArray("required") { add("contactName"); add("message") }
                }
            })
            add(buildJsonObject {
                put("name", "sendGmail")
                put("description", "Draft or send an email.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("recipientEmail") { put("type", "STRING") }
                        putJsonObject("subject") { put("type", "STRING") }
                        putJsonObject("body") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("recipientEmail"); add("subject"); add("body") }
                }
            })
            add(buildJsonObject {
                put("name", "searchYouTube")
                put("description", "Search for a query on YouTube app.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
            add(buildJsonObject {
                put("name", "adjustVolume")
                put("description", "Adjust the device volume.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("direction") { 
                            put("type", "STRING") 
                            put("description", "Volume action: 'up', 'down', 'mute', 'unmute', or 'max'")
                        }
                    }
                    putJsonArray("required") { add("direction") }
                }
            })
            add(buildJsonObject {
                put("name", "setVolumePercent")
                put("description", "Set the device volume to a specific percentage (0 to 100).")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("percent") { 
                            put("type", "INTEGER") 
                            put("description", "Volume percentage (0-100)")
                        }
                    }
                    putJsonArray("required") { add("percent") }
                }
            })
            add(buildJsonObject {
                put("name", "getSimCardInfo")
                put("description", "Check how many active SIM cards the device has.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "openQuickSettings")
                put("description", "Pull down the quick settings / components panel (toggles for wifi, bluetooth, etc).")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "clickTextOnScreen")
                put("description", "Click on any text visible on the screen. Acts like a real human finger tap and shows tap effect visually.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("text") {
                            put("type", "STRING")
                            put("description", "The text to tap on the screen")
                        }
                    }
                    putJsonArray("required") { add("text") }
                }
            })
            add(buildJsonObject {
                put("name", "openNotificationPanel")
                put("description", "Pull down the notification bar / status bar to view notifications.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "toggleTorch")
                put("description", "Turn the flashlight/torch on or off.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("state") { 
                            put("type", "STRING") 
                            put("description", "'on' or 'off'")
                        }
                    }
                    putJsonArray("required") { add("state") }
                }
            })
            add(buildJsonObject {
                put("name", "setBrightness")
                put("description", "Set the screen brightness. Note: Requires write settings permission first.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("level") { 
                            put("type", "INTEGER") 
                            put("description", "Brightness level 0 to 100")
                        }
                    }
                    putJsonArray("required") { add("level") }
                }
            })
            add(buildJsonObject {
                put("name", "playMedia")
                put("description", "Play media (like a song, video, or movie) from another app by searching for it.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") { 
                            put("type", "STRING") 
                            put("description", "What to play (e.g. 'Despacito by Luis Fonsi' or 'latest tech news')")
                        }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
        }
    }

    fun startSession() {
        if (webSocket != null) return
        
        val prefs = context.getSharedPreferences("ZoyaPrefs", android.content.Context.MODE_PRIVATE)
        var apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY" || apiKey == "MY_GEMINI_API_KEY") {
            val buildKey = runCatching {
                val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
                field.get(null) as? String
            }.getOrNull() ?: ""
            if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" && buildKey != "YOUR_API_KEY") {
                apiKey = buildKey
                prefs.edit().putString("api_key", apiKey).apply()
            }
        }
        if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY" || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("ZoyaDiagnostic", "No API Key found")
            addMessage("Notice: API Key is required. Please set it via API Key Settings.")
            _zoyaState.value = ZoyaState.IDLE
            return
        }
        
        Log.i("ZoyaDiagnostic", "Connecting to Gemini Live API...")
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("ZoyaDiagnostic", "WebSocket connection OPENED successfully.")
                addMessage("WebSocket Opened")
                isSetupComplete = false
                sendSetupMessage(webSocket)
                _zoyaState.value = ZoyaState.LISTENING
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("ZoyaDiagnostic", "WebSocket Text Msg Received (length: ${text.length})")
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                val text = bytes.utf8()
                Log.d("ZoyaDiagnostic", "WebSocket Binary Msg Received (utf8 length: ${text.length})")
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorBody = response?.body?.string() ?: "No body"
                Log.e("ZoyaDiagnostic", "WebSocket ERROR: ${t.message}, Response: $errorBody", t)
                addMessage("WebSocket Error: ${t.message}. Details: $errorBody")
                _zoyaState.value = ZoyaState.IDLE
                this@LiveSessionManager.webSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("ZoyaDiagnostic", "WebSocket CLOSED. Code: $code, Reason: $reason")
                addMessage("WebSocket Closed: $reason")
                _zoyaState.value = ZoyaState.IDLE
                this@LiveSessionManager.webSocket = null
            }
        })
    }

    private fun sendInitialPrompt(ws: WebSocket) {
        val prefs = context.getSharedPreferences("ZoyaPrefs", android.content.Context.MODE_PRIVATE)
        val selectedVoice = prefs.getString("selected_voice", "Ren - Anime Boy") ?: "Ren - Anime Boy"
        val selectedLanguage = prefs.getString("selected_language", "Hindi") ?: "Hindi"

        val greetingPrompt = if (selectedLanguage.equals("Hindi", ignoreCase = true)) {
            if (selectedVoice.contains("David", ignoreCase = true) || selectedVoice.contains("Cowboy", ignoreCase = true)) {
                "Hindi me bolo: 'Ram Ram partner! David yahan hai. Batao kya madad karu?'"
            } else if (selectedVoice.contains("Ren", ignoreCase = true) || selectedVoice.contains("Anime", ignoreCase = true)) {
                "Hindi me energetic anime hero tone me bolo: 'Konnichiwa! Ren online hai. Batao senpai, aaj kya madad karu?'"
            } else if (selectedVoice.contains("Hiro", ignoreCase = true)) {
                "Hindi me cool calm anime voice me bolo: 'Namaste senpai. Hiro yahan hai. Main aapki kya seva kar sakta hoon?'"
            } else {
                "Hindi me bolo: 'Namaste! Main aapki kya madad kar sakta hoon?'"
            }
        } else {
            if (selectedVoice.contains("David", ignoreCase = true) || selectedVoice.contains("Cowboy", ignoreCase = true)) {
                "Say in your gruff cowboy voice: Howdy partner. David here. What can I do for ya?"
            } else if (selectedVoice.contains("Ren", ignoreCase = true) || selectedVoice.contains("Anime", ignoreCase = true)) {
                "Say in your cool energetic anime boy voice: 'Hey there! Ren here, your anime AI partner. Ready when you are, Senpai!'"
            } else if (selectedVoice.contains("Hiro", ignoreCase = true)) {
                "Say in your calm, cool anime voice: 'Greetings. Hiro online and synced. How can I assist you today?'"
            } else {
                "Hi! Introduce yourself briefly."
            }
        }
        val msg = buildJsonObject {
            putJsonObject("clientContent") {
                putJsonArray("turns") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("text", greetingPrompt)
                            })
                        }
                    })
                }
                put("turnComplete", true)
            }
        }
        ws.send(msg.toString())
    }

    private fun addMessage(msg: String) {
        _messages.value = _messages.value + msg
    }

    fun restartSession() {
        stopSession()
        startSession()
    }

    fun stopSession() {
        webSocket?.close(1000, "User stopped")
        webSocket = null
        _zoyaState.value = ZoyaState.IDLE
        addMessage("Session stopped.")
    }

    fun sendTextMessage(text: String) {
        if (webSocket == null || !isSetupComplete || _zoyaState.value == ZoyaState.IDLE) return
        addMessage("You: $text")
        val msg = buildJsonObject {
            putJsonObject("clientContent") {
                putJsonArray("turns") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", text) })
                        }
                    })
                }
                put("turnComplete", true)
            }
        }
        webSocket?.send(msg.toString())
    }
    
    fun sendAudioData(pcmData: ShortArray, length: Int) {
        if (webSocket == null || !isSetupComplete || _zoyaState.value == ZoyaState.IDLE) {
            return
        }
        
        Log.v("ZoyaDiagnostic", "Sending audio chunk size=${length} to Gemini")
        // Convert ShortArray to ByteArray (Little Endian)
        val byteArray = ByteArray(length * 2)
        for (i in 0 until length) {
            val s = pcmData[i]
            byteArray[i * 2] = (s.toInt() and 0x00FF).toByte()
            byteArray[i * 2 + 1] = (s.toInt() shr 8).toByte()
        }
        
        val base64Data = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        
        val inputMsg = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonArray("mediaChunks") {
                    add(buildJsonObject {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Data)
                    })
                }
            }
        }
        webSocket?.send(inputMsg.toString())
    }
    
    private fun sendSetupMessage(ws: WebSocket) {
        val prefs = context.getSharedPreferences("ZoyaPrefs", android.content.Context.MODE_PRIVATE)
        val selectedVoice = prefs.getString("selected_voice", "Ren - Anime Boy") ?: "Ren - Anime Boy"
        val selectedLanguage = prefs.getString("selected_language", "Hindi") ?: "Hindi"

        val voiceName = when {
            selectedVoice.contains("David", ignoreCase = true) || selectedVoice.contains("Cowboy", ignoreCase = true) -> "Fenrir"
            selectedVoice.contains("Ren", ignoreCase = true) || selectedVoice.contains("Anime", ignoreCase = true) -> "Puck"
            selectedVoice.contains("Hiro", ignoreCase = true) -> "Fenrir"
            selectedVoice.contains("Aoede", ignoreCase = true) -> "Aoede"
            selectedVoice.contains("Fenrir", ignoreCase = true) -> "Fenrir"
            selectedVoice.contains("Puck", ignoreCase = true) -> "Puck"
            selectedVoice.contains("Charon", ignoreCase = true) -> "Charon"
            selectedVoice.contains("Kore", ignoreCase = true) -> "Kore"
            else -> "Puck"
        }

        val languageInstruction = if (selectedLanguage.equals("Hindi", ignoreCase = true)) {
            "LANGUAGE INSTRUCTION (MANDATORY):\n- You MUST speak, converse, and reply entirely in HINDI (natural everyday Hindi / conversational Hinglish as spoken in India).\n- Regardless of whether the user speaks in Hindi, English, or mixed language, your verbal voice response MUST ALWAYS be in Hindi.\n- Never answer in full English. Speak naturally, politely, and fluently in Hindi.\n\n"
        } else {
            ""
        }

        val personaPrompt = if (selectedVoice.contains("David", ignoreCase = true) || selectedVoice.contains("Cowboy", ignoreCase = true)) {
            if (selectedLanguage.equals("Hindi", ignoreCase = true)) {
                "You are David, a gruff, weathered cowboy voice assistant on the user's Android phone. Speak in HINDI with a deep, rugged cowboy cadence and attitude (use authentic cowboy remarks adapted into Hindi like 'Haan partner', 'Theek hai partner, abhi karta hoon', 'Arre partner, ruko thoda', 'Bahut shukriya partner'). Keep responses short, direct, and authoritative in Hindi.\n\n"
            } else {
                "You are David, a gruff, weathered cowboy voice assistant on the user's Android phone. Speak with a deep, rugged, gravelly cowboy voice and terse Western cadence (using authentic cowboy remarks like 'Partner', 'Reckon', 'Hold your horses', 'Much obliged', 'Alright partner'). Keep responses short, direct, and authoritative.\n\n"
            }
        } else if (selectedVoice.contains("Ren", ignoreCase = true) || selectedVoice.contains("Anime", ignoreCase = true)) {
            if (selectedLanguage.equals("Hindi", ignoreCase = true)) {
                "You are Ren, a cool, energetic, and loyal anime boy AI companion on the user's Android phone. Speak in HINDI with a youthful, confident, and spirited anime hero personality (using fun anime expressions adapted into Hindi like 'Haan senpai!', 'Bilkul, abhi karta hoon!', 'Chinta mat karo senpai, main hoon na!', 'Mission accomplished!'). Keep responses snappy, enthusiastic, and direct in Hindi.\n\n"
            } else {
                "You are Ren, a cool, stylish anime boy AI assistant and companion on the user's Android phone. Speak with an energetic, confident, and loyal anime protagonist cadence (using remarks like 'You got it, Senpai!', 'Leave it to me!', 'System ready!'). Keep responses short, direct, and sharp.\n\n"
            }
        } else if (selectedVoice.contains("Hiro", ignoreCase = true)) {
            if (selectedLanguage.equals("Hindi", ignoreCase = true)) {
                "You are Hiro, a calm, cool, and composed anime senpai assistant. Speak in polite, collected, and smooth Hindi ('Haan bilkul', 'Main sambhal lunga', 'Samajh gaya'). Keep responses concise and calm.\n\n"
            } else {
                "You are Hiro, a calm, composed anime senpai assistant. Speak smoothly, calmly, and attentively. Keep responses short and direct.\n\n"
            }
        } else {
            if (selectedLanguage.equals("Hindi", ignoreCase = true)) {
                "You are a fast, helpful AI assistant on the user's Android phone. Always speak in natural, clear, friendly Hindi.\n\n"
            } else {
                "You are a fast, helpful AI assistant on the user's Android phone.\n\n"
            }
        }

        val systemPrompt = personaPrompt + languageInstruction + "CRITICAL RULE: DO NOT output any internal thinking, planning, or narration. NEVER say what you are going to do before doing it. JUST CALL THE TOOL IN SILENCE. Keep your verbal responses EXTREMELY short, brief, and NEVER repeat yourself. Do not use filler words.\n\nCRITICAL: DO NOT INVENT NUMBERS. NEVER DIAL 121. If the user asks to call someone by name (e.g. 'Shivank' or 'Rahul'), you MUST pass their EXACT NAME into the contactName parameter of the tool. The tool will find the number automatically! If you don't know the name, ask the user. DO NOT GUESS NUMBERS.\n\nCALLING INSTRUCTIONS:\nWhen asked to call, DO NOT explain your plan. 1. use getSimCardInfo. 2. use searchAndCallContact with useDialer=true FIRST. This opens the dialer, entirely overwrites/clears any old number, and types the new number so the user can verify it safely. 3. Verbally say ONLY ONCE in Hindi: 'Maine number enter kar diya hai. [Ask for SIM if 2 SIMs present: Kaunse SIM me balance hai, 1 ya 2? Agar confirm hai to call laga du?]' 4. AFTER user confirms, use searchAndCallContact with useDialer=false and simSlot to instantly start the call.\n\nUI ACTIONS:\nTo do real human-like clicks that show onscreen, use clickTextOnScreen, openNotificationPanel, or openQuickSettings.\nIf asked to turn on torch, use toggleTorch. If asked to change brightness, use setBrightness. If asked to set volume, use setVolumePercent. If asked for camera or other apps, use openApp."

        val setupMsg = buildJsonObject {
            putJsonObject("setup") {
                put("model", "models/gemini-2.5-flash-native-audio-latest")
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add("AUDIO") }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", voiceName)
                            }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", systemPrompt)
                        })
                    }
                }
                putJsonArray("tools") {
                    add(toolsJson)
                }
            }
        }
        ws.send(setupMsg.toString())
    }

    private var isSetupComplete = false

    private fun handleServerMessage(text: String) {
        Log.d("LiveSessionManager", "Server msg: $text")
        try {
            val jsonMsg = json.parseToJsonElement(text).jsonObject
            // DEBUUGING: show keys on UI
            addMessage("Server says: ${jsonMsg.keys}")
            
            if (jsonMsg.containsKey("setupComplete")) {
                isSetupComplete = true
                addMessage("Server says: Setup Complete")
                sendInitialPrompt(webSocket!!)
            }
            if (jsonMsg.containsKey("serverContent")) {
                val serverContent = jsonMsg["serverContent"]?.jsonObject
                val modelTurn = serverContent?.get("modelTurn")?.jsonObject
                
                if (serverContent?.get("interrupted")?.jsonPrimitive?.content == "true" || serverContent?.get("interrupted")?.jsonPrimitive?.booleanOrNull == true) {
                    onInterrupt()
                }
                
                modelTurn?.get("parts")?.jsonArray?.forEach { partElement ->
                    val part = partElement.jsonObject
                    
                    if (part.containsKey("inlineData")) {
                       val dataBase64 = part["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content
                       if (dataBase64 != null) {
                           _zoyaState.value = ZoyaState.SPEAKING
                           val rawBytes = Base64.decode(dataBase64, Base64.NO_WRAP)
                           onAudioOut(rawBytes)
                       }
                    }

                    if (part.containsKey("text")) {
                        val textContent = part["text"]?.jsonPrimitive?.content
                        if (!textContent.isNullOrBlank()) {
                            addMessage("Zoya: $textContent")
                        }
                    }
                }
                
                if (serverContent?.containsKey("turnComplete") == true && serverContent["turnComplete"]?.jsonPrimitive?.content == "true") {
                    _zoyaState.value = ZoyaState.LISTENING
                }
            }
            
            if (jsonMsg.containsKey("toolCall")) {
                val toolCallObj = jsonMsg["toolCall"]?.jsonObject
                val functionCalls = toolCallObj?.get("functionCalls")?.jsonArray
                
                functionCalls?.forEach { callElement ->
                    val callObj = callElement.jsonObject
                    val id = callObj["id"]?.jsonPrimitive?.content ?: ""
                    val name = callObj["name"]?.jsonPrimitive?.content ?: ""
                    val args = callObj["args"]?.jsonObject ?: buildJsonObject { }
                    
                    executeToolAndRespond(id, name, args)
                }
            }
        } catch (e: Exception) {
            Log.e("LiveSessionManager", "Error parsing server message", e)
            addMessage("Parsing error: ${e.message}")
        }
    }
    
    private fun executeToolAndRespond(id: String, name: String, args: JsonObject) {
         _zoyaState.value = ZoyaState.THINKING
         scope.launch {
              val resultStr = toolEngine.execute(name, args)
              
              val responseMsg = buildJsonObject {
                  putJsonObject("toolResponse") {
                      putJsonArray("functionResponses") {
                          add(buildJsonObject {
                              put("id", id)
                              put("name", name)
                              putJsonObject("response") {
                                  put("result", resultStr)
                              }
                          })
                      }
                  }
              }
              webSocket?.send(responseMsg.toString())
         }
    }
}
