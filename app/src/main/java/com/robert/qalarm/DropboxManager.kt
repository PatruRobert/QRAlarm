package com.robert.qalarm

import android.content.Context
import android.content.SharedPreferences
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.http.OkHttp3Requestor
import com.dropbox.core.v2.DbxClientV2
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import com.dropbox.core.v2.files.FileMetadata
import java.io.File
import java.io.FileOutputStream

object DropboxManager {

    private const val PREFS = "dropbox"
    private const val KEY_ACCESS = "access_token"

    private val config by lazy {
        // allEnabledTlsVersions + allEnabledCipherSuites lets the platform
        // negotiate freely instead of OkHttp's restrictive MODERN_TLS list,
        // which gets rejected by Dropbox's CDN on some Android devices.
        val spec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .allEnabledTlsVersions()
            .allEnabledCipherSuites()
            .build()
        val okHttpClient = OkHttpClient.Builder()
            .connectionSpecs(listOf(spec))
            .build()
        DbxRequestConfig.newBuilder("QRAlarm")
            .withHttpRequestor(OkHttp3Requestor(okHttpClient))
            .build()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConnected(context: Context): Boolean {
        val connected = prefs(context).contains(KEY_ACCESS)
        android.util.Log.d("DropboxManager", "isConnected: $connected (key='$KEY_ACCESS')")
        return connected
    }

    fun setToken(context: Context, token: String) {
        val trimmed = token.trim()
        prefs(context).edit().putString(KEY_ACCESS, trimmed).commit()
        android.util.Log.d("DropboxManager", "setToken: saved token (first 10 chars: ${trimmed.take(10)}…)")
    }

    fun disconnect(context: Context) {
        prefs(context).edit().remove(KEY_ACCESS).commit()
        android.util.Log.d("DropboxManager", "disconnect: cleared token")
    }

    fun getClient(context: Context): DbxClientV2? {
        val token = prefs(context).getString(KEY_ACCESS, null)
        android.util.Log.d("DropboxManager", "getClient: token retrieved = ${token?.take(10)}…, is null: ${token == null}")
        return if (token != null) DbxClientV2(config, token) else null
    }

    fun getCacheDir(context: Context): File =
        File(context.getExternalFilesDir(null), "dropbox_cache").also { it.mkdirs() }

    fun testToken(context: Context): String? {
        val client = getClient(context) ?: return "Not connected"
        return try {
            val account = client.users().currentAccount
            "OK: ${account.name.displayName}"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    // Downloads all MP3s from /Alarms in Dropbox to the local cache.
    // Returns (localPaths, errorMessage). errorMessage is null on success.
    fun syncFiles(context: Context): Pair<List<String>, String?> {
        val client = getClient(context)
            ?: return Pair(emptyList(), "Not connected — paste your access token first")
        val cacheDir = getCacheDir(context)
        val paths = mutableListOf<String>()
        return try {
            android.util.Log.d("DropboxManager", "Listing /Alarms …")
            val result = client.files().listFolder("/Alarms")
            android.util.Log.d("DropboxManager", "Found ${result.entries.size} entries")
            val dropboxNames = mutableSetOf<String>()
            for (entry in result.entries) {
                android.util.Log.d("DropboxManager", "Entry: ${entry.name} (${entry.javaClass.simpleName})")
                if (entry is FileMetadata && entry.name.lowercase().endsWith(".mp3")) {
                    dropboxNames.add(entry.name)
                    val localFile = File(cacheDir, entry.name)
                    if (!localFile.exists() || localFile.length() != entry.size) {
                        android.util.Log.d("DropboxManager", "Downloading ${entry.name} …")
                        FileOutputStream(localFile).use { out ->
                            client.files().downloadBuilder(entry.pathLower).download(out)
                        }
                    }
                    paths.add(localFile.absolutePath)
                }
            }
            cacheDir.listFiles()?.forEach { f -> if (f.name !in dropboxNames) f.delete() }
            android.util.Log.d("DropboxManager", "Sync done — ${paths.size} MP3s")
            Pair(paths, null)
        } catch (e: InvalidAccessTokenException) {
            disconnect(context)
            android.util.Log.e("DropboxManager", "Token expired")
            Pair(emptyList(), "TOKEN_EXPIRED")
        } catch (e: Exception) {
            android.util.Log.e("DropboxManager", "syncFiles failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Pair(emptyList(), "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
