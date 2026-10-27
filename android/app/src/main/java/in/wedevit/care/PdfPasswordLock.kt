package `in`.wedevit.care

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Adds a real PDF open-password (AES-256, PDF 2.0 standard security handler, revision 6) to a PDF
 * made by Android's PdfDocument. Any PDF reader that supports passwords asks for it before showing
 * the page (Adobe, Chrome, Google Drive, Firefox, Preview). PdfDocument cannot do this itself.
 *
 * Works on classic-xref PDFs without object streams, which is what PdfDocument (Skia) writes.
 * Anything else is refused rather than written out half-encrypted.
 */
object PdfPasswordLock {
    const val MIN_PASSWORD = 6
    const val MAX_PASSWORD = 64

    fun passwordError(password: String): String? = when {
        password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters. · कम से कम $MIN_PASSWORD अक्षर रखें।"
        password.length > MAX_PASSWORD -> "Use $MAX_PASSWORD characters or fewer. · $MAX_PASSWORD से कम अक्षर रखें।"
        password.any { it.isISOControl() } -> "Use letters, numbers and symbols only. · केवल अक्षर, अंक और चिह्न।"
        else -> null
    }

    fun lock(pdf: ByteArray, password: String, random: SecureRandom = SecureRandom()): ByteArray {
        passwordError(password)?.let { throw IllegalArgumentException(it) }
        val reader = Reader(pdf)
        val trailer = reader.trailer()
        require(trailer.get("Encrypt") == null) { "This PDF is already locked." }
        val size = (trailer.get("Size") as? Raw)?.text?.toIntOrNull() ?: error("This PDF has no object count.")
        val key = ByteArray(32).also(random::nextBytes)
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.7\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        val offsets = sortedMapOf<Int, Int>()
        for ((number, offset) in reader.objectOffsets()) {
            val obj = reader.objectAt(offset, number)
            offsets[number] = out.size()
            out.write("$number ${obj.generation} obj\n".toByteArray(Charsets.ISO_8859_1))
            val value = encryptStrings(obj.value, key, random)
            if (obj.stream != null) {
                val data = aes(key, obj.stream, random)
                val dict = value as Dict
                write(out, Dict(dict.entries.filterNot { it.first == "Length" } + ("Length" to Raw(data.size.toString()))))
                out.write("\nstream\n".toByteArray(Charsets.ISO_8859_1)); out.write(data); out.write("\nendstream".toByteArray(Charsets.ISO_8859_1))
            } else write(out, value)
            out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        val encryptNumber = size
        offsets[encryptNumber] = out.size()
        out.write("$encryptNumber 0 obj\n".toByteArray(Charsets.ISO_8859_1))
        write(out, encryptDictionary(password.toByteArray(Charsets.UTF_8).let { if (it.size > 127) it.copyOf(127) else it }, key, random))
        out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        val xref = out.size()
        val total = encryptNumber + 1
        val table = StringBuilder("xref\n0 $total\n0000000000 65535 f \n")
        for (n in 1 until total) table.append(offsets[n]?.let { "%010d 00000 n \n".format(it) } ?: "0000000000 65535 f \n")
        out.write(table.toString().toByteArray(Charsets.ISO_8859_1))
        val id = ByteArray(16).also(random::nextBytes)
        val newTrailer = Dict(trailer.entries.filterNot { it.first in setOf("Size", "Prev", "Encrypt", "ID", "XRefStm") } +
            listOf("Size" to Raw(total.toString()), "Encrypt" to Raw("$encryptNumber 0 R"), "ID" to Arr(listOf(Str(id, true), Str(id, true)))))
        out.write("trailer\n".toByteArray(Charsets.ISO_8859_1)); write(out, newTrailer)
        out.write("\nstartxref\n$xref\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    // ---- Standard security handler, revision 6 (ISO 32000-2, 7.6.4.3) ----

    private fun encryptDictionary(password: ByteArray, key: ByteArray, random: SecureRandom): Dict {
        fun salt() = ByteArray(8).also(random::nextBytes)
        val validation = salt(); val keySalt = salt()
        val u = hash(password, validation, ByteArray(0)) + validation + keySalt
        val ue = aesNoPad(hash(password, keySalt, ByteArray(0)), key)
        // Nobody needs owner rights over a family's own card: the owner password is random and thrown away.
        val owner = ByteArray(32).also(random::nextBytes)
        val ownerValidation = salt(); val ownerKeySalt = salt()
        val o = hash(owner, ownerValidation, u) + ownerValidation + ownerKeySalt
        val oe = aesNoPad(hash(owner, ownerKeySalt, u), key)
        val permissions = -4
        val perms = ByteArray(16).also { p ->
            for (i in 0 until 4) p[i] = (permissions shr (8 * i)).toByte()
            for (i in 4 until 8) p[i] = 0xFF.toByte()
            p[8] = 'T'.code.toByte(); p[9] = 'a'.code.toByte(); p[10] = 'd'.code.toByte(); p[11] = 'b'.code.toByte()
            random.nextBytes(ByteArray(4).also { it.copyInto(p, 12) })
        }
        val permsEncrypted = Cipher.getInstance("AES/ECB/NoPadding").run { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")); doFinal(perms) }
        val filter = Dict(listOf("CFM" to Raw("/AESV3"), "AuthEvent" to Raw("/DocOpen"), "Length" to Raw("32")))
        return Dict(listOf(
            "Filter" to Raw("/Standard"), "V" to Raw("5"), "R" to Raw("6"), "Length" to Raw("256"),
            "CF" to Dict(listOf("StdCF" to filter)), "StmF" to Raw("/StdCF"), "StrF" to Raw("/StdCF"),
            "O" to Str(o, true), "U" to Str(u, true), "OE" to Str(oe, true), "UE" to Str(ue, true),
            "P" to Raw(permissions.toString()), "Perms" to Str(permsEncrypted, true), "EncryptMetadata" to Raw("true"),
        ))
    }

    /** Algorithm 2.B. */
    internal fun hash(password: ByteArray, salt: ByteArray, userKey: ByteArray): ByteArray {
        var k = MessageDigest.getInstance("SHA-256").digest(password + salt + userKey)
        var round = 0
        while (true) {
            val block = password + k + userKey
            val k1 = ByteArray(block.size * 64).also { for (i in 0 until 64) block.copyInto(it, i * block.size) }
            val e = Cipher.getInstance("AES/CBC/NoPadding").run { init(Cipher.ENCRYPT_MODE, SecretKeySpec(k.copyOf(16), "AES"), IvParameterSpec(k.copyOfRange(16, 32))); doFinal(k1) }
            val mod = e.copyOf(16).sumOf { it.toInt() and 0xFF } % 3
            k = MessageDigest.getInstance(arrayOf("SHA-256", "SHA-384", "SHA-512")[mod]).digest(e)
            round++
            if (round >= 64 && (e.last().toInt() and 0xFF) <= round - 32) break
        }
        return k.copyOf(32)
    }

    private fun aesNoPad(key: ByteArray, data: ByteArray) = Cipher.getInstance("AES/CBC/NoPadding").run { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16))); doFinal(data) }

    /** AESV3 strings and streams: a random 16-byte IV, then AES-256-CBC with PKCS#7 padding. */
    private fun aes(key: ByteArray, data: ByteArray, random: SecureRandom): ByteArray {
        val iv = ByteArray(16).also(random::nextBytes)
        return iv + Cipher.getInstance("AES/CBC/PKCS5Padding").run { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)); doFinal(data) }
    }

    private fun encryptStrings(value: Value, key: ByteArray, random: SecureRandom): Value = when (value) {
        is Str -> Str(aes(key, value.bytes, random), true)
        is Arr -> Arr(value.items.map { encryptStrings(it, key, random) })
        is Dict -> Dict(value.entries.map { it.first to encryptStrings(it.second, key, random) })
        is Raw -> value
    }

    // ---- A small PDF object model: enough for PdfDocument output ----

    private sealed interface Value
    private data class Raw(val text: String) : Value
    private class Str(val bytes: ByteArray, val hex: Boolean) : Value
    private data class Arr(val items: List<Value>) : Value
    private data class Dict(val entries: List<Pair<String, Value>>) : Value { fun get(name: String) = entries.lastOrNull { it.first == name }?.second }
    private class PdfObject(val generation: Int, val value: Value, val stream: ByteArray?)

    private fun write(out: ByteArrayOutputStream, value: Value) {
        when (value) {
            is Raw -> out.write(value.text.toByteArray(Charsets.ISO_8859_1))
            is Str -> out.write(("<" + value.bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) } + ">").toByteArray(Charsets.ISO_8859_1))
            is Arr -> { out.write('['.code); value.items.forEachIndexed { i, v -> if (i > 0) out.write(' '.code); write(out, v) }; out.write(']'.code) }
            is Dict -> { out.write("<<".toByteArray()); value.entries.forEach { (k, v) -> out.write("/$k ".toByteArray(Charsets.ISO_8859_1)); write(out, v); out.write('\n'.code) }; out.write(">>".toByteArray()) }
        }
    }

    private class Reader(val b: ByteArray) {
        private var p = 0
        private fun ws(c: Int) = c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32
        private fun delim(c: Int) = c in "()<>[]{}/%".map { it.code }
        private fun at(i: Int) = if (i < b.size) b[i].toInt() and 0xFF else -1
        private fun skip() { while (p < b.size) { val c = at(p); if (ws(c)) p++ else if (c == '%'.code) { while (p < b.size && at(p) != 10 && at(p) != 13) p++ } else break } }
        private fun word(): String { skip(); val s = p; while (p < b.size && !ws(at(p)) && !delim(at(p))) p++; return String(b, s, p - s, Charsets.ISO_8859_1) }
        private fun expect(text: String) { skip(); val bytes = text.toByteArray(Charsets.ISO_8859_1); require(p + bytes.size <= b.size && (bytes.indices).all { b[p + it] == bytes[it] }) { "Unexpected PDF layout near byte $p." }; p += bytes.size }
        private fun lastIndexOf(text: String): Int { val t = text.toByteArray(Charsets.ISO_8859_1); for (i in b.size - t.size downTo 0) if (t.indices.all { b[i + it] == t[it] }) return i; return -1 }

        fun trailer(): Dict {
            val start = lastIndexOf("startxref"); require(start >= 0) { "Not a PDF file." }
            p = start + "startxref".length
            p = word().toIntOrNull() ?: error("Broken PDF cross-reference.")
            require(word() == "xref") { "This PDF uses a compressed layout that cannot be locked here." }
            while (true) { val save = p; val first = word(); if (first == "trailer") break; p = save; word(); val count = word().toInt(); repeat(count) { word(); word(); word() } }
            val trailer = value() as Dict
            require(trailer.get("Prev") == null) { "This PDF was edited after it was made and cannot be locked here." }
            return trailer
        }

        fun objectOffsets(): List<Pair<Int, Int>> {
            val start = lastIndexOf("startxref"); p = start + "startxref".length; p = word().toInt(); word()
            val result = mutableListOf<Pair<Int, Int>>()
            while (true) {
                val save = p; val first = word(); if (first == "trailer") break
                p = save; val from = word().toInt(); val count = word().toInt()
                for (i in 0 until count) { val offset = word().toInt(); word(); val kind = word(); if (kind == "n" && from + i != 0) result += (from + i) to offset }
            }
            return result.sortedBy { it.first }
        }

        fun objectAt(offset: Int, number: Int): PdfObject {
            p = offset
            require(word().toIntOrNull() == number) { "PDF object $number is not where the file says." }
            val generation = word().toInt(); require(word() == "obj") { "Broken PDF object $number." }
            val value = value()
            skip()
            val save = p
            if (word() == "stream") {
                if (at(p) == 13) p++
                if (at(p) == 10) p++
                val dict = value as? Dict ?: error("Broken PDF stream $number.")
                val length = when (val l = dict.get("Length")) {
                    is Raw -> l.text.trim().let { t -> t.toIntOrNull() ?: refLength(t) }
                    else -> error("PDF stream $number has no length.")
                }
                val data = b.copyOfRange(p, p + length); p += length
                expect("endstream"); expect("endobj")
                return PdfObject(generation, Dict(dict.entries.filterNot { it.first == "Length" }), data)
            }
            p = save; expect("endobj")
            return PdfObject(generation, value, null)
        }

        private fun refLength(ref: String): Int {
            val parts = ref.split(Regex("\\s+")); require(parts.size == 3 && parts[2] == "R") { "Unreadable PDF stream length." }
            val target = objectOffsets().firstOrNull { it.first == parts[0].toInt() } ?: error("Missing PDF stream length.")
            val save = p; val obj = objectAt(target.second, target.first); p = save
            return (obj.value as Raw).text.trim().toInt()
        }

        fun value(): Value {
            skip()
            return when (at(p)) {
                '/'.code -> { p++; Raw("/" + word()) }
                '('.code -> literal()
                '['.code -> { p++; val items = mutableListOf<Value>(); while (true) { skip(); if (at(p) == ']'.code) { p++; break }; items += value() }; Arr(items) }
                '<'.code -> if (at(p + 1) == '<'.code) dict() else hexString()
                else -> {
                    val first = word(); require(first.isNotEmpty()) { "Unexpected PDF syntax near byte $p." }
                    if (first.toIntOrNull() != null) {
                        val save = p; val second = word(); val third = word()
                        if (second.toIntOrNull() != null && third == "R") Raw("$first $second R") else { p = save; Raw(first) }
                    } else Raw(first)
                }
            }
        }

        private fun dict(): Dict {
            p += 2; val entries = mutableListOf<Pair<String, Value>>()
            while (true) {
                skip()
                if (at(p) == '>'.code && at(p + 1) == '>'.code) { p += 2; return Dict(entries) }
                require(at(p) == '/'.code) { "Broken PDF dictionary near byte $p." }
                p++; val name = word(); entries += name to value()
            }
        }

        private fun hexString(): Str {
            p++; val digits = StringBuilder()
            while (at(p) != '>'.code) { val c = at(p); require(c >= 0) { "Broken PDF string." }; if (!ws(c)) digits.append(c.toChar()); p++ }
            p++
            if (digits.length % 2 == 1) digits.append('0')
            return Str(ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, true)
        }

        private fun literal(): Str {
            p++; val out = ByteArrayOutputStream(); var depth = 1
            while (true) {
                val c = at(p++); require(c >= 0) { "Broken PDF string." }
                when (c) {
                    '('.code -> { depth++; out.write(c) }
                    ')'.code -> { depth--; if (depth == 0) return Str(out.toByteArray(), false); out.write(c) }
                    '\\'.code -> {
                        val e = at(p++)
                        when (e) {
                            'n'.code -> out.write(10); 'r'.code -> out.write(13); 't'.code -> out.write(9); 'b'.code -> out.write(8); 'f'.code -> out.write(12)
                            13 -> if (at(p) == 10) p++
                            10 -> {}
                            in '0'.code..'7'.code -> { var v = e - '0'.code; repeat(2) { if (at(p) in '0'.code..'7'.code) v = v * 8 + (at(p++) - '0'.code) }; out.write(v and 0xFF) }
                            else -> out.write(e)
                        }
                    }
                    else -> out.write(c)
                }
            }
        }
    }
}
