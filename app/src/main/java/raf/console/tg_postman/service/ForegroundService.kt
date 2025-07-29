package raf.console.tg_postman.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import raf.console.tg_postman.R
import raf.console.tg_postman.screens.DurationSubMode
import raf.console.tg_postman.screens.SendMode
import raf.console.tg_postman.screens.TelegramMessageType
import raf.console.tg_postman.utils.compressVideoStandard

class TelegramForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var contactName: String? = null
    private var contactPhone: String? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra("token") ?: ""
        val chatIds = intent?.getStringArrayListExtra("chatIds") ?: arrayListOf()
        val message = intent?.getStringExtra("message") ?: ""
        val sendMode = intent?.getStringExtra("sendMode") ?: SendMode.ONCE.name
        val delayMs = intent?.getLongExtra("delayMs", 0L) ?: 0L
        val intervalMs = intent?.getLongExtra("intervalMs", 0L) ?: 0L
        val repeatCount = intent?.getIntExtra("repeatCount", 1) ?: 1

        val messageType = TelegramMessageType.valueOf(intent?.getStringExtra("messageType") ?: "TEXT")
        val multiMediaUris = intent?.getStringArrayListExtra("multiMediaUris")?.map { Uri.parse(it) } ?: emptyList()
        val selectedDocs = intent?.getStringArrayListExtra("selectedDocs")?.map { Uri.parse(it) } ?: emptyList()
        val selectedVideos = intent?.getStringArrayListExtra("selectedVideos")?.map { Uri.parse(it) } ?: emptyList()
        val selectedAudios = intent?.getStringArrayListExtra("selectedAudios")?.map { Uri.parse(it) } ?: emptyList()
        val mediaUri = intent?.getStringExtra("mediaUri")?.let { Uri.parse(it) }
        val latitude = intent?.getDoubleExtra("latitude", 0.0)
        val longitude = intent?.getDoubleExtra("longitude", 0.0)

        val durationSubMode = intent?.getStringExtra("durationSubMode")?.let { DurationSubMode.valueOf(it) }
        val durationTotalTime = intent?.getIntExtra("durationTotalTime", 60) ?: 60
        val durationSendCount = intent?.getIntExtra("durationSendCount", 3) ?: 3
        val durationFixedInterval = intent?.getIntExtra("durationFixedInterval", 10) ?: 10

        contactName = intent?.getStringExtra("contactName")
        contactPhone = intent?.getStringExtra("contactPhone")

        startForegroundServiceNotification()

        // ✅ Правильный расчет общего количества сообщений
        val totalMessages = when (sendMode) {
            SendMode.ONCE.name -> chatIds.size
            SendMode.MULTIPLE.name -> chatIds.size * repeatCount
            SendMode.DURATION.name -> {
                val durationCount = if (durationSubMode == DurationSubMode.TIMES_PER_SECONDS) durationSendCount
                else durationTotalTime / durationFixedInterval
                chatIds.size * durationCount
            }
            else -> chatIds.size
        }

        serviceScope.launch {
            if (delayMs > 0) delay(delayMs)

            val botService = TelegramBotService()
            val failed = mutableListOf<String>()
            var sentCount = 0

            when (sendMode) {
                SendMode.ONCE.name -> {
                    sentCount += sendMessagesOnce(
                        botService, token, chatIds, message, messageType,
                        multiMediaUris, selectedDocs, selectedVideos, selectedAudios,
                        mediaUri, latitude, longitude, failed, totalMessages, sentCount
                    )
                }

                SendMode.MULTIPLE.name -> {
                    repeat(repeatCount) { attempt ->
                        sentCount += sendMessagesOnce(
                            botService, token, chatIds, message, messageType,
                            multiMediaUris, selectedDocs, selectedVideos, selectedAudios,
                            mediaUri, latitude, longitude, failed, totalMessages, sentCount
                        )
                        if (attempt != repeatCount - 1 && intervalMs > 0) delay(intervalMs)
                    }
                }

                SendMode.DURATION.name -> {
                    val iterations = if (durationSubMode == DurationSubMode.TIMES_PER_SECONDS) durationSendCount
                    else durationTotalTime / durationFixedInterval

                    repeat(iterations) { attempt ->
                        sentCount += sendMessagesOnce(
                            botService, token, chatIds, message, messageType,
                            multiMediaUris, selectedDocs, selectedVideos, selectedAudios,
                            mediaUri, latitude, longitude, failed, totalMessages, sentCount
                        )
                        if (attempt != iterations - 1 && durationFixedInterval > 0) delay(durationFixedInterval * 1000L)
                    }
                }
            }

            val success = failed.isEmpty()
            val intentResult = Intent("raf.console.tg_postman.ACTION_SEND_COMPLETE").apply {
                putExtra("success", success)
            }
            sendBroadcast(intentResult)

            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun sendMessagesOnce(
        botService: TelegramBotService,
        token: String,
        chatIds: List<String>,
        message: String,
        messageType: TelegramMessageType,
        multiMediaUris: List<Uri>,
        selectedDocs: List<Uri>,
        selectedVideos: List<Uri>,
        selectedAudios: List<Uri>,
        mediaUri: Uri?,
        latitude: Double?,
        longitude: Double?,
        failed: MutableList<String>,
        totalMessages: Int,
        startCount: Int
    ): Int {

        var localCount = 0

        chatIds.forEach { chatId ->
            val ok = when (messageType) {
                TelegramMessageType.TEXT -> botService.sendMessage(token, chatId, message)
                TelegramMessageType.PHOTO -> {
                    if (multiMediaUris.isNotEmpty()) {
                        botService.sendMediaGroup(this@TelegramForegroundService, token, chatId, multiMediaUris, "photo", message)
                    } else if (mediaUri != null) {
                        botService.sendPhoto(this@TelegramForegroundService, token, chatId, mediaUri, message)
                    } else false
                }
                TelegramMessageType.DOCUMENT -> {
                    if (selectedDocs.isNotEmpty()) {
                        botService.sendMediaGroup(this@TelegramForegroundService, token, chatId, selectedDocs, "document", message)
                    } else if (mediaUri != null) {
                        botService.sendDocument(this@TelegramForegroundService, token, chatId, mediaUri, message)
                    } else false
                }
                TelegramMessageType.VIDEO -> {
                    if (selectedVideos.isNotEmpty()) {
                        val compressedUris = selectedVideos.map { uri ->
                            val size = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
                            if (size > 45 * 1024 * 1024) compressVideoStandard(this@TelegramForegroundService, uri) ?: uri else uri
                        }
                        botService.sendMediaGroup(this@TelegramForegroundService, token, chatId, compressedUris, "video", message)
                    } else if (mediaUri != null) {
                        val size = contentResolver.openFileDescriptor(mediaUri, "r")?.statSize ?: 0L
                        val finalUri = if (size > 45 * 1024 * 1024) compressVideoStandard(this@TelegramForegroundService, mediaUri) ?: mediaUri else mediaUri
                        botService.sendVideo(this@TelegramForegroundService, token, chatId, finalUri, message)
                    } else false
                }
                TelegramMessageType.AUDIO -> {
                    if (selectedAudios.isNotEmpty()) {
                        botService.sendMediaGroup(this@TelegramForegroundService, token, chatId, selectedAudios, "audio", message)
                    } else if (mediaUri != null) {
                        botService.sendAudio(this@TelegramForegroundService, token, chatId, mediaUri, message)
                    } else false
                }
                TelegramMessageType.CONTACT -> {
                    if (!contactName.isNullOrBlank() && !contactPhone.isNullOrBlank()) {
                        botService.sendContact(token, chatId, contactPhone!!, contactName!!)
                    } else false
                }
                TelegramMessageType.LOCATION -> {
                    if (latitude != null && longitude != null) {
                        botService.sendLocation(token, chatId, latitude, longitude)
                    } else false
                }
            }

            if (!ok) failed.add(chatId)

            localCount++
            val progressIntent = Intent("raf.console.tg_postman.ACTION_SEND_PROGRESS").apply {
                putExtra("sent", startCount + localCount)
                putExtra("total", totalMessages)
                setPackage(packageName)
            }
            sendBroadcast(progressIntent)
        }

        return localCount
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundServiceNotification() {
        val channelId = "telegram_postman_channel"
        val channel = NotificationChannel(channelId, "Telegram Postman", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Рассылка сообщений")
            .setContentText("Сообщения отправляются в фоне")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
