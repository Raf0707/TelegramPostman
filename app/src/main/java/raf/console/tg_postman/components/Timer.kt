package raf.console.tg_postman.components

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import raf.console.tg_postman.data.TelegramDataStore
import raf.console.tg_postman.data.TelegramSettings

@Composable
fun TimePickerField(
    label: String,
    type: String, // "delay" или "interval"
    onTimeChanged: (Long) -> Unit
) {
    val context = LocalContext.current
    val dataStore = remember { TelegramDataStore(context) }
    val coroutineScope = rememberCoroutineScope()

    val settings by dataStore.settingsFlow.collectAsState(initial = TelegramSettings())

    var hoursText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    var secondsText by remember { mutableStateOf("") }

    // Загружаем сохранённые значения при открытии (но не вызываем onTimeChanged)
    LaunchedEffect(settings) {
        if (type == "delay") {
            hoursText = settings.delayHours.takeIf { it > 0 }?.toString() ?: ""
            minutesText = settings.delayMinutes.takeIf { it > 0 }?.toString() ?: ""
            secondsText = settings.delaySeconds.takeIf { it > 0 }?.toString() ?: ""
        } else {
            hoursText = settings.intervalHours.takeIf { it > 0 }?.toString() ?: ""
            minutesText = settings.intervalMinutes.takeIf { it > 0 }?.toString() ?: ""
            secondsText = settings.intervalSeconds.takeIf { it > 0 }?.toString() ?: ""
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hoursText,
            onValueChange = {
                hoursText = it.filter { char -> char.isDigit() }.take(2)
                handleTimeChange(context, type, hoursText, minutesText, secondsText, coroutineScope, dataStore, onTimeChanged)
            },
            label = { Text("Часы") },
            placeholder = { Text("0") },
            modifier = Modifier.width(80.dp)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = minutesText,
            onValueChange = {
                minutesText = it.filter { char -> char.isDigit() }.take(2)
                handleTimeChange(context, type, hoursText, minutesText, secondsText, coroutineScope, dataStore, onTimeChanged)
            },
            label = { Text("Мин") },
            placeholder = { Text("0") },
            modifier = Modifier.width(80.dp)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = secondsText,
            onValueChange = {
                secondsText = it.filter { char -> char.isDigit() }.take(2)
                handleTimeChange(context, type, hoursText, minutesText, secondsText, coroutineScope, dataStore, onTimeChanged)
            },
            label = { Text("Сек") },
            placeholder = { Text("0") },
            modifier = Modifier.width(80.dp)
        )
    }
}

private fun handleTimeChange(
    context: Context,
    type: String,
    hoursText: String,
    minutesText: String,
    secondsText: String,
    coroutineScope: CoroutineScope,
    dataStore: TelegramDataStore,
    onTimeChanged: (Long) -> Unit
) {
    val hours = hoursText.toIntOrNull() ?: 0
    val minutes = minutesText.toIntOrNull() ?: 0
    val seconds = secondsText.toIntOrNull() ?: 0

    val totalMs = (hours * 3600 + minutes * 60 + seconds) * 1000L

    coroutineScope.launch {
        dataStore.saveTimer(context, type, hours, minutes, seconds)
    }

    onTimeChanged(totalMs)
}
