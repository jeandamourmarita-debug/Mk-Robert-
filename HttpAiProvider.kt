package com.mkrobot.assistant.ai

import android.content.Context
import com.mkrobot.assistant.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Default [AiProvider]: calls the Anthropic Messages API (or any compatible
 * endpoint the user points it at from Settings) using nothing but
 * [HttpURLConnection] and [org.json] — both built into Android — so no
 * extra networking dependency is pulled into the low-RAM target APK.
 *
 * The API key is read fresh from [SecurePrefs] on every call and is never
 * logged, cached in memory longer than one request, or written into the
 * source/build output.
 */
class HttpAiProvider(private val context: Context) : AiProvider {

    override suspend fun answer(question: String): String = withContext(Dispatchers.IO) {
        val apiKey = SecurePrefs.getApiKey(context)
            ?: throw AiProviderException("Nta API key yashyizwemo. Jya muri Settings uyishyiremo.")

        val endpoint = SecurePrefs.getEndpoint(context)
        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }

            val body = JSONObject().apply {
                put("model", "claude-sonnet-4-6")
                put("max_tokens", 512)
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", question)
                    }
                ))
            }

            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            if (responseCode !in 200..299) {
                throw AiProviderException("AI backend yasubije ikosa ($responseCode): $responseText")
            }

            parseAnswer(responseText)
        } catch (e: AiProviderException) {
            throw e
        } catch (e: Exception) {
            throw AiProviderException("Ntibyashobotse guhuza na interineti / AI.", e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseAnswer(json: String): String {
        val root = JSONObject(json)
        val content = root.optJSONArray("content") ?: return "Sinabonye igisubizo."
        val builder = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") {
                builder.append(block.optString("text"))
            }
        }
        return builder.toString().ifBlank { "Sinabonye igisubizo." }
    }
}
