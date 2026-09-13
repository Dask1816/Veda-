package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.models.AssistantState
import com.example.data.models.ContactInfo
import com.example.ui.components.SoundWaveVisualizer
import com.example.ui.components.VedaOrb

@Composable
fun VedaScreen(viewModel: VedaViewModel) {
    val context = LocalContext.current
    val assistantState by viewModel.assistantState.collectAsState()
    val userTranscript by viewModel.userTranscript.collectAsState()
    val vedaTranscript by viewModel.vedaTranscript.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val detectedLanguage by viewModel.detectedLanguage.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()
    val contactDisambiguation by viewModel.contactDisambiguation.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsState()
    val isSpeakerTestRunning by viewModel.isSpeakerTestRunning.collectAsState()
    val speakerTestResult by viewModel.speakerTestResult.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    // Permission launcher for Microphone and Contacts
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            viewModel.startAssistant()
        }
    }

    LaunchedEffect(Unit) {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070A14),
                        Color(0xFF0D1224),
                        Color(0xFF090D1A)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- TOP BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "VEDA",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Live AI Voice",
                            color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Top Actions: Language Chip, Speaker Test, Diagnostics, API Key
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Auto-detected Language Chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF161E38),
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        Text(
                            text = detectedLanguage,
                            color = Color(0xFF80D8FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Speaker Diagnostic Button
                    IconButton(
                        onClick = { viewModel.runSpeakerDiagnostic() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF161E38), CircleShape)
                            .testTag("speaker_test_button")
                    ) {
                        if (isSpeakerTestRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF00E5FF),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speaker Diagnostic Test",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Diagnostics Log Button
                    IconButton(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF161E38), CircleShape)
                            .testTag("logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = "Diagnostics Logs",
                            tint = Color(0xFFB0BEC5),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Settings / API Key Button
                    IconButton(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY")
                                    Color(0xFFFF6D00).copy(alpha = 0.25f)
                                else Color(0xFF161E38),
                                CircleShape
                            )
                            .testTag("api_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Gemini API Key Settings",
                            tint = if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY")
                                Color(0xFFFFAB00)
                            else Color(0xFF7C4DFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Error Banner (if any)
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFB71C1C).copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss error",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Speaker Test Feedback (if recently run)
            AnimatedVisibility(visible = speakerTestResult != null) {
                speakerTestResult?.let { testMsg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF004D40).copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = testMsg,
                            color = Color(0xFF80CBC4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Action Feedback Notification (when a tool/device action runs)
            AnimatedVisibility(visible = actionFeedback != null) {
                actionFeedback?.let { feedback ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (feedback.success) Color(0xFF003829) else Color(0xFF4A121A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (feedback.action.contains("Call", ignoreCase = true)) Icons.Default.Call else Icons.Default.RecordVoiceOver,
                                contentDescription = "Action feedback",
                                tint = if (feedback.success) Color(0xFF00E676) else Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = feedback.message,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissActionFeedback() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss action feedback",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- CENTER STAGE: STATE BADGE + ORB + SOUNDWAVE ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Status Badge
                val (badgeBg, badgeText, badgeColor) = when (assistantState) {
                    AssistantState.IDLE -> Triple(Color(0xFF161E38), "IDLE • TAP TO WAKE", Color(0xFF80D8FF))
                    AssistantState.CONNECTING -> Triple(Color(0xFF3E2723), "CONNECTING GEMINI LIVE...", Color(0xFFFFB74D))
                    AssistantState.LISTENING -> Triple(Color(0xFF003829), "LISTENING...", Color(0xFF00E5FF))
                    AssistantState.SPEAKING -> Triple(Color(0xFF311B92), "VEDA SPEAKING...", Color(0xFFD500F9))
                    AssistantState.ERROR -> Triple(Color(0xFF4A121A), "OFFLINE / ERROR", Color(0xFFFF5252))
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeBg,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(badgeColor.copy(alpha = 0.5f), badgeColor))
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Center Animated Glowing Orb
                VedaOrb(
                    state = assistantState,
                    audioLevel = audioLevel,
                    modifier = Modifier
                        .size(210.dp)
                        .clickable { viewModel.togglePower() }
                        .testTag("veda_orb")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Reactive Sound Wave Visualizer
                SoundWaveVisualizer(
                    audioLevel = audioLevel,
                    isSpeaking = assistantState == AssistantState.SPEAKING
                )
            }

            // --- REAL-TIME TRANSCRIPT / SUBTITLE BUBBLE ---
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10172C).copy(alpha = 0.9f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF00E5FF).copy(alpha = 0.2f), Color(0xFF7C4DFF).copy(alpha = 0.2f))
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(horizontal = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    when {
                        assistantState == AssistantState.SPEAKING && vedaTranscript.isNotEmpty() -> {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "Veda: ",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = vedaTranscript,
                                    color = Color(0xFFF5F5F5),
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        userTranscript.isNotEmpty() -> {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "You: ",
                                    color = Color(0xFFFFAB40),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = userTranscript,
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        assistantState == AssistantState.LISTENING -> {
                            Text(
                                text = "Speak naturally in Hindi, English, Hinglish, Marathi...",
                                color = Color(0xFF90A4AE),
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            Text(
                                text = "Tap the power button below to speak with Veda",
                                color = Color(0xFF78909C),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // --- QUICK VOICE ACTION PROMPTS ---
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "QUICK VOICE PROMPTS",
                    color = Color(0xFF546E7A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val promptSuggestions = listOf(
                        "Hello Veda!",
                        "WhatsApp kholo",
                        "Open YouTube",
                        "Call 8899361900",
                        "Call Mom",
                        "Hindi mein baat karo",
                        "Tell me a witty joke",
                        "Open Settings"
                    )

                    promptSuggestions.forEach { prompt ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF141C36),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF00E5FF).copy(alpha = 0.35f), Color(0xFF7C4DFF).copy(alpha = 0.35f))
                                )
                            ),
                            modifier = Modifier
                                .clickable { viewModel.sendPrompt(prompt) }
                                .testTag("prompt_chip_${prompt.replace(" ", "_")}")
                        ) {
                            Text(
                                text = prompt,
                                color = Color(0xFFE0F7FA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // --- BOTTOM PRIMARY MIC / POWER BUTTON ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "btn_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (assistantState == AssistantState.LISTENING || assistantState == AssistantState.SPEAKING) 1.14f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "btn_scale"
                )

                val buttonGradient = when (assistantState) {
                    AssistantState.LISTENING -> listOf(Color(0xFF00E5FF), Color(0xFF0091EA))
                    AssistantState.SPEAKING -> listOf(Color(0xFFD500F9), Color(0xFF651FFF))
                    AssistantState.CONNECTING -> listOf(Color(0xFFFFAB00), Color(0xFFFF6D00))
                    AssistantState.ERROR -> listOf(Color(0xFFFF1744), Color(0xFFD50000))
                    AssistantState.IDLE -> listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                }

                Box(contentAlignment = Alignment.Center) {
                    // Outer pulsating glow ring
                    if (assistantState == AssistantState.LISTENING || assistantState == AssistantState.SPEAKING) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(buttonGradient.first().copy(alpha = 0.25f))
                        )
                    }

                    // Main Toggle Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(buttonGradient))
                            .clickable {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasMic) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.RECORD_AUDIO,
                                            Manifest.permission.READ_CONTACTS
                                        )
                                    )
                                } else {
                                    viewModel.togglePower()
                                }
                            }
                            .shadow(elevation = 12.dp, shape = CircleShape)
                            .testTag("power_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        when (assistantState) {
                            AssistantState.IDLE -> {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Start Veda",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            AssistantState.CONNECTING -> {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            AssistantState.LISTENING -> {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Veda Listening",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            AssistantState.SPEAKING -> {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Tap to Interrupt",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            AssistantState.ERROR -> {
                                Icon(
                                    imageVector = Icons.Default.MicOff,
                                    contentDescription = "Retry Veda",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (assistantState) {
                        AssistantState.IDLE -> "Tap to Start Veda"
                        AssistantState.CONNECTING -> "Connecting..."
                        AssistantState.LISTENING -> "Listening • Speak anytime"
                        AssistantState.SPEAKING -> "Tap to interrupt Veda"
                        AssistantState.ERROR -> "Tap to retry"
                    },
                    color = Color(0xFF78909C),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // --- CONTACT DISAMBIGUATION DIALOG ---
        contactDisambiguation?.let { contacts ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissContactDisambiguation() },
                title = {
                    Text(
                        text = "Multiple Contacts Found",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Which contact would you like to call?",
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        LazyColumn(modifier = Modifier.height(180.dp)) {
                            items(contacts) { contact ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2846)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.selectContactForCall(contact) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = contact.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = contact.phoneNumber,
                                                color = Color(0xFF80D8FF),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissContactDisambiguation() }) {
                        Text("Cancel", color = Color(0xFF00E5FF))
                    }
                },
                containerColor = Color(0xFF121829),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // --- API KEY CONFIGURATION DIALOG ---
        if (showApiKeyDialog) {
            var tempKey by remember { mutableStateOf(apiKey) }
            AlertDialog(
                onDismissRequest = { showApiKeyDialog = false },
                title = {
                    Text(
                        text = "Gemini API Key",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter your Google Gemini API key for real-time voice & multimodality. Key is stored securely on your device.",
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF546E7A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveApiKey(tempKey)
                            showApiKeyDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("Save Key", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyDialog = false }) {
                        Text("Cancel", color = Color(0xFFB0BEC5))
                    }
                },
                containerColor = Color(0xFF121829),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // --- DIAGNOSTICS & AUDIO PIPELINE LOGS DIALOG ---
        if (showLogsDialog) {
            AlertDialog(
                onDismissRequest = { showLogsDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Diagnostics & Audio Pipeline",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        TextButton(onClick = { viewModel.runSpeakerDiagnostic() }) {
                            Text("Test 440Hz", color = Color(0xFF00E5FF), fontSize = 12.sp)
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.height(340.dp)) {
                        Text(
                            text = "Real-time logs for WebSocket, AudioTrack, AudioRecord & Tools:",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF080C16), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            items(diagnosticLogs) { logLine ->
                                Text(
                                    text = logLine,
                                    color = when {
                                        logLine.contains("[Error]") || logLine.contains("FAILED") -> Color(0xFFFF5252)
                                        logLine.contains("[Action Result]") || logLine.contains("PASSED") -> Color(0xFF00E676)
                                        logLine.contains("[AudioOut]") -> Color(0xFFD500F9)
                                        logLine.contains("[AudioIn]") -> Color(0xFF00E5FF)
                                        logLine.contains("[User Speech]") -> Color(0xFFFFAB40)
                                        else -> Color(0xFFB0BEC5)
                                    },
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLogsDialog = false }) {
                        Text("Close", color = Color(0xFF00E5FF))
                    }
                },
                containerColor = Color(0xFF121829),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
