package raf.console.tg_postman.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

@SuppressLint("Recycle")
fun compressVideoStandard1(context: Context, inputUri: Uri): Uri? {
    return try {
        val inputDescriptor = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return null
        val extractor = MediaExtractor()
        extractor.setDataSource(inputDescriptor.fileDescriptor)

        val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIndex = -1
        var audioTrackIndex = -1

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)

            if (mime != null && mime.startsWith("video/")) {
                // Уменьшаем битрейт и размер
                format.setInteger(MediaFormat.KEY_BIT_RATE, 1_000_000) // 1 Mbps
                if (format.containsKey(MediaFormat.KEY_WIDTH) && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                    val width = format.getInteger(MediaFormat.KEY_WIDTH)
                    val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    format.setInteger(MediaFormat.KEY_WIDTH, width / 2)
                    format.setInteger(MediaFormat.KEY_HEIGHT, height / 2)
                }
                videoTrackIndex = muxer.addTrack(format)
            } else if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = muxer.addTrack(format)
            }
        }

        muxer.start()

        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        extractor.selectTrack(0)
        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break

            bufferInfo.presentationTimeUs = extractor.sampleTime
            @SuppressLint("WrongConstant")
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }

        if (audioTrackIndex >= 0) {
            extractor.selectTrack(1)
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = extractor.sampleTime
                @SuppressLint("WrongConstant")
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
        }

        muxer.stop()
        muxer.release()
        extractor.release()
        inputDescriptor.close()

        Uri.fromFile(outputFile)
    } catch (e: Exception) {
        Log.e("VideoCompressor", "Ошибка сжатия видео: ${e.message}", e)
        null
    }
}

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