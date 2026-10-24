package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Actual local PDF, never a prescription, verified signature, clinical record or public URL. */
object DoctorWorkflowPdf {
    fun create(context: Context, version: WorkflowVersion, patientName: String): File {
        val folder = File(context.cacheDir, "doctor-preview-exports").apply { mkdirs() }
        val file = File(folder, "care-note-preview-${version.id.replace(Regex("[^A-Za-z0-9-]"), "")}.pdf")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(56, 46, 41) }
        val heading = Paint(paint).apply { textSize = 17f; isFakeBoldText = true }
        val footer = Paint(paint).apply { textSize = 9f }
        val pdf = PdfDocument()
        var number = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun start() {
            number++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
            page!!.canvas.drawText("Sahara | Care Note preview", 42f, 44f, heading)
            page!!.canvas.drawText("UNVERIFIED LOCAL PREVIEW - NOT A CLINICAL RECORD", 42f, 63f, footer)
            y = 94f
        }
        fun finish() {
            page!!.canvas.drawText("Local preview only | Page $number", 42f, 812f, footer)
            pdf.finishPage(page!!)
        }
        fun line(value: String) {
            if (y > 775f) { finish(); start() }
            page!!.canvas.drawText(value, 42f, y, paint)
            y += 17f
        }
        fun paragraph(value: String) {
            value.lines().forEach { original ->
                var remaining = original
                if (remaining.isEmpty()) line("")
                while (remaining.isNotEmpty()) {
                    val count = paint.breakText(remaining, true, 511f, null).coerceAtLeast(1)
                    val split = if (count < remaining.length) remaining.lastIndexOf(' ', count).takeIf { it > 0 } ?: count else count
                    line(remaining.take(split)); remaining = remaining.drop(split).trimStart()
                }
            }
            line("")
        }
        try {
            start()
            paragraph("$patientName | Version ${version.number}")
            paragraph("Reviewer (self-entered): ${version.reviewerName}\nPublished locally: ${DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(version.publishedAt))}\n${version.reviewLabel}")
            val d = version.draft
            if(d.transcribedAt>0) paragraph("Transcript provenance\n${d.transcriptProvider} / ${d.transcriptModel}\nCreated ${Instant.ofEpochMilli(d.transcribedAt)} from a consented local conversation recording.")
            if(d.aiModel.isNotBlank()) paragraph("AI drafting provenance\n${d.aiProvider} / ${d.aiModel}\n"+d.aiWarnings.joinToString("\n"))
            listOf("What matters" to d.priorities, "Participants" to d.participants, "Discussion" to d.topics, "Questions" to d.questions, "Undecided" to d.undecided, "Next steps" to d.nextSteps).forEach { (title, text) -> paragraph("$title\n${text.ifBlank { "Not recorded" }}") }
            paragraph("Discussion checklist\n" + d.checklist.joinToString("\n") { "${it.title}: ${it.status.name.lowercase().replace('_', ' ')}" })
            paragraph("Source references\n" + if(d.transcribedAt>0) "Consented conversation recording and exact transcript below" else DoctorWorkflowData.sources(version.patientId).filter { it.id in d.sourceIds }.joinToString("\n") { it.label }.ifBlank { "Manually entered source" })
            if(d.evidence.isNotEmpty()) paragraph("Source evidence\n"+d.evidence.joinToString("\n") { "${it.section}: ${it.quote}" })
            paragraph("Original source text\n${d.sourceText}")
            paragraph("This is a local workflow preview. No professional identity was verified. This document is not a prescription, treatment order, consent form, advance directive or verified signature. No external patient/family delivery has occurred.")
            finish()
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        return file
    }
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share local preview PDF"))
    }
}
