package com.example.birthdaywidget.network

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ContactPhotoCache(private val context: Context) {

    private val httpClient = OkHttpClient()

    suspend fun cachePhoto(
        resourceName: String,
        url: String,
        credential: GoogleAccountCredential,
        accessToken: String?
    ): String? = withContext(Dispatchers.IO) {
        val cacheDir = photoDirectory()
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val file = File(cacheDir, "${hash(resourceName)}.jpg")
        if (file.exists()) {
            return@withContext file.absolutePath
        }
        if (accessToken.isNullOrBlank()) {
            return@withContext null
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@runCatching null
                }
                val body = response.body ?: return@runCatching null
                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                file.absolutePath
            }
        }.getOrNull()
    }

    suspend fun trimTo(keys: Set<String>) = withContext(Dispatchers.IO) {
        val dir = photoDirectory()
        if (!dir.exists()) return@withContext
        dir.listFiles()?.forEach { file ->
            val name = file.nameWithoutExtension
            if (!keys.contains(name)) {
                file.delete()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val dir = photoDirectory()
        if (!dir.exists()) return@withContext
        dir.listFiles()?.forEach { file -> file.delete() }
    }

    private fun photoDirectory(): File = File(context.filesDir, "widget_photos")

    fun keyFor(resourceName: String): String = hash(resourceName)

    private fun hash(resourceName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(resourceName.toByteArray()).joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
        return hash
    }
}
