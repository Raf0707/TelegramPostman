package raf.console.tg_postman.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import raf.console.tg_postman.components.RadioButtonWithLabel
import raf.console.tg_postman.data.TelegramDataStore
import raf.console.tg_postman.data.TelegramSettings
import raf.console.tg_postman.service.TelegramBotService


enum class SendMode {
    ONCE, MULTIPLE, DURATION
}

enum class DurationSubMode {
    TIMES_PER_SECONDS, FIXED_INTERVAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramPostmanScreen() {
    val context = LocalContext.current
    val dataStore = remember { TelegramDataStore(context) }
    val coroutineScope = rememberCoroutineScope()
    val botService = remember { TelegramBotService() }
    val focusManager = LocalFocusManager.current

    // Общие состояния
    var botName by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf("channel") }
    val chatIds = remember { mutableStateListOf("") }
    var status by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }

    // Режимы отправки
    var sendMode by rememberSaveable { mutableStateOf(SendMode.ONCE) }
    var sendWithDelay by rememberSaveable { mutableStateOf(false) }
    var delaySeconds by rememberSaveable { mutableFloatStateOf(20f) }

    var sendCount by rememberSaveable { mutableStateOf("3") }
    var intervalSeconds by rememberSaveable { mutableFloatStateOf(20f) }

    var durationSubMode by rememberSaveable { mutableStateOf(DurationSubMode.TIMES_PER_SECONDS) }
    var durationTotalTime by rememberSaveable { mutableStateOf("60") }
    var durationSendCount by rememberSaveable { mutableStateOf("3") }
    var durationFixedInterval by rememberSaveable { mutableStateOf("10") }

    fun saveAll() {
        coroutineScope.launch {
            dataStore.saveSettings(
                TelegramSettings(
                    botName = botName,
                    token = token,
                    selectedType = selectedType,
                    chatIds = chatIds.toList(),
                    sendOnce = sendMode == SendMode.ONCE,
                    interval = delaySeconds,
                    message = message
                )
            )
        }
    }
    LaunchedEffect(Unit) {
        dataStore.settingsFlow.collect { settings ->
            botName = settings.botName
            token = settings.token
            selectedType = settings.selectedType
            chatIds.clear()
            chatIds.addAll(settings.chatIds)
            sendMode = if (settings.sendOnce) SendMode.ONCE else SendMode.MULTIPLE
            delaySeconds = settings.interval
            message = settings.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                focusManager.clearFocus()
            }
            .padding(16.dp)
    ) {
        Text("Telegram Postman", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = botName,
            onValueChange = { botName = it; saveAll() },
            label = { Text("Название бота") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it; saveAll() },
            label = { Text("Bot Token") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Text("Тип назначения:", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedType == "channel",
                onClick = { selectedType = "channel"; saveAll() }
            )
            Text("Канал")
            Spacer(Modifier.width(16.dp))
            RadioButton(
                selected = selectedType == "group",
                onClick = { selectedType = "group"; saveAll() }
            )
            Text("Группа")
        }

        Spacer(Modifier.height(8.dp))
        Text("Chat ID", style = MaterialTheme.typography.titleMedium)

        chatIds.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        chatIds[index] = it
                        saveAll()
                    },
                    label = { Text("Chat ID ${index + 1}") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 8.dp)
                )
                if (chatIds.size > 1) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        chatIds.removeAt(index)
                        saveAll()
                    }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClick = {
                if (chatIds.size < 1000) {
                    chatIds.add("")
                    saveAll()
                }
            }) {
                Text("Добавить")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Режим отправки:", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            // --- Отправить 1 раз ---
            RadioButtonWithLabel(
                selected = sendMode == SendMode.ONCE,
                onClick = { sendMode = SendMode.ONCE; saveAll() },
                label = "Отправить 1 раз"
            )

            if (sendMode == SendMode.ONCE) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sendWithDelay,
                        onCheckedChange = { sendWithDelay = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Включить таймер")
                }

                if (sendWithDelay) {
                    Spacer(Modifier.height(8.dp))
                    Text("Таймер отправки")
                    Text("Задержка: ${delaySeconds.toInt()} секунд")
                    Slider(
                        value = delaySeconds,
                        onValueChange = { delaySeconds = it; saveAll() },
                        valueRange = 1f..180f,
                        steps = 20
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Отправить несколько раз ---
            RadioButtonWithLabel(
                selected = sendMode == SendMode.MULTIPLE,
                onClick = { sendMode = SendMode.MULTIPLE; saveAll() },
                label = "Отправить несколько раз"
            )

            if (sendMode == SendMode.MULTIPLE) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sendCount,
                    onValueChange = { sendCount = it },
                    label = { Text("Количество раз") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Частота отправки")
                Text("Интервал: ${intervalSeconds.toInt()} секунд")
                Slider(
                    value = intervalSeconds,
                    onValueChange = { intervalSeconds = it },
                    valueRange = 1f..180f,
                    steps = 20
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sendWithDelay,
                        onCheckedChange = { sendWithDelay = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Включить таймер")
                }

                if (sendWithDelay) {
                    Spacer(Modifier.height(8.dp))
                    Text("Задержка: ${delaySeconds.toInt()} секунд")
                    Slider(
                        value = delaySeconds,
                        onValueChange = { delaySeconds = it },
                        valueRange = 1f..180f,
                        steps = 20
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Отправлять в течение времени ---
            RadioButtonWithLabel(
                selected = sendMode == SendMode.DURATION,
                onClick = { sendMode = SendMode.DURATION; saveAll() },
                label = "Отправлять в течение времени"
            )

            if (sendMode == SendMode.DURATION) {
                Spacer(Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(12.dp)) {
                        RadioButtonWithLabel(
                            selected = durationSubMode == DurationSubMode.TIMES_PER_SECONDS,
                            onClick = { durationSubMode = DurationSubMode.TIMES_PER_SECONDS },
                            label = "Отправить N раз за K секунд"
                        )
                        RadioButtonWithLabel(
                            selected = durationSubMode == DurationSubMode.FIXED_INTERVAL,
                            onClick = { durationSubMode = DurationSubMode.FIXED_INTERVAL },
                            label = "За K секунд с интервалом X"
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (durationSubMode == DurationSubMode.TIMES_PER_SECONDS) {
                    Row {
                        OutlinedTextField(
                            value = durationSendCount,
                            onValueChange = { durationSendCount = it },
                            label = { Text("Количество (N)") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = durationTotalTime,
                            onValueChange = { durationTotalTime = it },
                            label = { Text("Время (K)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val l = runCatching {
                        durationTotalTime.toInt() / durationSendCount.toInt()
                    }.getOrNull()

                    if (l != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Интервал: $l секунд")
                    }
                }

                if (durationSubMode == DurationSubMode.FIXED_INTERVAL) {
                    Row {
                        OutlinedTextField(
                            value = durationTotalTime,
                            onValueChange = { durationTotalTime = it },
                            label = { Text("Время (K)") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = durationFixedInterval,
                            onValueChange = { durationFixedInterval = it },
                            label = { Text("Интервал (X)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val times = runCatching {
                        durationTotalTime.toInt() / durationFixedInterval.toInt()
                    }.getOrNull()

                    if (times != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Будет отправлено: $times сообщений")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = message,
            onValueChange = {
                message = it
                saveAll()
            },
            label = { Text("Сообщение") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            //interactionSource = interactionSource
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (token.isBlank() || message.isBlank()) {
                    status = "❗ Заполните токен и сообщение"
                    return@Button
                }

                val ids = chatIds.mapNotNull { raw ->
                    val id = raw.trim()
                    if (id.isBlank()) null
                    else if (selectedType == "group" && !id.startsWith("-")) "-$id" else id
                }

                if (ids.isEmpty()) {
                    status = "❗ Укажите хотя бы один Chat ID"
                    return@Button
                }

                status = "📤 Рассылка запущена... Оставьте приложение открытым"
                isSending = true

                coroutineScope.launch {
                    if (sendWithDelay) delay(delaySeconds.toLong() * 1000)

                    val failed = mutableListOf<String>()

                    when (sendMode) {
                        SendMode.ONCE -> {
                            for (chatId in ids) {
                                val ok = botService.sendMessage(token, chatId, message)
                                if (!ok) failed.add(chatId)
                            }
                        }

                        SendMode.MULTIPLE -> {
                            repeat(sendCount.toIntOrNull() ?: 1) {
                                for (chatId in ids) {
                                    val ok = botService.sendMessage(token, chatId, message)
                                    if (!ok) failed.add(chatId)
                                }
                                delay(intervalSeconds.toLong() * 1000)
                            }
                        }

                        SendMode.DURATION -> {
                            val k = durationTotalTime.toIntOrNull() ?: 0
                            when (durationSubMode) {
                                DurationSubMode.TIMES_PER_SECONDS -> {
                                    val n = durationSendCount.toIntOrNull() ?: 1
                                    val l = if (n > 0) k / n else 1
                                    repeat(n) {
                                        for (chatId in ids) {
                                            val ok = botService.sendMessage(token, chatId, message)
                                            if (!ok) failed.add(chatId)
                                        }
                                        delay(l * 1000L)
                                    }
                                }

                                DurationSubMode.FIXED_INTERVAL -> {
                                    val x = durationFixedInterval.toIntOrNull() ?: 1
                                    val count = if (x > 0) k / x else 1
                                    repeat(count) {
                                        for (chatId in ids) {
                                            val ok = botService.sendMessage(token, chatId, message)
                                            if (!ok) failed.add(chatId)
                                        }
                                        delay(x * 1000L)
                                    }
                                }
                            }
                        }
                    }

                    status = if (failed.isEmpty()) {
                        "✅ Все сообщения успешно отправлены"
                    } else {
                        "⚠️ Ошибка в chat_id: ${failed.joinToString()}"
                    }
                    isSending = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isSending
        ) {
            Text(text = if (isSending) "Отправка..." else "Отправить")
        }

        Spacer(Modifier.height(12.dp))

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.error)
        }

        infoDialogText?.let { text ->
            AlertDialog(
                onDismissRequest = { infoDialogText = null },
                confirmButton = {
                    TextButton(onClick = { infoDialogText = null }) {
                        Text("OK")
                    }
                },
                title = { Text("Информация") },
                text = { Text(text) }
            )
        }
    }
}
