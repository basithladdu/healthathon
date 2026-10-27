package `in`.wedevit.care

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** TalkBack helpers shared by every screen, so screen titles, cards and status lines behave the same way everywhere. */

/** Screen and section titles: TalkBack users can jump between headings. */
fun Modifier.a11yHeading(): Modifier = semantics { heading() }

/** A card read as one item ("title, date, detail"); its own buttons and fields stay separately focusable. */
fun Modifier.a11yCard(): Modifier = semantics(mergeDescendants = true) {}

/** Saved / error / loading lines that appear or change without focus: TalkBack reads them out politely. */
fun Modifier.announce(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
