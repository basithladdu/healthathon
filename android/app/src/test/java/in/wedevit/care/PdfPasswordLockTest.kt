package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.SecureRandom

class PdfPasswordLockTest {
    /** A real PdfDocument (Skia) file from the 0.5.0 CI emulator run; fictional content only. */
    private val skia = requireNotNull(javaClass.classLoader!!.getResource("skia-fictional-preview.pdf")).readBytes()

    @Test fun lockedFileHasAes256HandlerAndNoReadableText() {
        val locked = PdfPasswordLock.lock(skia, "garden42", SecureRandom())
        val text = String(locked, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.7"))
        assertTrue(text.contains("/Filter /Standard")); assertTrue(text.contains("/V 5")); assertTrue(text.contains("/R 6")); assertTrue(text.contains("/CFM /AESV3"))
        assertFalse("Info string left in clear", text.contains("Skia/PDF"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
    }

    @Test fun weakPasswordsAndRelockingAreRefused() {
        assertNotNull(PdfPasswordLock.passwordError("abc"))
        assertNotNull(PdfPasswordLock.passwordError("a".repeat(65)))
        assertNull(PdfPasswordLock.passwordError("garden42"))
        assertThrows(IllegalArgumentException::class.java) { PdfPasswordLock.lock(skia, "abc") }
        val locked = PdfPasswordLock.lock(skia, "garden42")
        assertThrows(IllegalArgumentException::class.java) { PdfPasswordLock.lock(locked, "garden42") }
        assertThrows(Exception::class.java) { PdfPasswordLock.lock("not a pdf".toByteArray(), "garden42") }
    }

    /** Independent check with Poppler, when this machine has it: right password opens, none and wrong do not. */
    @Test fun popplerOpensItOnlyWithThePassword() {
        val tool = listOf("/usr/bin/pdftotext", "/usr/local/bin/pdftotext").map(::File).firstOrNull(File::canExecute)
        assumeTrue("pdftotext not installed", tool != null)
        val file = File.createTempFile("locked", ".pdf").apply { deleteOnExit(); writeBytes(PdfPasswordLock.lock(skia, "garden42 नमस्ते")) }
        fun run(vararg args: String): Pair<Int, String> { val p = ProcessBuilder(listOf(tool!!.path) + args.toList() + listOf(file.path, "-")).redirectErrorStream(true).start(); val out = p.inputStream.bufferedReader().readText(); return p.waitFor() to out }
        val (ok, text) = run("-upw", "garden42 नमस्ते")
        assertEquals(text, 0, ok); assertTrue(text, text.contains("Care Note preview"))
        assertNotEquals(0, run().first)
        assertNotEquals(0, run("-upw", "wrong-one").first)
    }
}
