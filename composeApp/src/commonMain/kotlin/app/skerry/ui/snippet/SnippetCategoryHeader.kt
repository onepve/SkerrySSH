package app.skerry.ui.snippet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.design.Sym
import app.skerry.ui.design.Txt
import app.skerry.ui.theme.Skerry

/**
 * Collapsible category header in the snippet library ("All" view), modeled on the host folder
 * headers in the sidebar: a chevron (expand_more / chevron_right) + tag icon + chip label
 * (`#tag`, or the localized "Uncategorized" label for the synthetic bucket) + count badge. The
 * whole header is the click target (no dragging here, unlike host folders). Shared by the desktop
 * and mobile libraries.
 */
@Composable
internal fun SnippetCategoryHeader(
    category: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Sym(if (collapsed) "chevron_right" else "expand_more", size = 16.sp, color = Skerry.colors.faint)
        Sym("label", size = 14.sp, color = Skerry.colors.cyanBright)
        Txt(
            snippetChipLabel(category),
            color = Skerry.colors.dim,
            size = 12.5.sp,
            weight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Skerry.colors.card).padding(horizontal = 6.dp, vertical = 1.dp)) {
            Txt(count.toString(), color = Skerry.colors.faint, size = 10.sp)
        }
    }
}
