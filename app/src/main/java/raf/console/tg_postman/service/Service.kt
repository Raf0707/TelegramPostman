package raf.console.tg_postman.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TelegramBotService {

    private val client = HttpClient(CIO)

    /**
     * Отправляет сообщение в указанный chat_id через Telegram Bot API
     */
    suspend fun sendMessage(token: String, chatId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val payload = buildJson(chatId, text)

            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            val body = response.bodyAsText()
            val json = JSONObject(body)

            if (!json.optBoolean("ok", false)) {
                println("❌ Ошибка Telegram API: ${json.optString("description")}")
            }

            json.optBoolean("ok", false)
        } catch (e: Exception) {
            println("❌ Ошибка отправки в chatId=$chatId: ${e.message}")
            false
        }
    }

    private fun buildJson(chatId: String, text: String): String {
        val escapedText = text.replace("\"", "\\\"")
        return """{
            "chat_id": "$chatId",
            "text": "$escapedText"
        }""".trimIndent()
    }

    fun close() {
        client.close()
    }
}
