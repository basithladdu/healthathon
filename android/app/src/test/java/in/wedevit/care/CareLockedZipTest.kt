package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CareLockedZipTest {
    private val pdf = ("%PDF-1.4\n" + "Care note line\n".repeat(400) + "%%EOF").toByteArray()

    /** Independent reader: JDK PBKDF2 and AES/CTR, following the WinZip AE-2 layout. */
    private fun open(zip: ByteArray, password: String): ByteArray {
        val b = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x04034b50, b.getInt(0)); assertEquals(1, b.getShort(6).toInt() and 1); assertEquals(99, b.getShort(8).toInt())
        val size = b.getInt(18); val plainSize = b.getInt(22); val nameLength = b.getShort(26).toInt(); val extraLength = b.getShort(28).toInt()
        val extra = 30 + nameLength
        assertEquals(0x9901, b.getShort(extra).toInt() and 0xffff); assertEquals(3, zip[extra + 8].toInt()); assertEquals(8, b.getShort(extra + 9).toInt())
        val start = extra + extraLength
        val salt = zip.copyOfRange(start, start + 16)
        val keys = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec(password.toCharArray(), salt, 1000, 66 * 8)).encoded
        require(zip[start + 16] == keys[64] && zip[start + 17] == keys[65]) { "wrong password" }
        val data = zip.copyOfRange(start + 18, start + size - 10)
        val mac = Mac.getInstance("HmacSHA1").run { init(SecretKeySpec(keys.copyOfRange(32, 64), "HmacSHA1")); doFinal(data) }.copyOf(10)
        assertArrayEquals(mac, zip.copyOfRange(start + size - 10, start + size))
        val aes = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.copyOfRange(0, 32), "AES")) }
        val compressed = ByteArray(data.size) { i ->
            val block = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putLong(i / 16 + 1L).array()
            (data[i].toInt() xor aes.doFinal(block)[i % 16].toInt()).toByte()
        }
        val out = ByteArray(plainSize); Inflater(true).run { setInput(compressed); assertEquals(plainSize, inflate(out)); end() }
        assertEquals(0x06054b50, b.getInt(zip.size - 22))
        return out
    }

    @Test fun lockedZipOpensOnlyWithItsPassword() {
        val zip = CareLockedZip.create("care-note-preview-1.pdf", pdf, "sahara-2026")
        assertArrayEquals(pdf, open(zip, "sahara-2026"))
        assertThrows(IllegalArgumentException::class.java) { open(zip, "wrong-pass") }
        assertFalse(String(zip, Charsets.ISO_8859_1).contains("Care note line"))
    }
    @Test fun shortPasswordsAreRefused() {
        assertNotNull(CareLockedZip.passwordError("12345"))
        assertNull(CareLockedZip.passwordError("123456"))
        assertThrows(IllegalArgumentException::class.java) { CareLockedZip.create("a.pdf", pdf, "abc") }
    }
    @Test fun pbkdf2MatchesTheJdk() {
        val salt = ByteArray(16) { it.toByte() }
        val jdk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec("pass word".toCharArray(), salt, 1000, 66 * 8)).encoded
        assertArrayEquals(jdk, CareLockedZip.pbkdf2("pass word".toByteArray(), salt, 1000, 66))
    }
}
