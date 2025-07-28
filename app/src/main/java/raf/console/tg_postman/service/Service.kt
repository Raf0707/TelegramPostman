package raf.console.tg_postman.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

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

    /*suspend fun sendMediaGroup(
        context: Context,
        token: String,
        chatId: String,
        uris: List<Uri>,
        type: String, // photo, video, document, audio
        caption: String?
    ): Boolean = withContext(Dispatchers.IO) {  // ✅ Переводим на IO поток
        val chunks = uris.chunked(10)
        var allOk = true
        for ((index, chunk) in chunks.withIndex()) {
            val boundary = "----Boundary${System.currentTimeMillis()}"
            val url = URL("https://api.telegram.org/bot$token/sendMediaGroup")
            val connection = url.openConnection() as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val output = DataOutputStream(connection.outputStream)

            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
            output.writeBytes("$chatId\r\n")

            val mediaJson = StringBuilder("[")
            for ((i, uri) in chunk.withIndex()) {
                val attachName = "$type$i"
                mediaJson.append("{\"media\":\"attach://$attachName\",\"type\":\"$type\"")
                if (i == 0 && !caption.isNullOrBlank()) {
                    mediaJson.append(",\"caption\":\"${caption.replace("\"", "\\\"")}\"")
                }
                mediaJson.append("}")
                if (i < chunk.size - 1) mediaJson.append(",")

                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"$attachName\"; filename=\"file$i\"\r\n")
                output.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(output) }
                output.writeBytes("\r\n")
            }
            mediaJson.append("]")

            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"media\"\r\n\r\n")
            output.writeBytes(mediaJson.toString() + "\r\n")

            output.writeBytes("--$boundary--\r\n")
            output.flush()
            output.close()

            val ok = connection.responseCode == 200
            allOk = allOk && ok
            if (!ok) println("❌ Ошибка при отправке mediaGroup ($index): ${connection.responseCode}")
        }
        allOk
    }*/

    /*suspend fun sendMediaGroup(
        context: Context,
        token: String,
        chatId: String,
        uris: List<Uri>,
        type: String, // photo, video, document, audio
        caption: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val chunks = uris.chunked(10)
        var allOk = true
        for ((index, chunk) in chunks.withIndex()) {
            val boundary = "----Boundary${System.currentTimeMillis()}"
            val url = URL("https://api.telegram.org/bot$token/sendMediaGroup")
            val connection = url.openConnection() as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val output = DataOutputStream(connection.outputStream)

            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
            output.writeBytes("$chatId\r\n")

            val mediaJson = StringBuilder("[")
            for ((i, uri) in chunk.withIndex()) {
                val attachName = "$type$i"
                mediaJson.append("{\"media\":\"attach://$attachName\",\"type\":\"$type\"")
                if (i == 0 && !caption.isNullOrBlank()) {
                    mediaJson.append(",\"caption\":\"${caption.replace("\"", "\\\"")}\"")
                }
                mediaJson.append("}")
                if (i < chunk.size - 1) mediaJson.append(",")

                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"$attachName\"; filename=\"file$i\"\r\n")
                output.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(output) }
                output.writeBytes("\r\n")
            }
            mediaJson.append("]")

            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"media\"\r\n\r\n")
            output.writeBytes(mediaJson.toString() + "\r\n")

            output.writeBytes("--$boundary--\r\n")
            output.flush()
            output.close()

            val ok = connection.responseCode == 200
            allOk = allOk && ok
            if (!ok) println("❌ Ошибка при отправке mediaGroup ($index): ${connection.responseCode}")
        }
        allOk
    }*/

    suspend fun compressVideoStandard(context: Context, inputUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")

        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(inputUri, "r")?.use { fd ->
                extractor.setDataSource(fd.fileDescriptor)
            }

            val trackIndex = selectTrack(extractor)
            if (trackIndex < 0) return@withContext null

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)

            // Настройка кодека
            val width = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val newWidth = if (width > 1280) 1280 else width
            val newHeight = if (height > 720) 720 else height

            val outputFormat = MediaFormat.createVideoFormat("video/avc", newWidth, newHeight).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000) // 2 Mbps
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }

            val encoder = MediaCodec.createEncoderByType("video/avc")
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            encoder.start()
            val bufferInfo = MediaCodec.BufferInfo()
            val inputBuffers = encoder.inputBuffers
            val outputBuffers = encoder.outputBuffers
            var muxerTrackIndex = -1
            var isEOS = false

            while (!isEOS) {
                val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = inputBuffers[inputBufferIndex]
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)

                    if (sampleSize < 0) {
                        encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        encoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }

                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encoder.outputBuffers
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = encoder.outputFormat
                    muxerTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                } else if (outputBufferIndex >= 0) {
                    val encodedData = outputBuffers[outputBufferIndex]
                    if (bufferInfo.size > 0 && muxerTrackIndex != -1) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                }
            }

            muxer.stop()
            muxer.release()
            encoder.stop()
            encoder.release()
            extractor.release()

            return@withContext Uri.fromFile(outputFile)

        } catch (e: Exception) {
            Log.e("VideoCompressor", "Ошибка сжатия: ${e.message}", e)
            extractor.release()
            return@withContext null
        }
    }

    private fun selectTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                return i
            }
        }
        return -1
    }

    suspend fun sendMediaGroup(
        context: Context,
        token: String,
        chatId: String,
        uris: List<Uri>,
        mediaType: String,
        caption: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = createHttpClient()//OkHttpClient()

            // === Функция для потоковой отправки одного файла ===
            suspend fun sendSingleFile(uri: Uri, paramName: String, url: String): Boolean {
                val fileName = uri.lastPathSegment ?: "file"

                val fileBody = object : RequestBody() {
                    override fun contentType(): MediaType? = when (mediaType) {
                        "photo" -> "image/jpeg".toMediaType()
                        "video" -> "video/mp4".toMediaType()
                        "audio" -> "audio/mpeg".toMediaType()
                        "document" -> "application/octet-stream".toMediaType()
                        else -> "application/octet-stream".toMediaType()
                    }

                    override fun writeTo(sink: BufferedSink) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                sink.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }

                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart(paramName, fileName, fileBody)
                    .apply { if (!caption.isNullOrEmpty()) addFormDataPart("caption", caption) }
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(multipart)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("TelegramBotService", "Ошибка отправки файла: ${response.body?.string()}")
                        return@use false
                    } else {

                    }
                }
                return true
            }

            // === Для видео и документов — отправляем каждый файл отдельно ===
            if (mediaType == "video" || mediaType == "document") {
                for (uri in uris) {
                    val url = when (mediaType) {
                        "video" -> "https://api.telegram.org/bot$token/sendVideo"
                        "document" -> "https://api.telegram.org/bot$token/sendDocument"
                        else -> "https://api.telegram.org/bot$token/sendDocument"
                    }
                    if (!sendSingleFile(uri, mediaType, url)) return@withContext false
                }
                return@withContext true
            }

            // === Для фото и аудио можно отправлять пачками (до 10 штук) ===
            val chunks = uris.chunked(10)
            for (batch in chunks) {
                val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                multipartBuilder.addFormDataPart("chat_id", chatId)

                val mediaJson = JSONArray()

                batch.forEachIndexed { index, uri ->
                    val fileName = uri.lastPathSegment ?: "file_$index"

                    val requestBody = object : RequestBody() {
                        override fun contentType(): MediaType? = when (mediaType) {
                            "photo" -> "image/jpeg".toMediaType()
                            "audio" -> "audio/mpeg".toMediaType()
                            else -> "application/octet-stream".toMediaType()
                        }

                        override fun writeTo(sink: BufferedSink) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    sink.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                    }

                    multipartBuilder.addFormDataPart("media$index", fileName, requestBody)

                    val mediaObject = JSONObject().apply {
                        put("type", mediaType)
                        put("media", "attach://media$index")
                        if (!caption.isNullOrEmpty() && index == 0) put("caption", caption)
                    }
                    mediaJson.put(mediaObject)
                }

                multipartBuilder.addFormDataPart("media", mediaJson.toString())

                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$token/sendMediaGroup")
                    .post(multipartBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("TelegramBotService", "Ошибка отправки группы: ${response.body?.string()}")
                        return@withContext false
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e("TelegramBotService", "sendMediaGroup error: ${e.message}", e)
            false
        }
    }





    suspend fun sendContactsBatch(
        token: String,
        chatId: String,
        contacts: List<Pair<String, String>> // (name, phone)
    ): Boolean = withContext(Dispatchers.IO) {
        var allOk = true
        for ((name, phone) in contacts) {
            val ok = sendContact(token, chatId, phone, name)
            allOk = allOk && ok
            delay(200) // небольшой интервал, чтобы избежать flood
        }
        allOk
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

    fun String.encodeURLParam(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    fun <T> List<T>.chunkedByTelegramLimit(): List<List<T>> = this.chunked(10)

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)   // соединение
            .writeTimeout(10, TimeUnit.MINUTES)     // запись (загрузка)
            .readTimeout(10, TimeUnit.MINUTES)      // чтение ответа
            .build()
    }



}
