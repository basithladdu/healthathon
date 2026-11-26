package `in`.wedevit.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The patient's step-by-step note reader: the pieces around one step. Only the patient's reader uses these; the doctor's
 * screens keep drawing the form their own way. Look: white cards on a soft tinted ground, no hairline, a soft shadow.
 */
private val StepCardShape = RoundedCornerShape(20.dp)
private val StepPill = RoundedCornerShape(999.dp)

/** A white rounded card with a soft warm shadow and no border. */
@Composable internal fun NoteStepCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().shadow(3.dp, StepCardShape, ambientColor = SaharaColors.Shadow.copy(alpha = .25f), spotColor = SaharaColors.Shadow.copy(alpha = .25f))
        .background(Color.White, StepCardShape).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

/** "Step 2 of 6", a segmented bar and the step's title, fixed above the scrolling step. */
@Composable internal fun NoteStepProgress(step: Int, count: Int, title: String, modifier: Modifier = Modifier) { val t = careText()
    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp).testTag("note-step-progress"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Step ${step + 1} of $count", "चरण ${step + 1} / $count"), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.InkFaint)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { i -> Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (i <= step) SaharaColors.Primary else SaharaColors.Line)) }
        }
        Text(title, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = SaharaColors.Ink)
    }
}

/**
 * The bar fixed at the bottom of the sheet: Back (outlined pill, off on step 1) and Next (filled pill). On the last step
 * [finalAction] takes Next's place (the Save button), given the modifier that sizes it.
 */
@Composable internal fun NoteStepBar(first: Boolean, last: Boolean, onBack: () -> Unit, onNext: () -> Unit, finalAction: (@Composable (Modifier) -> Unit)?) { val t = careText()
    Surface(color = Color.White, shadowElevation = 10.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, enabled = !first, shape = StepPill, modifier = Modifier.weight(if (last && finalAction != null) .8f else 1f).heightIn(min = 52.dp).testTag("note-step-back")) {
                Text(t("Back", "वापस"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            val size = Modifier.weight(if (last && finalAction != null) 1.4f else 1f).heightIn(min = 52.dp)
            if (!last) Button(onClick = onNext, shape = StepPill, modifier = size.testTag("note-step-next"), colors = ButtonDefaults.buttonColors(containerColor = SaharaColors.Primary, contentColor = SaharaColors.OnPrimary)) {
                Text(t("Next", "आगे"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            } else finalAction?.invoke(size)
        }
    }
}

/**
 * One section of the form (owner, 26 Nov: "make it a good structure"): each numbered part (3.1, 3.2…) is its own block with a
 * number badge and title, questions in ink, "Not discussed" as a quiet grey pill, options inside a framed list with sub-options
 * indented under a line, and tables drawn as real tables.
 */
@Composable internal fun PatientNoteSectionCard(section: GocFormSection, hindi: Boolean) {
    Column(Modifier.fillMaxWidth().testTag("goc-form-section-${section.id}"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        section.groups.forEach { g ->
            NoteStepCard {
                if (g.title.en.isNotBlank()) NoteGroupTitle(g.title.pick(hindi))
                g.rows.forEachIndexed { i, row -> if (i > 0) HorizontalDivider(color = SaharaColors.Line); NoteStepRow(row, hindi) }
                g.table?.let { NoteStepTable(it, hindi) }
            }
        }
    }
}

/** "3.1 Home care (if relevant)" → a green number badge and the title beside it. */
@Composable private fun NoteGroupTitle(title: String) {
    val number = Regex("^(\\d+(\\.\\d+)*)\\s+").find(title)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (number != null) Box(Modifier.background(SaharaColors.PrimaryWash, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(number.groupValues[1], fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SaharaColors.PrimaryPressed)
        }
        Text(if (number != null) title.removeRange(number.range) else title, Modifier.weight(1f).a11yHeading(), color = SaharaColors.PrimaryPressed, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp)
    }
}

/** The "Not discussed" answer as a small grey pill, so it reads as a status rather than as an answer. */
@Composable private fun NoteOpenPill(text: String) {
    Box(Modifier.background(SaharaColors.Well, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, fontSize = 14.sp, lineHeight = 19.sp, fontStyle = FontStyle.Italic, color = SaharaColors.InkSub)
    }
}

@Composable private fun NoteStepRow(row: GocFormRow, hindi: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (row.label.en.isNotBlank()) Text(row.label.pick(hindi), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = SaharaColors.Ink)
        if (row.value.en.isNotBlank()) {
            if (row.open) NoteOpenPill(row.value.pick(hindi))
            else Text(row.value.pick(hindi), fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink)
        }
        if (row.ticks.isNotEmpty()) Column(Modifier.fillMaxWidth().border(1.dp, SaharaColors.Line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            row.ticks.forEach { tick -> NoteStepTick(tick.on, tick.text.pick(hindi) + if (tick.detail.isBlank()) "" else " " + tick.detail) }
        }
    }
}

/** A drawn tick box: filled green with a check when ticked. The text takes the full remaining width, so it wraps between words. */
@Composable private fun NoteStepTick(on: Boolean, text: String, compact: Boolean = false) { val t = careText()
    val nested = text.startsWith("    ")
    // Sub-options sit under a thin green line, indented, so "ONLY FOR THE FOLLOWING" reads as a parent with its list.
    Row(Modifier.fillMaxWidth().then(if (nested) Modifier.padding(start = 10.dp).drawBehind {
            drawLine(SaharaColors.PrimaryWash, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = 3.dp.toPx())
        }.padding(start = 14.dp) else Modifier)
        .semantics(mergeDescendants = true) { stateDescription = if (on) t("Ticked", "निशान लगा") else t("Not ticked", "निशान नहीं") },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
        Box(Modifier.padding(top = 2.dp).size(18.dp).background(if (on) SaharaColors.Primary else Color.White, RoundedCornerShape(5.dp))
            .then(if (on) Modifier else Modifier.border(1.5.dp, SaharaColors.InkFaint, RoundedCornerShape(5.dp))), contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        if (text.isNotBlank()) Text(text.trimStart(), Modifier.weight(1f), fontSize = if (compact) 13.sp else 15.sp, lineHeight = if (compact) 18.sp else 21.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) SaharaColors.Ink else SaharaColors.InkFaint)
    }
}

/** The lines of one table cell: lines that start with a box are tick boxes, "Not discussed" is muted italic. */
@Composable private fun NoteStepCell(cell: Bi, hindi: Boolean, compact: Boolean = false) {
    val notDiscussed = GocForm.NOT_DISCUSSED.pick(hindi)
    cell.pick(hindi).lines().filter { it.isNotBlank() }.forEach { line ->
        if (line.startsWith(GocForm.TICK_ON) || line.startsWith(GocForm.TICK_OFF)) NoteStepTick(line.startsWith(GocForm.TICK_ON), line.drop(1).trim(), compact)
        else { val open = line == notDiscussed
            Text(line, fontSize = if (compact) 14.sp else 16.sp, lineHeight = if (compact) 19.sp else 22.sp, fontWeight = if (open) FontWeight.Normal else FontWeight.Medium, fontStyle = if (open) FontStyle.Italic else FontStyle.Normal, color = if (open) SaharaColors.InkFaint else SaharaColors.Ink)
        }
    }
}

@Composable private fun NoteRealTable(table: GocFormTable, hindi: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    val columns = table.head.size
    // First column narrower; value columns share the rest.
    fun weightOf(i: Int) = if (i == 0) (if (columns == 3) .30f else .40f) else (if (columns == 3) .35f else .60f)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, SaharaColors.Line, shape).testTag("goc-note-table")) {
        Row(Modifier.fillMaxWidth().background(SaharaColors.PrimaryWash)) {
            table.head.forEachIndexed { i, h ->
                Text(h.pick(hindi).uppercase(), Modifier.weight(weightOf(i)).padding(horizontal = 8.dp, vertical = 10.dp), fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold, color = SaharaColors.PrimaryPressed, letterSpacing = .5.sp)
            }
        }
        table.rows.forEach { cells ->
            HorizontalDivider(color = SaharaColors.Line)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (i in 0 until columns) {
                    val cell = cells.getOrNull(i) ?: Bi.plain("")
                    Column(Modifier.weight(weightOf(i)).fillMaxHeight().background(if (i == 0) SaharaColors.GroundWarm else Color.White).padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (i == 0) Text(cell.pick(hindi), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink)
                        else NoteStepCell(cell, hindi, compact = true)
                    }
                    if (i < columns - 1) VerticalDivider(color = SaharaColors.Line)
                }
            }
        }
    }
}

@Composable private fun NoteMiniCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(SaharaColors.GroundWarm, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!title.isNullOrBlank()) Text(title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = SaharaColors.PrimaryPressed)
        content()
    }
}

