package com.lagradost.quicknovel.tts.edge

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object EdgeTTSProtocol {
    private const val EDGE_SPEECH_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    private const val EDGE_API_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    private const val CHROMIUM_MAJOR_VERSION = "143"

    fun generateSecMsGec(): String {
        var ticks = System.currentTimeMillis() / 1000
        ticks += 11644473600L // Windows epoch offset (1601 to 1970)
        ticks -= ticks % 300  // Round down to nearest 5 minutes
        ticks *= 10000000L   // Convert to 100-nanosecond intervals

        val strToHash = "${ticks}${EDGE_API_TOKEN}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(strToHash.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02X".format(it) }
    }

    fun generateMuid(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun buildConnectionUrl(): String {
        val connectId = UUID.randomUUID().toString().replace("-", "").uppercase()
        val secMsGec = generateSecMsGec()
        return "$EDGE_SPEECH_URL?ConnectionId=$connectId&TrustedClientToken=$EDGE_API_TOKEN&Sec-MS-GEC=$secMsGec&Sec-MS-GEC-Version=1-$CHROMIUM_FULL_VERSION"
    }

    fun buildHeaders(): Map<String, String> {
        val muid = generateMuid()
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROMIUM_MAJOR_VERSION}.0.0.0 Safari/537.36 Edg/${CHROMIUM_MAJOR_VERSION}.0.0.0",
            "Origin" to "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache",
            "Cookie" to "muid=$muid;"
        )
    }

    fun buildSpeechConfigFrame(date: String): String {
        val configContent = """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":false,"wordBoundaryEnabled":false},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""
        val headers = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Path" to "speech.config",
            "X-Timestamp" to date
        )
        return buildFrame(headers, configContent)
    }

    fun buildSSMLFrame(text: String, voice: String, lang: String, speed: Float, pitch: Float, date: String): String {
        val connectId = UUID.randomUUID().toString().replace("-", "").uppercase()
        val escapedText = escapeXml(text)
        
        // speed mapping: ratePercent = ((speed - 1.0) * 100).roundToInt()
        val ratePercent = ((speed - 1.0f) * 100).toInt()
        val rateSign = if (ratePercent >= 0) "+" else ""
        val rateStr = "$rateSign${ratePercent}%"

        // pitch mapping: same percent format for simplicity or default
        val pitchPercent = ((pitch - 1.0f) * 100).toInt()
        val pitchSign = if (pitchPercent >= 0) "+" else ""
        val pitchStr = "$pitchSign${pitchPercent}%"

        val ssml = """
            <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xmlns:mstts='https://www.w3.org/2001/mstts' xml:lang='$lang'>
            <voice name='$voice'>
            <prosody rate='$rateStr' pitch='$pitchStr' volume='+0%'>
            $escapedText
            </prosody>
            </voice>
            </speak>
        """.trimIndent()

        val headers = mapOf(
            "Content-Type" to "application/ssml+xml",
            "Path" to "ssml",
            "X-RequestId" to connectId,
            "X-Timestamp" to date
        )
        return buildFrame(headers, ssml)
    }

    private fun buildFrame(headers: Map<String, String>, content: String): String {
        val sb = StringBuilder()
        for ((key, value) in headers) {
            sb.append(key).append(": ").append(value).append("\r\n")
        }
        sb.append("\r\n").append(content)
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun parseTextFrame(message: String): ParsedTextFrame {
        val lines = message.split("\n")
        val headers = mutableMapOf<String, String>()
        var body = ""
        var lineIdx = 0

        for (i in lines.indices) {
            lineIdx = i
            val line = lines[i].trim()
            if (line.isEmpty()) break
            val separatorIndex = line.indexOf(':')
            if (separatorIndex == -1) continue
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            headers[key] = value
        }

        val bodyBuilder = StringBuilder()
        for (i in (lineIdx + 1) until lines.size) {
            bodyBuilder.append(lines[i]).append("\n")
        }
        body = bodyBuilder.toString().trim()

        return ParsedTextFrame(headers, body)
    }
}

data class ParsedTextFrame(
    val headers: Map<String, String>,
    val body: String
)
