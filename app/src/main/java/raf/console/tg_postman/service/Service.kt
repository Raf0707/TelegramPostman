package raf.console.tg_postman.service

import android.content.Context
import android.net.Uri
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

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

    suspend fun sendPhoto(
        context: Context,
        token: String,
        chatId: String,
        imageUri: Uri,
        caption: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.telegram.org/bot$token/sendPhoto")
            val connection = url.openConnection() as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "POST"

            val mimeType = context.contentResolver.getType(imageUri) ?: "application/octet-stream"
            val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val outputStream = DataOutputStream(connection.outputStream)

            // 1. Параметр chat_id
            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
            outputStream.writeBytes("$chatId\r\n")

            // 2. Параметр caption
            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n")
            outputStream.writeBytes("$caption\r\n")

            // 3. Параметр photo (файл)
            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"image.jpg\"\r\n")
            outputStream.writeBytes("Content-Type: $mimeType\r\n\r\n")

            context.contentResolver.openInputStream(imageUri)?.use { input ->
                input.copyTo(outputStream)
            } ?: return@withContext false

            outputStream.writeBytes("\r\n--$boundary--\r\n")
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            val responseBody = connection.inputStream.bufferedReader().readText()
            println("Response ($responseCode): $responseBody")

            responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun sendDocument(
        context: Context,
        token: String,
        chatId: String,
        documentUri: Uri,
        caption: String
    ): Boolean = sendMultipartFile(context, token, chatId, documentUri, caption, "document")

    suspend fun sendVideo(
        context: Context,
        token: String,
        chatId: String,
        videoUri: Uri,
        caption: String
    ): Boolean = sendMultipartFile(context, token, chatId, videoUri, caption, "video")


    suspend fun sendAudio(
        context: Context,
        token: String,
        chatId: String,
        audioUri: Uri,
        caption: String
    ): Boolean = sendMultipartFile(context, token, chatId, audioUri, caption, "audio")

    suspend fun sendContact(token: String, chatId: String, phoneNumber: String, firstName: String): Boolean {
        val url = "https://api.telegram.org/bot$token/sendContact"
        val json = """
        {
            "chat_id": "$chatId",
            "phone_number": "$phoneNumber",
            "first_name": "$firstName"
        }
    """.trimIndent()
        return postJson(url, json)
    }

    suspend fun sendLocation(token: String, chatId: String, latitude: Double, longitude: Double): Boolean {
        val url = "https://api.telegram.org/bot$token/sendLocation"
        val json = """
        {
            "chat_id": "$chatId",
            "latitude": $latitude,
            "longitude": $longitude
        }
    """.trimIndent()
        return postJson(url, json)
    }


    private suspend fun sendMultipartFile(
        context: Context,
        token: String,
        chatId: String,
        fileUri: Uri,
        caption: String,
        type: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.telegram.org/bot$token/send${type.capitalize()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "POST"

            val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
            val boundary = "----Boundary${System.currentTimeMillis()}"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val outputStream = DataOutputStream(connection.outputStream)

            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
            outputStream.writeBytes("$chatId\r\n")

            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n")
            outputStream.writeBytes("$caption\r\n")

            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"$type\"; filename=\"file\"\r\n")
            outputStream.writeBytes("Content-Type: $mimeType\r\n\r\n")

            context.contentResolver.openInputStream(fileUri)?.use {
                it.copyTo(outputStream)
            } ?: return@withContext false

            outputStream.writeBytes("\r\n--$boundary--\r\n")
            outputStream.flush()
            outputStream.close()

            connection.responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun postJson(url: String, jsonBody: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = HttpClient(CIO)
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            val json = JSONObject(response.bodyAsText())
            json.optBoolean("ok", false)
        } catch (e: Exception) {
            e.printStackTrace()
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

    fun String.encodeURLParam(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

}
