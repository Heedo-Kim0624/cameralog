package com.example.camerawatch

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SQLiteDatabase
import java.util.concurrent.atomic.AtomicReference

class CameraWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SingletonHolder.initialize(this)
    }
}

object SingletonHolder {
    private val contextRef = AtomicReference<Context>()

    fun initialize(context: Context) {
        if (contextRef.get() != null) return
        context.applicationContext?.let {
            SQLiteDatabase.loadLibs(it)
            contextRef.compareAndSet(null, it)
        }
    }

    val appContext: Context
        get() = contextRef.get() ?: throw IllegalStateException("Context not initialized")

    val repository: com.example.camerawatch.data.CameraSessionRepository by lazy {
        val ctx = appContext
        val passphrase = DatabasePassphraseProvider(ctx).getOrCreatePassphrase()
        val db = com.example.camerawatch.data.AppDatabase.build(ctx, passphrase)
        com.example.camerawatch.data.CameraSessionRepository(db.sessionDao())
    }
}

private class DatabasePassphraseProvider(private val context: Context) {
    private val sharedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "camera_watch_secure_storage",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getOrCreatePassphrase(): ByteArray {
        val existing = sharedPrefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            return android.util.Base64.decode(existing, android.util.Base64.DEFAULT)
        }
        val newKey = ByteArray(64)
        java.security.SecureRandom().nextBytes(newKey)
        val encoded = android.util.Base64.encodeToString(newKey, android.util.Base64.NO_WRAP)
        sharedPrefs.edit().putString(KEY_PASSPHRASE, encoded).apply()
        return newKey
    }

    companion object {
        private const val KEY_PASSPHRASE = "db_passphrase"
    }
}
