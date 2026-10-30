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
    private data class Line(val text: String, val paint: Paint, override val height: Float) : Block
    private data class Qr(val url: String, val side: Float) : Block { override val height get() = side + 8f }
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

    private fun render(file: File, doc: Doc, footer: (Int, Int) -> String) {
        // Paginate first, so each footer can say "page x of y".
        val pages = mutableListOf(mutableListOf<Block>()); var y = 48f
        doc.blocks.forEach { b ->
            if (b === PageBreak) { pages += mutableListOf<Block>(); y = 48f; return@forEach }
            if (y + b.height > BOTTOM && pages.last().isNotEmpty()) { pages += mutableListOf<Block>(); y = 48f }
            pages.last() += b; y += b.height
        }
        file.parentFile?.mkdirs()
        val pdf = PdfDocument(); val small = paint(8f); val black = Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }
        try {
            pages.forEachIndexed { i, blocks ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create()); val c = page.canvas; var top = 48f
                blocks.forEach { b ->
                    when (b) {
                        is Line -> { c.drawText(b.text, LEFT, top + b.paint.textSize, b.paint); top += b.height }
                        is Box -> { c.drawRect(LEFT - 6f, top, LEFT + WIDTH + 6f, top + b.height - 4f, Paint().apply { color = b.fill; style = Paint.Style.FILL })
                            var ly = top + 8f; b.lines.forEach { l -> c.drawText(l.text, LEFT + 4f, ly + l.paint.textSize, l.paint); ly += l.height }; top += b.height }
                        is Qr -> { val m = Encoder.encode(b.url, ErrorCorrectionLevel.Q, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")).matrix; val quiet = 4; val cell = b.side / (m.width + 2 * quiet)
                            for (row in 0 until m.height) for (col in 0 until m.width) if (m.get(col, row).toInt() == 1) c.drawRect(LEFT + (col + quiet) * cell, top + (row + quiet) * cell, LEFT + (col + quiet + 1) * cell, top + (row + quiet + 1) * cell, black)
                            top += b.height }
                        PageBreak -> Unit
                    }
                }
                wrap(footer(i + 1, pages.size), small).forEachIndexed { k, l -> c.drawText(l, LEFT, 808f + k * 11f, small) }
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
    }

    fun plan(file: File, r: EctprRecord, interp: EdInterpretation, amd: Bi, cardUrl: String?, hindi: Boolean, today: LocalDate) {
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
            field(Bi("Understanding confirmed", "समझ की पुष्टि"), if (r.prognosticUnderstanding.confirmed) "${r.prognosticUnderstanding.confirmedBy} ${r.prognosticUnderstanding.confirmedOn}".trim() else "")
            d.space(8f); d.text(p(Bi("Signatures", "हस्ताक्षर")), h2, 18f)
            r.signatures.forEach { s -> d.text("${s.role.name.replace('_', ' ').lowercase()}: ${s.name}${s.registrationNo?.let { " (reg. $it)" } ?: ""}${if (s.thumbImpression == true) " — thumb impression" else ""} · ${s.signedAt.take(16).replace('T', ' ')}", body) }
            if (cardUrl != null) {
                d.space(16f); d.text(p(Bi("Wallet card — cut out and keep with the patient", "वॉलेट कार्ड — काटकर मरीज़ के पास रखें")), h2, 20f)
                WalletCard.lines(r).forEach { (label, value) -> d.text(p(label) + ": " + p(value), body, 14f) }
                d.box(alert, p(WalletCard.TREAT_LINE) to white)
                d.blocks += Qr(cardUrl, 150f)
                d.text(p(Bi("The QR is a random link with no personal details; it is switched off if the plan is replaced or withdrawn.", "QR एक रैंडम लिंक है जिसमें कोई निजी जानकारी नहीं; योजना बदलने या वापस लेने पर यह बंद हो जाता है।")), paint(9f))
            }
        }
        render(file, d) { page, pages -> EctprRules.pageFooter(r, today, page, pages) }
    }

    fun livingWill(file: File, a: AmdRecord, hindi: Boolean, today: LocalDate) {
        fun p(b: Bi) = b.pick(hindi)
        val h1 = paint(16f, true); val h2 = paint(12.5f, true); val body = paint(10.5f); val bold = paint(10.5f, true)
        val pale = android.graphics.Color.rgb(251, 232, 200)
        val d = Doc()
        d.text(p(Bi("ADVANCE MEDICAL DIRECTIVE (LIVING WILL)", "अग्रिम चिकित्सा निर्देश (लिविंग विल)")), h1, 22f)
        d.text(p(Bi("Under Common Cause v. Union of India (2018), as modified on 24 January 2023", "कॉमन कॉज़ बनाम भारत संघ (2018), 24 जनवरी 2023 के संशोधन सहित")), body)
        d.space()
        d.text(p(Bi("I, ", "मैं, ")) + a.maker.name + listOfNotNull(a.maker.dob.ifBlank { null }?.let { p(Bi(", born ", ", जन्मतिथि ")) + it }, a.maker.address.ifBlank { null }?.let { p(Bi(", of ", ", पता ")) + it },
            a.maker.idType?.let { p(PlanText.idType(it)) + " XXXX " + a.maker.idLast4.orEmpty() }?.let { ", $it" }).joinToString("") + ",", bold)
        d.text(p(Bi("being an adult of sound mind, make this directive voluntarily and without pressure from anyone, understanding its consequences.", "पूरे होश वाला/वाली वयस्क होकर, बिना किसी दबाव के, इसके नतीजे समझकर, यह निर्देश अपनी मर्ज़ी से देता/देती हूँ।")), body)
        d.space(); d.text(p(Bi("1. When this directive applies", "1. यह निर्देश कब लागू होगा")), h2, 18f)
        a.answers.situations.forEach { d.text("• " + p(AmdText.situation(it)), body) }
        d.space(); d.text(p(Bi("2. Treatments I refuse in those situations", "2. उन स्थितियों में मैं ये इलाज मना करता/करती हूँ")), h2, 18f)
        a.answers.refusedTreatments.forEach { d.text("• " + p(AmdText.treatment(it)), body) }
        if (AmdTreatment.TUBE_FEEDING in a.answers.refusedTreatments) d.text(p(AmdText.TUBE_NOTE), body)
        d.box(pale, p(AmdText.COMFORT) to bold)
        d.space(); d.text(p(Bi("3. Other wishes", "3. दूसरी इच्छाएँ")), h2, 18f)
        a.answers.placeOfCare?.let { d.text(p(Bi("Place of care: ", "देखभाल की जगह: ")) + p(AmdText.place(it)), body) }
        if (a.answers.religiousWishes.isNotBlank()) d.text(p(Bi("Religious or spiritual wishes: ", "धार्मिक या आध्यात्मिक इच्छाएँ: ")) + a.answers.religiousWishes, body)
        if (a.answers.otherWishes.isNotBlank()) d.text(p(Bi("Other: ", "अन्य: ")) + a.answers.otherWishes, body)
        if (a.answers.organDonation == true) d.text(p(Bi("I wish to donate my organs or body if possible.", "हो सके तो मैं अपने अंग या शरीर दान करना चाहता/चाहती हूँ।")), body)
        d.space(); d.text(p(Bi("4. People who will decide for me (in order)", "4. मेरे लिए फ़ैसला लेने वाले (क्रम से)")), h2, 18f)
        a.dhrs.filter { it.name.isNotBlank() }.forEachIndexed { i, x -> d.text("${i + 1}. ${x.name} (${x.relation}) · ${x.phone}${x.idType?.let { " · " + p(PlanText.idType(it)) + " XXXX " + x.idLast4.orEmpty() } ?: ""}${x.address.ifBlank { null }?.let { " · $it" } ?: ""}", body) }
        d.text(p(Bi("They are to be consulted, and their consent taken, before the medical boards decide.", "मेडिकल बोर्ड के फ़ैसले से पहले इनसे बात कर इनकी सहमति ली जाए।")), body)
        d.space(); d.text(p(Bi("I may revoke or change this directive at any time, in writing, in the same way.", "मैं इसे कभी भी, उसी तरह लिखकर, रद्द या बदल सकता/सकती हूँ।")), body)
        d.space(14f)
        val blank = "____________________________"
        d.text(p(Bi("Signature or thumb impression of the maker: ", "निर्देश देने वाले का हस्ताक्षर या अँगूठे का निशान: ")) + blank, bold, 22f)
        d.text(p(Bi("Date: ", "तारीख़: ")) + "____________   " + p(Bi("Place: ", "स्थान: ")) + "____________", body, 22f)
        listOf(1, 2).forEach { n ->
            d.space(6f); d.text(p(Bi("Witness $n — signed in my presence", "गवाह $n — मेरे सामने हस्ताक्षर किए")), h2, 18f)
            d.text(p(Bi("Name: ", "नाम: ")) + blank + "  " + p(Bi("Signature: ", "हस्ताक्षर: ")) + "________________", body, 20f)
            d.text(p(Bi("Address: ", "पता: ")) + blank + blank, body, 20f)
        }
        d.space(6f); d.text(p(Bi("Attestation by a Notary or Gazetted Officer", "नोटरी या राजपत्रित अधिकारी द्वारा सत्यापन")), h2, 18f)
        d.text(p(Bi("Signed before me by the maker, in the presence of the two witnesses above, voluntarily and with full understanding.", "निर्देश देने वाले ने ऊपर के दो गवाहों के सामने, अपनी मर्ज़ी से और पूरी समझ के साथ, मेरे सामने हस्ताक्षर किए।")), body)
        d.text(p(Bi("Name: ", "नाम: ")) + blank + "  " + p(Bi("Registration / designation: ", "रजिस्ट्रेशन / पद: ")) + "______________", body, 20f)
        d.text(p(Bi("Signature and seal: ", "हस्ताक्षर और मुहर: ")) + blank + "  " + p(Bi("Date: ", "तारीख़: ")) + "__________", body, 20f)
        d.space(8f)
        d.text(p(Bi("Copies to: each person named above · family doctor · local custodian of the district (when appointed). It may also be linked to digital health records.", "कॉपी: ऊपर नामित हर व्यक्ति · फ़ैमिली डॉक्टर · ज़िले के कस्टोडियन (नियुक्त होने पर)। इसे डिजिटल स्वास्थ्य रिकॉर्ड से भी जोड़ा जा सकता है।")), paint(9f))
        d.text(p(AmdText.CAVEAT), paint(9f))
        render(file, d) { page, pages -> "${a.maker.name} · living will v${a.version} · ${a.formVersion} · printed $today · page $page of $pages · not valid until signed, witnessed and attested" }
    }
}
