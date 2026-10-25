package com.codecrafters.leaktrace.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Status code and (size-capped) body of an HTTP response. */
data class HttpResponse(val code: Int, val body: String)

/** Minimal GET client. Sends no cookies or credentials; callers handle every status code. */
fun interface HttpClient {
    /** @throws IOException when the server cannot be reached. */
    suspend fun get(url: String): HttpResponse

    /** Mobile API calls. Bearer credentials are sent only to the configured origin. */
    suspend fun request(method: String, url: String, body: String? = null, bearer: String? = null): HttpResponse =
        if (method == "GET" && body == null && bearer == null) get(url)
        else throw UnsupportedOperationException("This client only supports unauthenticated GET")
}

class UrlConnectionHttpClient(
    private val timeoutMillis: Int = 15_000,
    private val maxBodyBytes: Int = 64 * 1024,
) : HttpClient {
    override suspend fun get(url: String): HttpResponse = request("GET", url)

    override suspend fun request(method: String, url: String, body: String?, bearer: String?): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { input ->
                val bytes = input.readNBytesCompat(maxBodyBytes)
                bytes.toString(Charsets.UTF_8)
            }.orEmpty()
            HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}

// InputStream.readNBytes(int) needs API 33; this keeps minSdk 26.
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val read = read(buffer, total, limit - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}
