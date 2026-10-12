package com.caplease.manager.core

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.credentialStore by preferencesDataStore(name = "caplease_credentials")

class CredentialVault(private val context: Context) {

    private val saltKey = stringPreferencesKey("pairing_salt")
    private val pairedKey = stringPreferencesKey("pairing_state")

    suspend fun loadSalt(): ByteArray? = read(saltKey)?.decodeHexOrNull()

    suspend fun storeSalt(salt: ByteArray) {
        context.credentialStore.edit { store ->
            store[saltKey] = salt.toHex()
            store[pairedKey] = PAIRED
        }
    }

    suspend fun isPaired(): Boolean = read(pairedKey) == PAIRED

    suspend fun clear() {
        context.credentialStore.edit { store ->
            store.remove(saltKey)
            store.remove(pairedKey)
        }
    }

    private suspend fun read(key: Preferences.Key<String>): String? =
        context.credentialStore.data.first()[key]

    private companion object {
        const val PAIRED = "paired"
    }
}

internal fun ByteArray.toHex(): String {
    val table = "0123456789abcdef"
    val output = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        output.append(table[value ushr 4])
        output.append(table[value and 0x0F])
    }
    return output.toString()
}

internal fun String.decodeHexOrNull(): ByteArray? {
    if (length % 2 != 0) return null

    val output = ByteArray(length / 2)
    for (index in output.indices) {
        val high = Character.digit(this[index * 2], 16)
        val low = Character.digit(this[index * 2 + 1], 16)
        if (high < 0 || low < 0) return null
        output[index] = ((high shl 4) or low).toByte()
    }
    return output
}