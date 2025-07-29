package raf.console.tg_postman.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import raf.console.tg_postman.screens.SendMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "telegram_settings")

data class TelegramSettings(
    val botName: String = "",
    val token: String = "",
    val selectedType: String = "channel",
    val chatIds: List<String> = listOf(""),
    val sendMode: String = SendMode.ONCE.name, // ✅ сохраняем режим полностью
    val interval: Float = 20f,
    val message: String = "",
    val delayMs: Long = 0L,
    val intervalMs: Long = 0L,
    val delayHours: Int = 0,
    val delayMinutes: Int = 0,
    val delaySeconds: Int = 0,
    val intervalHours: Int = 0,
    val intervalMinutes: Int = 0,
    val intervalSeconds: Int = 0
)

object TelegramPrefsKeys {
    val BOT_NAME = stringPreferencesKey("bot_name")
    val TOKEN = stringPreferencesKey("token")
    val SELECTED_TYPE = stringPreferencesKey("selected_type")
    val CHAT_IDS = stringSetPreferencesKey("chat_ids")
    val SEND_MODE = stringPreferencesKey("send_mode") // ✅ новое поле
    val INTERVAL = floatPreferencesKey("interval")
    val MESSAGE = stringPreferencesKey("message")
    val DELAY_MS = longPreferencesKey("delay_ms")
    val INTERVAL_MS = longPreferencesKey("interval_ms")

    val DELAY_HOURS = intPreferencesKey("delay_hours")
    val DELAY_MINUTES = intPreferencesKey("delay_minutes")
    val DELAY_SECONDS = intPreferencesKey("delay_seconds")

    val INTERVAL_HOURS = intPreferencesKey("interval_hours")
    val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
    val INTERVAL_SECONDS = intPreferencesKey("interval_seconds")
}

class TelegramDataStore(private val context: Context) {

    private val dataStore = context.dataStore

    val settingsFlow: Flow<TelegramSettings> = dataStore.data.map { prefs ->
        TelegramSettings(
            botName = prefs[TelegramPrefsKeys.BOT_NAME] ?: "",
            token = prefs[TelegramPrefsKeys.TOKEN] ?: "",
            selectedType = prefs[TelegramPrefsKeys.SELECTED_TYPE] ?: "channel",
            chatIds = prefs[TelegramPrefsKeys.CHAT_IDS]?.toList() ?: listOf(""),
            sendMode = prefs[TelegramPrefsKeys.SEND_MODE] ?: SendMode.ONCE.name, // ✅ читаем
            interval = prefs[TelegramPrefsKeys.INTERVAL] ?: 20f,
            message = prefs[TelegramPrefsKeys.MESSAGE] ?: "",
            delayMs = prefs[TelegramPrefsKeys.DELAY_MS] ?: 0L,
            intervalMs = prefs[TelegramPrefsKeys.INTERVAL_MS] ?: 0L,
            delayHours = prefs[TelegramPrefsKeys.DELAY_HOURS] ?: 0,
            delayMinutes = prefs[TelegramPrefsKeys.DELAY_MINUTES] ?: 0,
            delaySeconds = prefs[TelegramPrefsKeys.DELAY_SECONDS] ?: 0,
            intervalHours = prefs[TelegramPrefsKeys.INTERVAL_HOURS] ?: 0,
            intervalMinutes = prefs[TelegramPrefsKeys.INTERVAL_MINUTES] ?: 0,
            intervalSeconds = prefs[TelegramPrefsKeys.INTERVAL_SECONDS] ?: 0
        )
    }

    suspend fun saveSettings(settings: TelegramSettings) {
        dataStore.edit { prefs ->
            prefs[TelegramPrefsKeys.BOT_NAME] = settings.botName
            prefs[TelegramPrefsKeys.TOKEN] = settings.token
            prefs[TelegramPrefsKeys.SELECTED_TYPE] = settings.selectedType
            prefs[TelegramPrefsKeys.CHAT_IDS] = settings.chatIds.toSet()
            prefs[TelegramPrefsKeys.SEND_MODE] = settings.sendMode // ✅ сохраняем режим
            prefs[TelegramPrefsKeys.INTERVAL] = settings.interval
            prefs[TelegramPrefsKeys.MESSAGE] = settings.message

            prefs[TelegramPrefsKeys.DELAY_MS] = settings.delayMs
            prefs[TelegramPrefsKeys.INTERVAL_MS] = settings.intervalMs

            prefs[TelegramPrefsKeys.DELAY_HOURS] = settings.delayHours
            prefs[TelegramPrefsKeys.DELAY_MINUTES] = settings.delayMinutes
            prefs[TelegramPrefsKeys.DELAY_SECONDS] = settings.delaySeconds

            prefs[TelegramPrefsKeys.INTERVAL_HOURS] = settings.intervalHours
            prefs[TelegramPrefsKeys.INTERVAL_MINUTES] = settings.intervalMinutes
            prefs[TelegramPrefsKeys.INTERVAL_SECONDS] = settings.intervalSeconds
        }
    }

    suspend fun saveTimer(
        context: Context,
        type: String, // "delay" или "interval"
        hours: Int,
        minutes: Int,
        seconds: Int
    ) {
        context.dataStore.edit { prefs ->
            when (type) {
                "delay" -> {
                    prefs[TelegramPrefsKeys.DELAY_HOURS] = hours
                    prefs[TelegramPrefsKeys.DELAY_MINUTES] = minutes
                    prefs[TelegramPrefsKeys.DELAY_SECONDS] = seconds
                    prefs[TelegramPrefsKeys.DELAY_MS] =
                        (hours * 3600 + minutes * 60 + seconds) * 1000L
                }
                "interval" -> {
                    prefs[TelegramPrefsKeys.INTERVAL_HOURS] = hours
                    prefs[TelegramPrefsKeys.INTERVAL_MINUTES] = minutes
                    prefs[TelegramPrefsKeys.INTERVAL_SECONDS] = seconds
                    prefs[TelegramPrefsKeys.INTERVAL_MS] =
                        (hours * 3600 + minutes * 60 + seconds) * 1000L
                }
            }
        }
    }
}
