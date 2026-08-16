package me.mudkip.moememos.data.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

/**
 * Stores user-configured OpenAI-compatible LLM endpoint, key, and model.
 * Endpoint/model live in DataStore; the API key is encrypted at rest via
 * [SecureTokenStorage] (AndroidKeyStore AES/GCM). A legacy plaintext key in
 * DataStore is migrated on first read.
 */
class AiSettingsStorage(private val context: Context) {

    private val secureTokenStorage = SecureTokenStorage(context)

    private val endpointKey = stringPreferencesKey("api_endpoint")
    private val legacyApiKeyKey = stringPreferencesKey("api_key")
    private val modelKey = stringPreferencesKey("model")

    val settings: Flow<AiSettings> = context.aiDataStore.data.map { prefs ->
        AiSettings(
            endpoint = prefs[endpointKey] ?: "",
            apiKey = secureApiKey(prefs[legacyApiKeyKey]),
            model = prefs[modelKey] ?: DEFAULT_MODEL,
        )
    }

    suspend fun get(): AiSettings = settings.first()

    suspend fun update(endpoint: String, apiKey: String, model: String) {
        context.aiDataStore.edit { prefs ->
            prefs[endpointKey] = endpoint.trim()
            prefs[modelKey] = model.trim()
            prefs.remove(legacyApiKeyKey)
        }
        if (apiKey.isBlank()) {
            secureTokenStorage.removeToken(AI_API_KEY_ENTRY)
        } else {
            secureTokenStorage.saveToken(AI_API_KEY_ENTRY, apiKey.trim())
        }
    }

    private suspend fun secureApiKey(legacyPlainKey: String?): String {
        val stored = secureTokenStorage.getToken(AI_API_KEY_ENTRY)
        if (stored != null) {
            return stored
        }
        if (!legacyPlainKey.isNullOrBlank()) {
            secureTokenStorage.saveToken(AI_API_KEY_ENTRY, legacyPlainKey)
            context.aiDataStore.edit { prefs -> prefs.remove(legacyApiKeyKey) }
            return legacyPlainKey
        }
        return ""
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val AI_API_KEY_ENTRY = "ai.llm.api_key"
    }
}

data class AiSettings(
    val endpoint: String,
    val apiKey: String,
    val model: String,
) {
    val isEnabled: Boolean get() = endpoint.isNotBlank() && apiKey.isNotBlank()
}

/**
 * Calls an OpenAI-compatible /v1/chat/completions endpoint to transform text.
 *
 * Designed for memo authoring assistance: polish, summarize, expand, translate.
 * The response is returned as plain text; callers insert it into the editor.
 */
class LlmService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    suspend fun transform(settings: AiSettings, systemPrompt: String, input: String): String {
        if (!settings.isEnabled) {
            throw IllegalStateException("AI settings not configured")
        }
        if (input.isBlank()) {
            throw IllegalArgumentException("Input text is empty")
        }

        val endpoint = settings.endpoint.trimEnd('/')
        val url = if (endpoint.endsWith("/chat/completions")) endpoint
                  else "${endpoint}/v1/chat/completions"

        val body = JSONObject().apply {
            put("model", settings.model)
            put("temperature", 0.7)
            put("max_tokens", 2048)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", input)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    cont.resumeWith(Result.failure(RuntimeException("Network error: ${e.message}", e)))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        if (!response.isSuccessful) {
                            val errBody = response.body?.string().orEmpty()
                            cont.resumeWith(Result.failure(RuntimeException("HTTP ${response.code}: ${errBody.take(300)}")))
                            return
                        }
                        val raw = response.body?.string().orEmpty()
                        val parsed = JSONObject(raw)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            ?.trim()
                            .orEmpty()
                        if (parsed.isEmpty()) {
                            cont.resumeWith(Result.failure(RuntimeException("Empty AI response")))
                        } else {
                            cont.resumeWith(Result.success(parsed))
                        }
                    } catch (e: Exception) {
                        cont.resumeWith(Result.failure(e))
                    }
                }
            })
        }
    }
}
