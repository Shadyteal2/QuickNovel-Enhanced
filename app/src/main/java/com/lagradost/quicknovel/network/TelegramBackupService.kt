package com.lagradost.quicknovel.network

import android.content.Context
import android.net.Uri
import com.lagradost.nicehttp.Requests
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.safefile.SafeFile
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

interface TelegramBackupService {
    suspend fun uploadDocument(
        context: Context,
        botToken: String,
        chatId: String,
        documentFile: SafeFile,
        caption: String,
        thumbBytes: ByteArray? = null
    ): Resource<Long>
}

class TelegramBackupServiceImpl(
    private val client: Requests = Requests(
        OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    )
) : TelegramBackupService {

    override suspend fun uploadDocument(
        context: Context,
        botToken: String,
        chatId: String,
        documentFile: SafeFile,
        caption: String,
        thumbBytes: ByteArray?
    ): Resource<Long> {
        val documentLength = documentFile.length() ?: 0L
        if (documentLength > 50 * 1024 * 1024) {
            return Resource.Failure(
                null,
                "File ${documentFile.name()} exceeds Telegram Bot API upload limit of 50 MB (Size: ${documentLength / (1024 * 1024)} MB)"
            )
        }

        val url = "https://api.telegram.org/bot$botToken/sendDocument"

        return safeApiCall {
            var attempt = 0
            val maxAttempts = 3
            var lastResponseText = ""
            var lastResponseCode = 0

            while (attempt < maxAttempts) {
                attempt++
                val multipartBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("parse_mode", "HTML")
                    .addFormDataPart("caption", caption)

                // Document request body that opens its own input stream for each attempt
                val documentBody = object : RequestBody() {
                    override fun contentType() = "application/epub+zip".toMediaType()
                    override fun contentLength() = documentLength
                    override fun writeTo(sink: BufferedSink) {
                        val uri = documentFile.uri() ?: throw java.io.IOException("File URI is null")
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw java.io.IOException("Failed to open input stream for ${documentFile.name()}")
                        input.use { inStream ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (inStream.read(buffer).also { read = it } != -1) {
                                sink.write(buffer, 0, read)
                            }
                        }
                    }
                }
                multipartBuilder.addFormDataPart("document", documentFile.name() ?: "novel.epub", documentBody)

                // Add cover image thumbnail if present (bound directly as 'thumb' and 'thumbnail' for maximum compatibility)
                if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                    val thumbBody = RequestBody.create("image/jpeg".toMediaType(), thumbBytes)
                    multipartBuilder.addFormDataPart("thumb", "thumb.jpg", thumbBody)
                    multipartBuilder.addFormDataPart("thumbnail", "thumb.jpg", thumbBody)
                }

                val response = client.post(
                    url = url,
                    requestBody = multipartBuilder.build()
                )

                lastResponseCode = response.code
                lastResponseText = response.text ?: ""

                if (lastResponseCode == 429) {
                    var retryAfterMs = 5000L
                    try {
                        val retryHeader = response.headers["retry-after"]?.toLongOrNull()
                        if (retryHeader != null) {
                            retryAfterMs = retryHeader * 1000L
                        } else {
                            val root = com.lagradost.quicknovel.DataStore.mapper.readTree(lastResponseText)
                            val retryAfterSec = root.path("parameters").path("retry_after").asLong()
                            if (retryAfterSec > 0) {
                                retryAfterMs = retryAfterSec * 1000L
                            }
                        }
                    } catch (t: Throwable) {
                        // fallback to default retry delay
                    }
                    delay(retryAfterMs)
                    continue
                }

                if (!response.isSuccessful) {
                    throw Exception("Telegram API returned HTTP $lastResponseCode: $lastResponseText")
                }

                val root = com.lagradost.quicknovel.DataStore.mapper.readTree(lastResponseText)
                val ok = root.path("ok").asBoolean()
                if (!ok) {
                    val desc = root.path("description").asText()
                    throw Exception("Telegram API Error: $desc")
                }
                val messageId = root.path("result").path("message_id").asLong()
                return@safeApiCall messageId
            }

            throw Exception("Failed to upload document after $maxAttempts attempts (HTTP $lastResponseCode: $lastResponseText)")
        }
    }
}
