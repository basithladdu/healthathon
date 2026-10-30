package `in`.wedevit.care

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.io.File
import java.time.LocalDate

/**
 * Local PDFs drawn with Android's PdfDocument, like [EmergencyCardPdf]: the emergency plan (ED page first, then the wallet
 * card with its QR) and the living will in the Vidhi model layout with blank signature, witness and attestation blocks.
 * Every page carries the V8 footer: name, UHID, version, form version, print date, page x of y.
 */
internal object CarePlanPdf {
    private const val W = 595; private const val H = 842; private const val LEFT = 42f; private const val WIDTH = 511f; private const val BOTTOM = 790f
    private val ink = android.graphics.Color.rgb(56, 46, 41)
    private fun paint(size: Float, bold: Boolean = false, color: Int = ink) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; isFakeBoldText = bold; this.color = color }

    private sealed interface Block { val height: Float }
    private data class Line(val text: String, val paint: Paint, override val height: Float, val x: Float = 0f, val prefix: String = "", val prefixX: Float = 0f) : Block
    private data class Qr(val url: String, val side: Float, val level: ErrorCorrectionLevel = ErrorCorrectionLevel.Q) : Block { override val height get() = side + 8f }
    private data class TableRow(val cells: List<List<String>>, val widths: List<Float>, val paint: Paint, val x: Float, val minHeight: Float) : Block { override val height get() = maxOf(minHeight, cells.maxOf { it.size } * (paint.textSize + 4f)) + 8f }
    private data class Rule(val x: Float, val width: Float) : Block { override val height = 8f }
    private data class Stamp(val x: Float, val w: Float, val h: Float) : Block { override val height get() = h + 10f }
    private data class Box(val lines: List<Line>, val fill: Int) : Block { override val height get() = lines.sumOf { it.height.toDouble() }.toFloat() + 16f }
    private object PageBreak : Block { override val height = 0f }

    private fun wrap(value: String, paint: Paint, width: Float = WIDTH): List<String> = value.lines().flatMap { original ->
        val out = mutableListOf<String>(); var rest = original
        if (rest.isEmpty()) out += ""
        while (rest.isNotEmpty()) {
            val count = paint.breakText(rest, true, width, null).coerceAtLeast(1)
            val split = if (count < rest.length) rest.lastIndexOf(' ', count).takeIf { it > 0 } ?: count else count
            out += rest.take(split); rest = rest.drop(split).trimStart()
        }
        out
    }

    private class Doc { val blocks = mutableListOf<Block>()
        fun text(value: String, paint: Paint, gap: Float = paint.textSize + 5f, width: Float = WIDTH) { wrap(value, paint, width).forEach { blocks += Line(it, paint, gap) } }
        fun space(h: Float = 8f) { blocks += Line("", paint(4f), h) }
        fun box(fill: Int, vararg parts: Pair<String, Paint>) { blocks += Box(parts.flatMap { (v, p) -> wrap(v, p, WIDTH - 20f).map { Line(it, p, p.textSize + 5f) } }, fill) }
    }

    private fun render(file: File, doc: Doc, footer: (Int, Int) -> String) = render(file, doc) { c, page, pages ->
        val small = paint(8f); wrap(footer(page, pages), small).forEachIndexed { k, l -> c.drawText(l, LEFT, 808f + k * 11f, small) }
    }

    private fun render(file: File, doc: Doc, decorate: (android.graphics.Canvas, Int, Int) -> Unit) {
        // Paginate first, so each footer can say "page x of y".
        val pages = mutableListOf(mutableListOf<Block>()); var y = 48f
        doc.blocks.forEach { b ->
            if (b === PageBreak) { pages += mutableListOf<Block>(); y = 48f; return@forEach }
            if (y + b.height > BOTTOM && pages.last().isNotEmpty()) { pages += mutableListOf<Block>(); y = 48f }
            pages.last() += b; y += b.height
        }
        file.parentFile?.mkdirs()
        val pdf = PdfDocument(); val black = Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }
        try {
            pages.forEachIndexed { i, blocks ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create()); val c = page.canvas; var top = 48f
                blocks.forEach { b ->
                    when (b) {
                        is Line -> { if (b.prefix.isNotEmpty()) c.drawText(b.prefix, LEFT + b.prefixX, top + b.paint.textSize, b.paint); c.drawText(b.text, LEFT + b.x, top + b.paint.textSize, b.paint); top += b.height }
                        is TableRow -> { var cx = LEFT + b.x; val border = Paint().apply { color = android.graphics.Color.rgb(50, 50, 50); style = Paint.Style.STROKE; strokeWidth = .6f }
                            b.cells.forEachIndexed { i, lines -> c.drawRect(cx, top, cx + b.widths[i], top + b.height, border); lines.forEachIndexed { k, l -> c.drawText(l, cx + 4f, top + 4f + b.paint.textSize + k * (b.paint.textSize + 4f), b.paint) }; cx += b.widths[i] }
                            top += b.height }
                        is Rule -> { c.drawLine(LEFT + b.x, top + 3f, LEFT + b.x + b.width, top + 3f, Paint().apply { color = android.graphics.Color.GRAY; strokeWidth = .5f }); top += b.height }
                        is Stamp -> { c.drawRect(LEFT + b.x, top, LEFT + b.x + b.w, top + b.h, Paint().apply { color = android.graphics.Color.GRAY; style = Paint.Style.STROKE; strokeWidth = .6f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f) }); top += b.height }
                        is Box -> { c.drawRect(LEFT - 6f, top, LEFT + WIDTH + 6f, top + b.height - 4f, Paint().apply { color = b.fill; style = Paint.Style.FILL })
                            var ly = top + 8f; b.lines.forEach { l -> c.drawText(l.text, LEFT + 4f, ly + l.paint.textSize, l.paint); ly += l.height }; top += b.height }
                        is Qr -> { val m = Encoder.encode(b.url, b.level, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")).matrix; val quiet = 4; val cell = b.side / (m.width + 2 * quiet)
                            for (row in 0 until m.height) for (col in 0 until m.width) if (m.get(col, row).toInt() == 1) c.drawRect(LEFT + (col + quiet) * cell, top + (row + quiet) * cell, LEFT + (col + quiet + 1) * cell, top + (row + quiet + 1) * cell, black)
                            top += b.height }
                        PageBreak -> Unit
                    }
                }
                decorate(c, i + 1, pages.size)
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
    }

    fun plan(file: File, r: EctprRecord, interp: EdInterpretation, amd: Bi, cardUrl: String?, offlineText: String?, cardOnServer: Boolean, hindi: Boolean, today: LocalDate) {
        fun p(b: Bi) = b.pick(hindi)
        val h1 = paint(17f, true); val h2 = paint(13f, true); val body = paint(10.5f); val bold = paint(10.5f, true); val white = paint(10.5f, true, android.graphics.Color.WHITE); val whiteBody = paint(10f, false, android.graphics.Color.WHITE)
        val alert = android.graphics.Color.rgb(158, 59, 35); val mint = android.graphics.Color.rgb(220, 235, 228); val pale = android.graphics.Color.rgb(247, 221, 210)
        val d = Doc()
        d.text(p(Bi("Sahara emergency care plan (ECTPR) — ED Quick View", "सहारा आपातकालीन देखभाल योजना (ECTPR) — ED क्विक व्यू")), h1, 24f)
        d.text("${r.patient.name} · UHID ${r.patient.uhid.ifBlank { "—" }} · DOB ${r.patient.dob.ifBlank { "—" }}", h2, 20f)
        d.box(if (interp.kind == EdKind.ACTIVE) mint else pale, p(interp.banner) to bold)
        d.space(6f)
        d.box(alert, p(IfInDoubt.TITLE) to paint(13f, true, android.graphics.Color.WHITE), p(IfInDoubt.LEAD) to whiteBody, *IfInDoubt.CONDITIONS.map { "• " + p(it) to whiteBody }.toTypedArray(), p(IfInDoubt.AFTER) to white)
        d.space(6f)
        if (interp.showContent) EdView.rows(r, amd).forEach { row -> d.text(p(row.label).uppercase(), paint(8.5f, true, android.graphics.Color.rgb(85, 72, 63)), 12f); d.text(p(row.value), if (row.flag) paint(10.5f, true, alert) else bold, 14f); d.space(3f) }
        else d.text(p(Bi("No plan content is shown: give full standard emergency care.", "योजना की जानकारी नहीं दिखाई जाती: पूरा सामान्य आपातकालीन इलाज दें।")), bold)
        d.space(6f)
        d.box(mint, p(CareThatContinues.TITLE) to h2, *CareThatContinues.ITEMS.map { "• " + p(it) to body }.toTypedArray(), p(CareThatContinues.NOTE) to bold)
        if (interp.showContent) {
            d.blocks += PageBreak
            d.text(p(Bi("Full record", "पूरा रिकॉर्ड")), h1, 24f)
            fun field(label: Bi, value: String) { if (value.isNotBlank()) { d.text(p(label) + ": " + value, body) } }
            field(Bi("Diagnosis", "बीमारी"), listOf(r.clinical.diagnosis, r.clinical.stage, r.clinical.ecog?.let { "ECOG $it" }.orEmpty()).filter(String::isNotBlank).joinToString(" · "))
            field(Bi("Key medicines", "मुख्य दवाएँ"), r.clinical.keyMedicines)
            field(Bi("Alternate decision-maker", "दूसरे फ़ैसला लेने वाले"), r.decisionMakers.alternate?.let { listOf(it.name, it.relation, it.phone24h).filter(String::isNotBlank).joinToString(" · ") }.orEmpty())
            field(Bi("Family to inform", "परिवार जिन्हें बताना है"), r.decisionMakers.familyToInform.filter(String::isNotBlank).joinToString(", "))
            field(Bi("What matters, in the patient's words", "क्या ज़रूरी है, मरीज़ के शब्दों में"), r.values.ownWords)
            field(Bi("Not acceptable to the patient", "मरीज़ को मंज़ूर नहीं"), r.values.unacceptableOutcomes.filter(String::isNotBlank).joinToString("; "))
            d.space(4f); d.text(p(AmdText.LEGAL_PENDING_TITLE) + ": " + p(AmdText.LEGAL_PENDING), paint(9f)); d.text(p(AmdText.RECONSIDERATION), paint(9f)); d.space(4f)
            field(Bi("Understanding confirmed", "समझ की पुष्टि"), if (r.prognosticUnderstanding.confirmed) "${r.prognosticUnderstanding.confirmedBy} ${r.prognosticUnderstanding.confirmedOn}".trim() else "")
            d.space(8f); d.text(p(Bi("Signatures", "हस्ताक्षर")), h2, 18f)
            r.signatures.forEach { s -> d.text("${s.role.name.replace('_', ' ').lowercase()}: ${s.name}${s.registrationNo?.let { " (reg. $it)" } ?: ""}${if (s.thumbImpression == true) " — thumb impression" else ""} · ${s.signedAt.take(16).replace('T', ' ')}", body) }
            if (cardUrl != null) {
                d.space(16f); d.text(p(Bi("Wallet card — cut out and keep with the patient", "वॉलेट कार्ड — काटकर मरीज़ के पास रखें")), h2, 20f)
                WalletCard.lines(r).forEach { (label, value) -> d.text(p(label) + ": " + p(value), body, 14f) }
                d.box(alert, p(WalletCard.TREAT_LINE) to white)
                d.text(p(Bi("Link QR: full card, needs internet", "लिंक QR: पूरा कार्ड, इंटरनेट चाहिए")), bold)
                d.blocks += Qr(cardUrl, 130f)
                d.text(p(Bi("The QR is a random link with no personal details; it is switched off if the plan is replaced or withdrawn.", "QR एक रैंडम लिंक है जिसमें कोई निजी जानकारी नहीं; योजना बदलने या वापस लेने पर यह बंद हो जाता है।")), paint(9f))
                if (!cardOnServer) d.text(p(Bi("When this was printed the card was saved on the phone only: the link QR may not open yet. Use the offline QR.", "छपते समय कार्ड सिर्फ़ फ़ोन पर सहेजा गया था: लिंक QR शायद अभी न खुले। ऑफ़लाइन QR इस्तेमाल करें।")), paint(9f, true))
                if (offlineText != null) {
                    d.text(p(Bi("Offline QR: this summary as text, no internet needed", "ऑफ़लाइन QR: यह सारांश लिखित रूप में, इंटरनेट नहीं चाहिए")), bold)
                    d.blocks += Qr(offlineText, 170f, ErrorCorrectionLevel.L)
                }
            }
        }
        render(file, d) { page, pages -> EctprRules.pageFooter(r, today, page, pages) }
    }

    /** The Vidhi layout from [AmdDocument]: English operative text in a serif face, headings also in the chosen language, signature and ID boxes blank for handwriting. */
    fun livingWill(file: File, a: AmdRecord, lang: CareLang, today: LocalDate) {
        val hash = AmdLifecycle.contentHash(a); val offset = 30f; val width = WIDTH - 2 * offset
        fun serif(size: Float, bold: Boolean = false, italic: Boolean = false, color: Int = android.graphics.Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, when { bold && italic -> android.graphics.Typeface.BOLD_ITALIC; bold -> android.graphics.Typeface.BOLD; italic -> android.graphics.Typeface.ITALIC; else -> android.graphics.Typeface.NORMAL }) }
        val grey = android.graphics.Color.rgb(90, 90, 90); val d = Doc()
        AmdDocument.blocks(a, lang, hash, today).forEach { b -> when (b) {
            is AmdBlock.Title -> { val t = serif(15f, true); d.blocks += Line(b.text, t, 24f, offset); b.translated?.let { d.blocks += Line(it, serif(11f, color = grey), 16f, offset) }; d.space(6f) }
            is AmdBlock.Heading -> { d.space(6f); val t = serif(12.5f, true); wrap(b.text, t, width).forEach { d.blocks += Line(it, t, 18f, offset) }; b.translated?.let { wrap(it, serif(10f, color = grey), width).forEach { l -> d.blocks += Line(l, serif(10f, color = grey), 15f, offset) } }; d.space(4f) }
            is AmdBlock.Para -> { val t = serif(b.size, b.bold, b.italic); val inner = b.indent + if (b.prefix.isNotEmpty()) 14f else 0f
                wrap(b.text, t, width - inner).forEachIndexed { i, l -> d.blocks += Line(l, t, b.size + 4.5f, offset + inner, if (i == 0) b.prefix else "", offset + b.indent) }; d.space(b.after) }
            is AmdBlock.Table -> { val body = serif(10f); val head = serif(10f, true); fun row(cells: List<String>, p: Paint, min: Float) { d.blocks += TableRow(cells.mapIndexed { i, c -> wrap(c, p, b.widths[i] - 8f) }, b.widths, p, offset, min) }
                b.header?.let { row(it, head, 0f) }; b.rows.forEach { row(it, body, b.minRow) }; d.space(10f) }
            is AmdBlock.BlankLine -> d.blocks += Line(b.label + " " + b.value.ifBlank { "______________________________" }, serif(10.5f), 20f, offset)
            is AmdBlock.Space -> d.space(b.height)
            AmdBlock.Rule -> d.blocks += Rule(offset, 140f)
            AmdBlock.PageBreak -> d.blocks += PageBreak
            is AmdBlock.Stamp -> d.blocks += Stamp(offset, b.width, b.height)
        } }
        val small = serif(8f, color = grey); val name = a.content.executant.name.ifBlank { "—" }; val version = a.version; val form = a.formVersion.substringBefore(" (")
        render(file, d) { c, page, pages ->
            c.drawText("Advance Medical Directive of $name · Version $version", LEFT + offset, 30f, small)
            c.drawText(form, LEFT + WIDTH - offset - small.measureText(form), 30f, small)
            val label = "Page $page of $pages"; val nine = serif(9f)
            c.drawText(label, (W - nine.measureText(label)) / 2, 806f, nine)
            c.drawText("Content SHA-256 ${hash.take(16)}…", LEFT + offset, 822f, small)
            val initials = "Executor’s initials: ________"
            c.drawText(initials, LEFT + WIDTH - offset - small.measureText(initials), 822f, small)
        }
    }
}
