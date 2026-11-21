package com.cg.readingcompanion.android

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun AppScreen(vm: AppViewModel, onMicTap: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.turns.size, state.isThinking) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.lastIndex)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Cream)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            TopBar(onSettings = { showSettings = true })
            Spacer(Modifier.height(12.dp))
            ContextCard(state)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.turns) { turn -> ChatBubble(turn) }
                if (state.isThinking) {
                    item { Text("在想…", color = Ink.copy(alpha = 0.5f), fontSize = 13.sp) }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("也可以打字问") },
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                    val value = text.trim()
                    if (value.isNotEmpty()) {
                        text = ""
                        vm.ask(value)
                    }
                }) { Text("发送") }
            }
            Spacer(Modifier.height(8.dp))
            BottomBar(state, vm, onMicTap)
        }
    }

    if (showSettings) {
        SettingsDialog(vm, onDismiss = { showSettings = false })
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("橘猫陪读", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("读到哪，就陪你聊到哪", fontSize = 12.sp, color = Ink.copy(alpha = 0.55f))
        }
        Text("⚙", fontSize = 24.sp, color = Ink.copy(alpha = 0.7f),
            modifier = Modifier.clickable { onSettings() }.padding(8.dp))
    }
}

@Composable
private fun ContextCard(state: AppUiState) {
    val ctx = state.context
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (ctx.book.isBlank()) Orange.copy(alpha = 0.35f) else Color(0xFF4A9E5C), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(ctx.book.ifBlank { "等 Kindle 翻页…" }, fontSize = 14.sp, color = Ink.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            if (ctx.book.isNotBlank()) Text(ctx.pageLabel, fontSize = 11.sp, color = Ink.copy(alpha = 0.5f))
        }
        if (ctx.chapter.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(ctx.chapter, fontSize = 12.sp, color = Ink.copy(alpha = 0.5f), maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (ctx.progress / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Orange.copy(alpha = 0.75f),
            trackColor = Orange.copy(alpha = 0.13f),
        )
        Spacer(Modifier.height(6.dp))
        Text(state.address, fontSize = 10.sp, color = Ink.copy(alpha = 0.45f))
    }
}

@Composable
private fun ChatBubble(turn: ChatTurn) {
    val isUser = turn.role == "user"
    Row(Modifier.fillMaxWidth()) {
        if (isUser) Spacer(Modifier.weight(1f))
        Text(
            turn.text,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (isUser) Orange.copy(alpha = 0.13f) else Color.Transparent)
                .padding(horizontal = if (isUser) 12.dp else 0.dp, vertical = if (isUser) 8.dp else 2.dp),
            color = Ink.copy(alpha = 0.9f),
            fontSize = if (isUser) 16.sp else 18.sp
        )
        if (!isUser) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun BottomBar(state: AppUiState, vm: AppViewModel, onMicTap: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(
        if (state.isRecording) Orange.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.55f),
        label = "recording-bg"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable {
                if (!state.isThinking && !state.isSpeaking && !state.isTranscribing) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onMicTap()
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CatBadge(state)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    state.isThinking -> "在想…"
                    state.isSpeaking -> "说话中"
                    state.isRecording -> "听你说…"
                    else -> state.status
                },
                fontSize = 14.sp,
                color = Ink.copy(alpha = 0.8f)
            )
            Text(
                when {
                    state.isThinking -> "点右侧停止可打断"
                    state.isSpeaking -> "轻点停止回答"
                    state.isRecording -> "再点一下发送"
                    state.isTranscribing -> "正在识别…"
                    else -> "点整条开始说话"
                },
                fontSize = 11.sp,
                color = Ink.copy(alpha = 0.45f)
            )
        }
        if (state.isThinking || state.isSpeaking) {
            TextButton(onClick = { vm.stopResponding() }) { Text("停止", color = Orange) }
        }
    }
}

@Composable
private fun CatBadge(state: AppUiState) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.isRecording, state.isSpeaking) {
        while (state.isRecording || state.isSpeaking) {
            delay(160)
            tick++
        }
    }
    val drawable = when {
        state.isSpeaking -> listOf(R.drawable.cat_talk1, R.drawable.cat_talk2, R.drawable.cat_talk3)[tick % 3]
        state.isRecording -> if (tick % 2 == 0) R.drawable.cat_listen1 else R.drawable.cat_listen2
        else -> R.drawable.cat_idle
    }
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
    )
}

@Composable
private fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var apiKey by remember { mutableStateOf(vm.apiKey()) }
    var model by remember { mutableStateOf(vm.model()) }
    var secretId by remember { mutableStateOf(vm.tencentSecretId()) }
    var secretKey by remember { mutableStateOf(vm.tencentSecretKey()) }
    var region by remember { mutableStateOf(vm.tencentRegion()) }
    var token by remember { mutableStateOf(vm.token()) }
    var port by remember { mutableStateOf(vm.port().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("OpenCode Go API Key") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("模型") }, singleLine = true)
                OutlinedTextField(secretId, { secretId = it }, label = { Text("腾讯云 SecretId") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(secretKey, { secretKey = it }, label = { Text("腾讯云 SecretKey") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(region, { region = it }, label = { Text("地域") }, singleLine = true)
                OutlinedTextField(token, { token = it }, label = { Text("Kindle Token") }, singleLine = true)
                OutlinedTextField(port, { port = it }, label = { Text("端口") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                TextButton(onClick = { vm.previewTts() }) {
                    Text("试听 Edge TTS")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveSettings(apiKey, model, secretId, secretKey, region, token, port.toIntOrNull() ?: 8790)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
