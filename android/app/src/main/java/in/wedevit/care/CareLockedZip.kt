package `in`.wedevit.care

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.Deflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android's built-in PdfDocument cannot add a PDF password, so a locked copy is one file inside a
 * standard AES-256 encrypted ZIP (WinZip AE-2). It opens in 7-Zip, WinRAR and most phone file managers
 * that support locked ZIPs; some built-in "Extract" tools do not. The file name inside is not hidden.
 */
object CareLockedZip {
    const val MIN_PASSWORD = 6
    fun passwordError(password: String): String? = when {
        password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters."
        password.length > 64 -> "Use 64 characters or fewer."
        password.any { it.isISOControl() } -> "Use letters, numbers and symbols only."
        else -> null
    }

    fun create(name: String, content: ByteArray, password: String, random: SecureRandom = SecureRandom(), now: java.time.LocalDateTime = java.time.LocalDateTime.now()): ByteArray {
        val dosTime = (now.hour shl 11) or (now.minute shl 5) or (now.second / 2)
        val dosDate = ((now.year - 1980).coerceIn(0, 127) shl 9) or (now.monthValue shl 5) or now.dayOfMonth
        passwordError(password)?.let { throw IllegalArgumentException(it) }
        val fileName = name.replace('\\', '/').substringAfterLast('/').filter { it.code in 32..126 && it != '/' }.ifBlank { "file.pdf" }.toByteArray(Charsets.US_ASCII)
        val compressed = Deflater(Deflater.BEST_COMPRESSION, true).run {
            setInput(content); finish()
            val out = ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024)
            while (!finished()) out.write(buffer, 0, deflate(buffer))
            end(); out.toByteArray()
        }
        val salt = ByteArray(16).also(random::nextBytes)
        val keys = pbkdf2(password.toByteArray(Charsets.UTF_8), salt, 1000, 66)
        val encrypted = ctr(keys.copyOfRange(0, 32), compressed)
        val mac = Mac.getInstance("HmacSHA1").run { init(SecretKeySpec(keys.copyOfRange(32, 64), "HmacSHA1")); doFinal(encrypted) }.copyOf(10)
        val payload = salt + keys.copyOfRange(64, 66) + encrypted + mac
        val extra = le16(0x9901) + le16(7) + le16(2) + "AE".toByteArray(Charsets.US_ASCII) + byteArrayOf(3) + le16(8)
        fun common() = le16(51) + le16(1) + le16(99) + le16(dosTime) + le16(dosDate) + le32(0) + le32(payload.size.toLong()) + le32(content.size.toLong()) + le16(fileName.size) + le16(extra.size)
        val local = le32(0x04034b50) + common() + fileName + extra
        val central = le32(0x02014b50) + le16(51) + common() + le16(0) + le16(0) + le16(0) + le32(0) + le32(0) + fileName + extra
        val end = le32(0x06054b50) + le16(0) + le16(0) + le16(1) + le16(1) + le32(central.size.toLong()) + le32((local.size + payload.size).toLong()) + le16(0)
        return local + payload + central + end
    }

    internal fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(password.takeIf { it.isNotEmpty() } ?: byteArrayOf(0), "HmacSHA1")) }
        val out = ByteArrayOutputStream(); var block = 1
        while (out.size() < length) {
            var u = mac.doFinal(salt + byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            val t = u.copyOf()
            repeat(iterations - 1) { u = mac.doFinal(u); for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte() }
            out.write(t); block++
        }
        return out.toByteArray().copyOf(length)
    }

    /** AES-CTR as WinZip defines it: a 16-byte little-endian counter starting at 1. */
    internal fun ctr(key: ByteArray, data: ByteArray): ByteArray {
        val aes = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        val counter = ByteArray(16); val out = ByteArray(data.size)
        var offset = 0
        while (offset < data.size) {
            var i = 0; while (i < 16) { counter[i] = (counter[i] + 1).toByte(); if (counter[i] != 0.toByte()) break; i++ }
            val stream = aes.doFinal(counter)
            for (j in 0 until minOf(16, data.size - offset)) out[offset + j] = (data[offset + j].toInt() xor stream[j].toInt()).toByte()
            offset += 16
        }
        return out
    }

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())
    private fun le32(v: Long) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
}
