package app.sawatt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.sawatt.ui.theme.SawattColors

// Grouped lists, as in settings screens: a small header, then rows on one rounded card.

@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = SawattColors.TextSecondary,
        modifier = modifier.padding(start = 36.dp, end = 36.dp, top = 24.dp, bottom = 6.dp),
    )
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SawattColors.Surface),
        content = content,
    )
}

@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 14.dp), thickness = 1.dp, color = SawattColors.Hairline)
}

/** One row of a [Group]; clickable rows get a chevron unless they have their own [trailing] content. */
@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SawattColors.TextSecondary) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SawattColors.TextSecondary) }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SawattColors.Chevron,
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    GroupRow(title, subtitle = subtitle, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SawattColors.Accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = SawattColors.Muted,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** Coloured rounded square holding a row's icon. */
@Composable
fun IconTile(icon: ImageVector, color: Color, tint: Color = SawattColors.OnAccent) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}
