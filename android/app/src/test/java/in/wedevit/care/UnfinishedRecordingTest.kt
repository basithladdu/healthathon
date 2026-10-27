package `in`.wedevit.care

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class UnfinishedRecordingTest {
    private fun root(): File = Files.createTempDirectory("recordings").toFile()

    @Test fun unfinishedClipInThePersonsFolderIsRemoved() {
        val root = root(); val clip = File(root, "rahul/clip.m4a").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(0)) }
        assertTrue(discardUnfinishedDoctorLocalAudio(clip, root, "rahul"))
        assertFalse(clip.exists())
        root.deleteRecursively()
    }

    @Test fun alreadyMissingClipCountsAsRemoved() {
        val root = root(); File(root, "rahul").mkdirs()
        assertTrue(discardUnfinishedDoctorLocalAudio(File(root, "rahul/never-written.m4a"), root, "rahul"))
        root.deleteRecursively()
    }

    @Test fun neverTouchesFilesOutsideThatPersonsRecordings() {
        val root = root()
        val other = File(root, "arjun/clip.m4a").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(0)) }
        val notAudio = File(root, "rahul/clip.offline-transcript.json").apply { parentFile!!.mkdirs(); writeText("{}") }
        val outside = File(root.parentFile, "${root.name}-outside.m4a").apply { writeBytes(byteArrayOf(0)) }
        assertFalse(discardUnfinishedDoctorLocalAudio(other, root, "rahul"))
        assertFalse(discardUnfinishedDoctorLocalAudio(notAudio, root, "rahul"))
        assertFalse(discardUnfinishedDoctorLocalAudio(outside, root, "rahul"))
        assertFalse(discardUnfinishedDoctorLocalAudio(File(root, "rahul/../arjun/clip.m4a"), root, "rahul"))
        assertFalse(discardUnfinishedDoctorLocalAudio(null, root, "rahul"))
        assertFalse(discardUnfinishedDoctorLocalAudio(other, root, ""))
        assertTrue(other.exists() && notAudio.exists() && outside.exists())
        outside.delete(); root.deleteRecursively()
    }
}
