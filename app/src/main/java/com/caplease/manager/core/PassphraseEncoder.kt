package com.caplease.manager.core

import java.security.SecureRandom
import kotlin.math.ln

object PassphraseEncoder {

    private const val MIN_LENGTH = 10
    private const val MIN_CHARACTER_CLASSES = 3

    private val random = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        random.nextBytes(salt)
        return salt
    }

    fun encode(passphrase: CharArray): ByteArray =
        passphrase.concatToString().toByteArray(Charsets.UTF_8)

    fun validate(passphrase: CharArray): Validation {
        val text = passphrase.concatToString()

        if (text.length < MIN_LENGTH) {
            return Validation.TooShort(MIN_LENGTH)
        }

        val classes = characterClasses(text)
        if (classes < MIN_CHARACTER_CLASSES) {
            return Validation.Weak(classes)
        }

        if (text.all { it == text.first() }) {
            return Validation.Weak(classes)
        }

        if (text.zipWithNext().all { (left, right) -> right == left + 1 }) {
            return Validation.Weak(classes)
        }

        return Validation.Acceptable(entropyBits(text))
    }

    private fun characterClasses(text: String): Int = listOf(
        text.any { it.isLowerCase() },
        text.any { it.isUpperCase() },
        text.any { it.isDigit() },
        text.any { !it.isLetterOrDigit() }
    ).count { it }

    private fun entropyBits(text: String): Double {
        var alphabet = 0
        if (text.any { it.isLowerCase() }) alphabet += 26
        if (text.any { it.isUpperCase() }) alphabet += 26
        if (text.any { it.isDigit() }) alphabet += 10
        if (text.any { !it.isLetterOrDigit() }) alphabet += 32

        if (alphabet <= 1) return 0.0
        return text.length * (ln(alphabet.toDouble()) / ln(2.0))
    }

    const val SALT_SIZE = 16

    sealed interface Validation {
        data class Acceptable(val entropyBits: Double) : Validation
        data class TooShort(val required: Int) : Validation
        data class Weak(val characterClasses: Int) : Validation
    }
}