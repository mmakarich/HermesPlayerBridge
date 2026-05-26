package com.hermes.playerbridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HTTP client that posts Health Connect data to the Hermes webhook.
 * Uses HMAC-SHA256 for request authentication.
 */
class HermesApiClient(private val context: Context) {

    companion object {
        private const val TAG = "HermesApi"
        private const val TIMEOUT_SEC = 30L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    data class SyncResult(
        val success: Boolean,
        val processed: Int = 0,
        val error: String = ""
    )

    /** POST a batch of health records to the Hermes webhook. */
    suspend fun postBatch(
        webhookUrl: String,
        secret: String,
        deviceId: String,
        records: List<JSONObject>,
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val batch = JSONObject().apply {
                put("device_id", deviceId)
                put("timestamp", System.currentTimeMillis() / 1000)
                put("records", JSONArray(records))
            }

            val bodyBytes = batch.toString().toByteArray(Charsets.UTF_8)
            val signature = computeHmacSha256(bodyBytes, secret)

            val requestBody = bodyBytes.toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
                .header("X-Hub-Signature-256", signature)
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val processed = json.optInt("processed", 0)
                Log.i(TAG, "Sync OK: $processed records, HTTP ${response.code}")
                SyncResult(success = true, processed = processed)
            } else {
                Log.w(TAG, "Sync HTTP ${response.code}: $responseBody")
                SyncResult(success = false, error = "HTTP ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            SyncResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    /** Compute HMAC-SHA256 hex signature. */
    private fun computeHmacSha256(data: ByteArray, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val digest = mac.doFinal(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