@Composable private fun NoteMiniField(label: String?, cell: Bi, hindi: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!label.isNullOrBlank()) Text(label, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = SaharaColors.InkFaint)
        NoteStepCell(cell, hindi)
    }
}

/**
 * A table of the form on a phone, as stacked mini-cards (label: value rows) instead of narrow columns. The decision-maker table
 * (blank first heading, then PRIMARY and SECONDARY) becomes one card per person; other tables become one card per row.
 */
@Composable private fun NoteStepTable(table: GocFormTable, hindi: Boolean) { val t = careText()
    val head = table.head
    // The decision-maker table (blank corner, then PRIMARY and SECONDARY) is a real table again (owner, 26 Nov), drawn to fit a
    // phone: a tinted heading row, the row names in a narrow shaded first column, smaller text so words wrap between words.
    if (head.size in 2..3 && head[0].en.isBlank()) { NoteRealTable(table, hindi); return }
    // The signature table (patient, family, treating doctor, witness): the same signature boxes as the doctor's own form, so the
    // doctor's row shows with its name, registration and signing box (✓ and name once signed), not just the role (owner, 26 Nov).
    if (head.any { it.en in GocDocSignatureHeads }) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { GocDocSignatures(table, hindi) }; return }
    val declaration = head.firstOrNull()?.pick(hindi)?.let { it.startsWith(GocForm.TICK_ON) || it.startsWith(GocForm.TICK_OFF) } == true
    if (declaration) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { NoteStepCell(head.first(), hindi) }
    val signatureHeads = setOf("Signature / thumb impression", "Signature", "SIGNATURE")
    table.rows.forEach { cells ->
        NoteMiniCard(null) {
            cells.forEachIndexed { i, cell ->
                if (i == 0) {
                    val first = cell.pick(hindi)
                    if (first.isNotBlank()) {
                        if (first.lines().any { it.startsWith(GocForm.TICK_ON) || it.startsWith(GocForm.TICK_OFF) }) NoteStepCell(cell, hindi)
                        else Text(first, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = SaharaColors.Ink)
                    }
                } else {
                    val h = head.getOrNull(i)
                    val label = h?.pick(hindi)?.takeIf { it.isNotBlank() }
                    if (cell.pick(hindi).isBlank()) { if (h != null && h.en in signatureHeads) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!label.isNullOrBlank()) Text(label, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = SaharaColors.InkFaint)
                        Text(t("Not signed yet", "अभी हस्ताक्षर नहीं"), fontSize = 16.sp, lineHeight = 22.sp, fontStyle = FontStyle.Italic, color = SaharaColors.InkFaint)
                    } }
                    else NoteMiniField(label, cell, hindi)
                }
            }
        }
    }
}
