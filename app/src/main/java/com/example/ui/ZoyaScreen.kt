package com.example.ui

import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.ZoyaForegroundService
import com.example.live.ZoyaState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border

import androidx.compose.foundation.BorderStroke
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState

import android.content.Context
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear



@Composable
fun ZoyaScreen() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToChat = { navController.navigate("chat") }
            )
        }
        composable("chat") {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToChat: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE) }
    val initialKey = remember {
        val storedKey = prefs.getString("api_key", "") ?: ""
        if (storedKey.isNotBlank()) storedKey
        else {
            val buildKey = runCatching {
                val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
                field.get(null) as? String
            }.getOrNull() ?: ""
            if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" && buildKey != "YOUR_API_KEY") {
                prefs.edit().putString("api_key", buildKey).apply()
                buildKey
            } else ""
        }
    }
    var apiKey by remember { mutableStateOf(initialKey) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var selectedVoice by remember { 
        mutableStateOf(prefs.getString("selected_voice", "Ren - Anime Boy") ?: "Ren - Anime Boy") 
    }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { 
        mutableStateOf(prefs.getString("selected_language", "Hindi") ?: "Hindi") 
    }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var isFullArtMode by remember { mutableStateOf(false) }
    var zoyaState by remember { mutableStateOf(ZoyaForegroundService.currentState) }
    var serviceStarted by remember { mutableStateOf(ZoyaForegroundService.activeService != null) }
    var showMenu by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.RECORD_AUDIO] == true) {
            val intent = Intent(context, ZoyaForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
            serviceStarted = true
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        ZoyaForegroundService.onStateChange = { state ->
            zoyaState = state
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (serviceStarted) Color(0xFF00FFA3) else Color(0xFFFF9100),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("REN", color = Color(0xFF00E5FF), fontWeight = FontWeight.Black, fontSize = 21.sp, letterSpacing = 2.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("// 蓮", color = Color(0xFFD500F9), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                if (serviceStarted) "AI COMPANION • SYNCED" else "ANIME AI COMPANION • STANDBY",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 9.sp,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    androidx.compose.material3.IconButton(
                        onClick = { isFullArtMode = !isFullArtMode },
                        modifier = Modifier
                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Text(if (isFullArtMode) "💫" else "🖼️", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    androidx.compose.material3.IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        Text("⚙", color = Color.White, fontSize = 20.sp)
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF16152B).copy(alpha = 0.95f))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Voice Style (${if (selectedVoice.length > 14) selectedVoice.take(12) + "..." else selectedVoice})", color = Color(0xFFFFB74D)) },
                            onClick = {
                                showMenu = false
                                showVoiceDialog = true
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Language ($selectedLanguage)", color = Color(0xFF64B5F6)) },
                            onClick = {
                                showMenu = false
                                showLanguageDialog = true
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (isFullArtMode) "Switch to Avatar Mode" else "Switch to Full Art View", color = Color(0xFF00FFA3)) },
                            onClick = {
                                showMenu = false
                                isFullArtMode = !isFullArtMode
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("View Logs", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onNavigateToChat()
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("API Key Settings", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showApiKeyDialog = true
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Accessibility Settings (Auto-Click)", color = Color.White) },
                            onClick = {
                                showMenu = false
                                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1E1738), Color(0xFF0B0D19), Color(0xFF06070E)),
                        radius = 1600f
                    )
                )
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF14142B).copy(alpha = 0.75f),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.4f),
                                    Color(0xFFD500F9).copy(alpha = 0.4f)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimeBoyCharacterView(
                            state = zoyaState,
                            isFullArtMode = isFullArtMode,
                            onToggleMode = { isFullArtMode = !isFullArtMode },
                            selectedLanguage = selectedLanguage
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Voice and Language Selectors
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.Surface(
                                onClick = { showVoiceDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF22173B).copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, Color(0xFFD500F9).copy(alpha = 0.7f)),
                                modifier = Modifier.testTag("voice_selector_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        if (selectedVoice.contains("Ren") || selectedVoice.contains("Anime")) "⚡"
                                        else if (selectedVoice.contains("Hiro")) "🌟"
                                        else if (selectedVoice.contains("David")) "🤠"
                                        else "🎙️",
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Column {
                                        Text("VOICE", fontSize = 8.sp, color = Color(0xFFD500F9), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                                        Text(
                                            if (selectedVoice.length > 12) selectedVoice.take(10) + "..." else selectedVoice,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("▾", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            androidx.compose.material3.Surface(
                                onClick = { showLanguageDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF0F263E).copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
                                modifier = Modifier.testTag("language_selector_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(if (selectedLanguage.equals("Hindi", ignoreCase = true)) "🇮🇳" else "🌐", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Column {
                                        Text("LANGUAGE", fontSize = 8.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                                        Text(if (selectedLanguage.equals("Hindi", ignoreCase = true)) "Hindi" else "English", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("▾", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Controls
                        if (!serviceStarted) {
                            if (apiKey.isEmpty()) {
                                androidx.compose.material3.Button(
                                    modifier = Modifier.testTag("setup_api_button"),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.1f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    onClick = { showApiKeyDialog = true }
                                ) {
                                    Text("Setup API Key", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp))
                                }
                            } else {
                                androidx.compose.material3.Button(
                                    modifier = Modifier.testTag("start_zoya_button"),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFD500F9)))),
                                    onClick = {
                                        val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        val hasContacts = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        val hasPhone = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        
                                        if (hasMic && hasContacts && hasPhone) {
                                            val intent = Intent(context, ZoyaForegroundService::class.java)
                                            ContextCompat.startForegroundService(context, intent)
                                            serviceStarted = true
                                        } else {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.RECORD_AUDIO,
                                                    android.Manifest.permission.READ_CONTACTS,
                                                    android.Manifest.permission.CALL_PHONE
                                                )
                                            )
                                        }
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color(0xFF00E5FF).copy(alpha = 0.25f),
                                                        Color(0xFFD500F9).copy(alpha = 0.25f)
                                                    )
                                                ),
                                                RoundedCornerShape(26.dp)
                                            )
                                            .padding(vertical = 12.dp, horizontal = 28.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("⚡", fontSize = 18.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("LINK WITH REN", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
                                        }
                                    }
                                }
                            }
                        } else if (zoyaState == ZoyaState.IDLE) {
                            androidx.compose.material3.Button(
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                    contentColor = Color(0xFF00E5FF)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                                onClick = {
                                    val service = ZoyaForegroundService.activeService
                                    if (service != null) {
                                        service.reconnectSession()
                                    } else {
                                        val intent = Intent(context, ZoyaForegroundService::class.java)
                                        ContextCompat.startForegroundService(context, intent)
                                    }
                                }
                            ) {
                                Text("⚡ Reconnect Uplink", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp))
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            androidx.compose.material3.Button(
                                onClick = {
                                    val intent = Intent(context, ZoyaForegroundService::class.java)
                                    context.stopService(intent)
                                    serviceStarted = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                                    contentColor = Color(0xFFEF9A9A)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Terminate Session", fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp))
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF00E5FF).copy(alpha = 0.15f),
                                                Color(0xFFD500F9).copy(alpha = 0.15f)
                                            )
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF00FFA3), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (zoyaState) {
                                        ZoyaState.LISTENING -> "LISTENING TO SENPAI..."
                                        ZoyaState.THINKING -> "COMPUTING RESPONSE..."
                                        ZoyaState.SPEAKING -> "TRANSMITTING VOICE..."
                                        else -> "LINK ACTIVE"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(18.dp))
                            
                            androidx.compose.material3.Button(
                                onClick = {
                                    val intent = Intent(context, ZoyaForegroundService::class.java)
                                    context.stopService(intent)
                                    serviceStarted = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                                    contentColor = Color(0xFFEF9A9A)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Disconnect Link", fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp))
                            }
                        }

                        // Quick voice actions
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "QUICK COMMANDS",
                            color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("📞 Call", "🔦 Torch", "☀️ Brightness", "📸 Camera").forEach { chipText ->
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            android.widget.Toast.makeText(context, "Tell Ren: '$chipText'", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Text(chipText, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }    
    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text("Enter your Gemini API key to use Z.O.Y.A.")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        placeholder = { Text("AIza...") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            if (tempKey.isNotEmpty()) {
                                androidx.compose.material3.IconButton(onClick = { tempKey = "" }) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear text"
                                    )
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Get your API key here",
                        color = Color(0xFF00B0FF),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.edit().putString("api_key", tempKey).apply()
                        apiKey = tempKey
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showVoiceDialog) {
        val voiceOptions = listOf(
            Triple("Ren - Anime Boy", "Cool, energetic & loyal anime hero ('Senpai!', 'Ikuzo!')", "⚡"),
            Triple("Hiro - Calm Senpai", "Calm, protective & composed anime companion", "🌟"),
            Triple("David - Gruff Cowboy", "Gruff, weathered cowboy drawl ('Partner', 'Reckon')", "🤠"),
            Triple("Aoede", "Breezy, natural & conversational tone", "🎵"),
            Triple("Fenrir", "Deep, dramatic & authoritative", "🐺"),
            Triple("Puck", "Playful, upbeat & direct tone", "✨"),
            Triple("Charon", "Calm, deep & informative style", "🎙️"),
            Triple("Kore", "Firm, confident & crisp delivery", "🌸")
        )

        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            containerColor = Color(0xFF1E1E2E),
            title = {
                Text("Select Voice", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Choose the voice & persona for your assistant:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    voiceOptions.forEach { (voiceName, description, icon) ->
                        val isSelected = selectedVoice == voiceName
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isSelected) Color(0xFFFFB74D).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    prefs.edit().putString("selected_voice", voiceName).apply()
                                    selectedVoice = voiceName
                                    ZoyaForegroundService.activeService?.restartLiveSession()
                                    android.widget.Toast.makeText(context, "Voice set to: $voiceName", android.widget.Toast.LENGTH_SHORT).show()
                                    showVoiceDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(icon, fontSize = 20.sp)
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = voiceName,
                                        color = if (isSelected) Color(0xFFFFB74D) else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = description,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                                if (isSelected) {
                                    Text("✓", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceDialog = false }) {
                    Text("Close", color = Color(0xFFFFB74D))
                }
            }
        )
    }

    if (showLanguageDialog) {
        val languageOptions = listOf(
            Triple("Hindi", "Hindi (हिंदी) - बात करने की भाषा हिंदी", "🇮🇳"),
            Triple("English", "English - Speak in English", "🌐")
        )

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = Color(0xFF1E1E2E),
            title = {
                Text("Select Language / भाषा चुनें", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Assistant kis bhasha me baat kare:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    languageOptions.forEach { (langKey, description, icon) ->
                        val isSelected = selectedLanguage.equals(langKey, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isSelected) Color(0xFF64B5F6).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    prefs.edit().putString("selected_language", langKey).apply()
                                    selectedLanguage = langKey
                                    ZoyaForegroundService.activeService?.restartLiveSession()
                                    android.widget.Toast.makeText(context, "Language: $langKey", android.widget.Toast.LENGTH_SHORT).show()
                                    showLanguageDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(icon, fontSize = 22.sp)
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (langKey == "Hindi") "Hindi (हिंदी)" else "English",
                                        color = if (isSelected) Color(0xFF64B5F6) else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = description,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                                if (isSelected) {
                                    Text("✓", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close", color = Color(0xFF64B5F6))
                }
            }
        )
    }

}



@Composable
fun AnimeBoyCharacterView(
    state: ZoyaState,
    isFullArtMode: Boolean,
    onToggleMode: () -> Unit,
    selectedLanguage: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "anime_boy_fx")
    
    // Float animation for idle/breathing
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // Pulse scale based on state
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when (state) {
            ZoyaState.LISTENING -> 1.15f
            ZoyaState.SPEAKING -> 1.22f
            ZoyaState.THINKING -> 1.08f
            else -> 1.04f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (state) {
                    ZoyaState.SPEAKING -> 350
                    ZoyaState.LISTENING -> 650
                    ZoyaState.THINKING -> 800
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Ring rotation
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                if (state == ZoyaState.THINKING || state == ZoyaState.SPEAKING) 4000 else 10000,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotate"
    )

    val ring2Rotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                if (state == ZoyaState.THINKING || state == ZoyaState.SPEAKING) 5500 else 13000,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2_rotate"
    )

    // Dynamic state colors
    val glowColor = when (state) {
        ZoyaState.IDLE -> Color(0xFF00E5FF)
        ZoyaState.LISTENING -> Color(0xFFD500F9)
        ZoyaState.THINKING -> Color(0xFFFF9100)
        ZoyaState.SPEAKING -> Color(0xFF00FFA3)
        else -> Color(0xFF00E5FF)
    }

    val stateText = when (state) {
        ZoyaState.IDLE -> if (selectedLanguage.equals("Hindi", ignoreCase = true)) "Konnichiwa Senpai! Ren online hai ⚡" else "Hey Senpai! Ready for orders ⚡"
        ZoyaState.LISTENING -> if (selectedLanguage.equals("Hindi", ignoreCase = true)) "Sun raha hoon senpai... 🎧" else "Listening to Senpai... 🎧"
        ZoyaState.THINKING -> if (selectedLanguage.equals("Hindi", ignoreCase = true)) "Soch raha hoon... ⚡" else "Processing neural net... ⚡"
        ZoyaState.SPEAKING -> if (selectedLanguage.equals("Hindi", ignoreCase = true)) "Ren bol raha hai... 🎙️" else "Ren speaking... 🎙️"
        else -> "Standby"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Holographic Speech Bubble
        Box(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.25f),
                            Color(0xFF1E163B).copy(alpha = 0.9f),
                            glowColor.copy(alpha = 0.25f)
                        )
                    ),
                    RoundedCornerShape(18.dp)
                )
                .border(
                    BorderStroke(1.dp, Brush.horizontalGradient(listOf(glowColor, Color(0xFFD500F9)))),
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stateText,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (isFullArtMode) {
            // Full Art Mode: High-Tech Cyber Companion Card
            Box(
                modifier = Modifier
                    .size(width = 240.dp, height = 300.dp)
                    .clickable { onToggleMode() }
                    .background(Color(0xFF101426), RoundedCornerShape(24.dp))
                    .border(
                        BorderStroke(2.dp, Brush.verticalGradient(listOf(glowColor, Color(0xFFD500F9)))),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_anime_boy_full),
                    contentDescription = "Anime Boy Full Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                )

                // Holographic Cyber Gradient Overlay & Tag
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF0A0E1A).copy(alpha = 0.95f))
                            )
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("REN // PROTOCOL-01", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("TAP FOR AVATAR RING", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                        }
                        Text("💫", fontSize = 16.sp)
                    }
                }
            }
        } else {
            // Circular Cyber Avatar Ring Mode
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .offset { IntOffset(0, floatY.toInt()) }
                    .clickable { onToggleMode() },
                contentAlignment = Alignment.Center
            ) {
                // Background Glow & Cyber Rings Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerOffset = center
                    val radius = size.minDimension / 2f - 24f

                    // 1. Ambient Glow Aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.45f), Color.Transparent),
                            center = centerOffset,
                            radius = radius * 1.35f * auraScale
                        ),
                        radius = radius * 1.35f * auraScale
                    )

                    // 2. Outer Rotating Cyber Ring (Dashed/Segmented)
                    rotate(ringRotation, centerOffset) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    glowColor,
                                    Color(0xFFD500F9),
                                    Color.Transparent,
                                    glowColor,
                                    Color(0xFF00FFA3),
                                    Color.Transparent
                                )
                            ),
                            radius = radius + 14f,
                            style = Stroke(
                                width = 3f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f), 0f)
                            )
                        )
                    }

                    // 3. Counter-rotating Inner Tech Ring
                    rotate(ring2Rotation, centerOffset) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFFD500F9),
                                    Color.Transparent,
                                    Color(0xFF00E5FF),
                                    Color.Transparent
                                )
                            ),
                            radius = radius + 4f,
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 25f), 0f)
                            )
                        )
                    }
                }

                // Inner Avatar Image
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .border(
                            BorderStroke(3.dp, Brush.linearGradient(listOf(glowColor, Color(0xFFD500F9)))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_anime_boy_avatar),
                        contentDescription = "Anime Boy Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Holographic Mode Switch Chip at bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-6).dp)
                        .background(Color(0xFF0F172A).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                        .border(1.dp, glowColor.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⚡ TAP FOR FULL ART",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Audio Equalizer Bars
        AudioEqualizerBars(state = state, glowColor = glowColor)
    }
}

@Composable
fun AudioEqualizerBars(state: ZoyaState, glowColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val heights = (0..6).map { index ->
        val duration = 240 + (index * 65)
        infiniteTransition.animateFloat(
            initialValue = if (state == ZoyaState.SPEAKING || state == ZoyaState.LISTENING) 6f else 4f,
            targetValue = if (state == ZoyaState.SPEAKING) 24f + (index % 3) * 8f else if (state == ZoyaState.LISTENING) 16f else 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(32.dp)
    ) {
        heights.forEachIndexed { index, heightAnim ->
            val barColor = if (index % 2 == 0) glowColor else Color(0xFFD500F9)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(heightAnim.value.dp)
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}



@Composable
fun ZoyaOrb(state: ZoyaState) {
    val radiusScale = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0.5f) }
    val rotateAngle = remember { Animatable(0f) }
    
    // Ring rotations
    val ring1Angle = remember { Animatable(0f) }
    val ring2Angle = remember { Animatable(120f) }
    val ring3Angle = remember { Animatable(240f) }
    val ring4Angle = remember { Animatable(45f) }

    LaunchedEffect(state) {
        when (state) {
            ZoyaState.IDLE -> {
                radiusScale.animateTo(1f, animationSpec = tween(1000))
                glowAlpha.animateTo(
                    targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.LISTENING -> {
                radiusScale.animateTo(1.1f, animationSpec = tween(500))
                glowAlpha.animateTo(
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.THINKING -> {
                radiusScale.animateTo(1.05f, animationSpec = tween(400))
                glowAlpha.animateTo(
                    targetValue = 0.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.SPEAKING -> {
                radiusScale.animateTo(1.2f, animationSpec = tween(200))
                glowAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(300, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        }
    }

    // Continuous rotation for rings
    LaunchedEffect(Unit) {
        launch {
            ring1Angle.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(6000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            ring2Angle.animateTo(
                targetValue = 360f + 120f,
                animationSpec = infiniteRepeatable(
                    animation = tween(7000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            ring3Angle.animateTo(
                targetValue = 360f + 240f,
                animationSpec = infiniteRepeatable(
                    animation = tween(5500, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            ring4Angle.animateTo(
                targetValue = -360f + 45f, // reverse rotation
                animationSpec = infiniteRepeatable(
                    animation = tween(8000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Box(
        modifier = Modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 4f
            val currentRadius = baseRadius * radiusScale.value
            
            // Core colors based on state
            val coreInnerColor = when (state) {
                ZoyaState.IDLE -> Color(0xFF80D8FF)
                ZoyaState.LISTENING -> Color(0xFFB388FF)
                ZoyaState.THINKING -> Color(0xFFFFD180)
                ZoyaState.SPEAKING -> Color(0xFF69F0AE)
                else -> Color.LightGray
            }
            
            val coreOuterColor = when (state) {
                ZoyaState.IDLE -> Color(0xFF00B0FF)
                ZoyaState.LISTENING -> Color(0xFF651FFF)
                ZoyaState.THINKING -> Color(0xFFFF9100)
                ZoyaState.SPEAKING -> Color(0xFF00E676)
                else -> Color.Gray
            }

            // 1. Ambient Background Glow
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(coreOuterColor.copy(alpha = glowAlpha.value * 0.5f), Color.Transparent),
                    center = center,
                    radius = currentRadius * 2.5f
                ),
                radius = currentRadius * 2.5f
            )

            // 2. The Glass Sphere (Core)
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        coreInnerColor.copy(alpha = 0.8f),
                        coreOuterColor.copy(alpha = 0.9f),
                        Color.Black.copy(alpha = 0.5f)
                    ),
                    center = androidx.compose.ui.geometry.Offset(center.x - currentRadius * 0.3f, center.y - currentRadius * 0.3f),
                    radius = currentRadius * 1.2f
                ),
                radius = currentRadius
            )
            
            // Inner Core Highlight for 3D effect
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                center = androidx.compose.ui.geometry.Offset(center.x - currentRadius * 0.4f, center.y - currentRadius * 0.4f),
                radius = currentRadius * 0.3f
            )

            // 3. Neon Orbital Rings
            val ringRadiusX = currentRadius * 1.8f
            val ringRadiusY = currentRadius * 0.6f
            
            // Helper function to draw a 3D-ish ring
            fun drawNeonRing(angle: Float, startColor: Color, endColor: Color, strokeWidth: Float) {
                rotate(angle, center) {
                    drawOval(
                        brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                            colors = listOf(startColor, endColor, startColor, Color.Transparent, startColor),
                            center = center
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - ringRadiusX, center.y - ringRadiusY),
                        size = androidx.compose.ui.geometry.Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth)
                    )
                    // Glow for the ring
                    drawOval(
                        color = startColor.copy(alpha = 0.3f),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - ringRadiusX, center.y - ringRadiusY),
                        size = androidx.compose.ui.geometry.Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth * 3)
                    )
                }
            }

            // Draw Rings
            val speedMultiplier = if (state == ZoyaState.THINKING || state == ZoyaState.SPEAKING) 2f else 1f
            
            // Red/Pink Ring
            drawNeonRing(ring1Angle.value * speedMultiplier, Color(0xFFFF1744), Color(0xFFD50000), 4f)
            
            // Green/Yellow Ring
            drawNeonRing(ring2Angle.value * speedMultiplier, Color(0xFF00E676), Color(0xFF76FF03), 4f)
            
            // Blue/Cyan Ring
            drawNeonRing(ring3Angle.value * speedMultiplier, Color(0xFF00E5FF), Color(0xFF2979FF), 4f)
            
            // Outer subtle glass ring
            drawNeonRing(ring4Angle.value * speedMultiplier, Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f), 2f)
            
            // 4. Outer Glass Dome Reflection
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.3f)),
                    center = center,
                    radius = currentRadius * 2.2f
                ),
                radius = currentRadius * 2.2f,
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
fun ChatScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val liveSessionManager = ZoyaForegroundService.activeService?.liveSessionManager
    val messages = liveSessionManager?.messages?.collectAsState(initial = emptyList())?.value ?: emptyList()

    androidx.compose.material3.Scaffold(
        containerColor = Color(0xFF1E1E2E),
        topBar = {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Button(
                    onClick = onNavigateBack,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF80D8FF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Back", color = Color.Black)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "State: ${ZoyaForegroundService.currentState.name}",
                color = Color(0xFF00E5FF),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    Text(
                        text = message,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.material3.Button(
                onClick = { ZoyaForegroundService.activeService?.reconnectSession() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF80D8FF)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Reconnect", color = Color.Black)
            }
        }
    }
}
