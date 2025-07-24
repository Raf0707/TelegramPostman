package raf.console.tg_postman.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "telegram_settings")

data class TelegramSettings(
    val botName: String = "",
    val token: String = "",
    val selectedType: String = "channel",
    val chatIds: List<String> = listOf(""),
    val sendOnce: Boolean = true,
    val interval: Float = 20f,
    val message: String = ""
)


object TelegramPrefsKeys {
    val BOT_NAME = stringPreferencesKey("bot_name")
    val TOKEN = stringPreferencesKey("token")
    val SELECTED_TYPE = stringPreferencesKey("selected_type")
    val CHAT_IDS = stringSetPreferencesKey("chat_ids")
    val SEND_ONCE = booleanPreferencesKey("send_once")
    val INTERVAL = floatPreferencesKey("interval")
    val MESSAGE = stringPreferencesKey("message")
}

class TelegramDataStore(private val context: Context) {

    private val dataStore = context.dataStore

    val settingsFlow: Flow<TelegramSettings> = dataStore.data.map { prefs ->
        TelegramSettings(
            botName = prefs[TelegramPrefsKeys.BOT_NAME] ?: "",
            token = prefs[TelegramPrefsKeys.TOKEN] ?: "",
            selectedType = prefs[TelegramPrefsKeys.SELECTED_TYPE] ?: "channel",
            chatIds = prefs[TelegramPrefsKeys.CHAT_IDS]?.toList() ?: listOf(""),
            sendOnce = prefs[TelegramPrefsKeys.SEND_ONCE] ?: true,
            interval = prefs[TelegramPrefsKeys.INTERVAL] ?: 20f,
            message = prefs[TelegramPrefsKeys.MESSAGE] ?: ""
        )
    }

    suspend fun saveSettings(settings: TelegramSettings) {
        dataStore.edit { prefs ->
            prefs[TelegramPrefsKeys.BOT_NAME] = settings.botName
            prefs[TelegramPrefsKeys.TOKEN] = settings.token
            prefs[TelegramPrefsKeys.SELECTED_TYPE] = settings.selectedType
            prefs[TelegramPrefsKeys.CHAT_IDS] = settings.chatIds.toSet()
            prefs[TelegramPrefsKeys.SEND_ONCE] = settings.sendOnce
            prefs[TelegramPrefsKeys.INTERVAL] = settings.interval
            prefs[TelegramPrefsKeys.MESSAGE] = settings.message
        }
    }

}
