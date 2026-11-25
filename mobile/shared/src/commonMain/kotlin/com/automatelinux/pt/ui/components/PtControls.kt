package com.automatelinux.pt.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The app's own controls. Material's defaults are drawn for a light, tinted-surface
 * world: outlined chips, a notched outline around every text field, a purple-grey for
 * "selected". On this dark sheet they made every screen a wall of identical hollow
 * boxes. These say the same things with one material and one rule:
 *
 *   a thing you can pick sits on a raised ground with no outline;
 *   the thing that IS picked is the only one in the accent colour.
 */

/** Ground and ink of the picked state, shared by every control so it is one language. */
@Composable
private fun pickedGround(): Color = MaterialTheme.colorScheme.primaryContainer

@Composable
private fun pickedInk(): Color = MaterialTheme.colorScheme.onPrimaryContainer

@Composable
private fun pickedEdge(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

/**
 * A choice that stands alone or among choices that may all be on at once. For "one of
 * these" use [PtSegmented]: a row of chips never says that picking one drops another.
 *
 * Takes the same slots Material's FilterChip did, so call sites kept their content.
 */
@Composable
fun PtChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.small
    val ground by animateColorAsState(
        if (selected) pickedGround() else MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val ink by animateColorAsState(
        if (selected) pickedInk() else MaterialTheme.colorScheme.onSurface
    )
    val edge by animateColorAsState(if (selected) pickedEdge() else Color.Transparent)
    Row(
        modifier = modifier
            // The breathing room Material bought with an invisible 48dp touch box,
            // stated here instead so rows of chips keep their rhythm.
            .padding(vertical = 4.dp)
            .defaultMinSize(minHeight = 38.dp)
            .clip(shape)
            .background(ground)
            .border(1.dp, edge, shape)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() }
            .padding(start = if (leadingIcon != null) 10.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) ink else ink.copy(alpha = 0.4f)) {
            if (leadingIcon != null) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                Spacer(Modifier.width(6.dp))
            }
            ProvideTextStyle(
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            ) { label() }
        }
    }
}

/** An action dressed as a quiet pill: something you do, not something you leave on. */
@Composable
fun PtActionChip(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .defaultMinSize(minHeight = 38.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

data class PtSegment(
    val label: String,
    val icon: ImageVector? = null,
    /** Read by TalkBack and tests when the label alone is a number or a glyph. */
    val description: String? = null,
)

/**
 * One of several, as a track with a thumb that slides to the choice.
 *
 * Segments are as wide as their own label, not equal: "Route planner" beside "Lines"
 * in equal cells either truncates the first or strands the second. Spare width is
 * shared out evenly when [expand] is set; when there is not enough, the track scrolls
 * sideways rather than squeezing a label onto two lines.
 *
 * [selectedIndex] may be -1: nothing picked, no thumb.
 */
@Composable
fun PtSegmented(
    segments: List<PtSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    expand: Boolean = true,
    height: Dp = 42.dp,
    /** Smaller labels and tighter cells, for a track of four or more wordy choices. */
    dense: Boolean = false,
) {
    val trackShape = RoundedCornerShape(14.dp)
    val thumbRadius = 11.dp
    val inset = 3.dp
    val thumbGround = pickedGround()
    val thumbEdge = pickedEdge()

    // Absolute x and width of every segment, written by the layout pass below.
    var bounds by remember { mutableStateOf(listOf<Pair<Int, Int>>()) }
    val target = bounds.getOrNull(selectedIndex)
    // The first placement must not slide in from the corner.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(target != null) { if (target != null) settled = true }
    val motion = if (settled) {
        spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    } else {
        snap()
    }
    val thumbX by animateFloatAsState(target?.first?.toFloat() ?: 0f, motion)
    val thumbW by animateFloatAsState(target?.second?.toFloat() ?: 0f, motion)
    val thumbAlpha by animateFloatAsState(if (target != null) 1f else 0f)

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(trackShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        Layout(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .drawBehind {
                    if (thumbAlpha > 0f && thumbW > 0f) {
                        val pad = inset.toPx()
                        val r = CornerRadius(thumbRadius.toPx())
                        val topLeft = Offset(thumbX, pad)
                        val size = Size(thumbW, this.size.height - pad * 2)
                        drawRoundRect(thumbGround.copy(alpha = thumbAlpha), topLeft, size, r)
                        drawRoundRect(
                            thumbEdge.copy(alpha = thumbEdge.alpha * thumbAlpha),
                            topLeft, size, r,
                            style = Stroke(1.dp.toPx())
                        )
                    }
                },
            content = {
                segments.forEachIndexed { index, segment ->
                    val picked = index == selectedIndex
                    val ink by animateColorAsState(
                        if (picked) pickedInk() else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(thumbRadius))
                            .selectable(
                                selected = picked,
                                role = Role.Tab,
                                onClick = { onSelect(index) }
                            )
                            .padding(horizontal = if (dense) 9.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (segment.icon != null) {
                            Icon(
                                segment.icon,
                                contentDescription = segment.description,
                                tint = ink,
                                modifier = Modifier.size(17.dp)
                            )
                            if (segment.label.isNotEmpty()) Spacer(Modifier.width(5.dp))
                        }
                        if (segment.label.isNotEmpty()) {
                            Text(
                                segment.label,
                                style = if (dense) MaterialTheme.typography.labelMedium
                                    else MaterialTheme.typography.labelLarge,
                                fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
                                color = ink,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val pad = inset.roundToPx()
            val h = constraints.maxHeight
            val cellH = (h - pad * 2).coerceAtLeast(0)
            val natural = measurables.map { it.maxIntrinsicWidth(cellH) }
            val room = available - pad * 2
            val spare = if (expand && measurables.isNotEmpty() && natural.sum() < room) {
                (room - natural.sum()) / measurables.size
            } else {
                0
            }
            val placeables = measurables.mapIndexed { i, m ->
                m.measure(Constraints.fixed(natural[i] + spare, cellH))
            }
            val contentW = placeables.sumOf { it.width } + pad * 2
            val width = if (expand) maxOf(contentW, available) else contentW
            val rtl = layoutDirection == LayoutDirection.Rtl
            var x = pad
            val placed = placeables.map { p ->
                val absolute = if (rtl) width - x - p.width else x
                x += p.width
                absolute to p.width
            }
            if (placed != bounds) bounds = placed
            layout(width, h) {
                placeables.forEachIndexed { i, p -> p.place(placed[i].first, pad) }
            }
        }
    }
}

/** On/off for one fact, with its name beside it: the whole row is the control. */
@Composable
fun PtSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(12.dp))
        PtSwitch(checked)
    }
}

/** The switch itself, drawn — it takes no clicks; the row or box around it does. */
@Composable
fun PtSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val track by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest
    )
    val knob by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
    val travel by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
    )
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(CircleShape)
            .background(track)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = travel)
                .size(20.dp)
                .clip(CircleShape)
                .background(knob)
        )
    }
}

/**
 * The text field: a raised ground, the label inside it, no outline. The previous
 * outlined field drew a box with a notch for the label, and two of them stacked with a
 * row of links between read as a form to fill in rather than two places to name.
 */
@Composable
fun PtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHighest,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            disabledContainerColor = scheme.surfaceContainer,
            errorContainerColor = scheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedLabelColor = scheme.primary,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            cursorColor = scheme.primary,
            focusedLeadingIconColor = scheme.onSurfaceVariant,
            unfocusedLeadingIconColor = scheme.onSurfaceVariant,
            focusedTrailingIconColor = scheme.onSurfaceVariant,
            unfocusedTrailingIconColor = scheme.onSurfaceVariant,
        )
    )
}

/** The one thing a screen is for. Full width, tall enough for a thumb on a moving bus. */
@Composable
fun PtPrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** A small word on a tinted ground: a fare, "direct", "tomorrow". Never a control. */
@Composable
fun PtTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ground: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    bold: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier
            .background(ground, RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = color,
        maxLines = 1,
        softWrap = false
    )
}

/**
 * The map's buttons as one object: a single capsule with a cell per button, instead
 * of five separate squares each casting its own shadow on the map. A cell that is on
 * fills with the accent; one that is off is plain ink — colour means the layer is
 * showing, not "this is a button".
 */
@Composable
fun PtMapRail(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(4.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
fun PtMapRailButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val ground by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else Color.Transparent
    )
    val ink by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    )
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ground)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = ink, modifier = Modifier.size(22.dp))
    }
}

/** The ground every card in the sheet stands on, thinned by the user's opacity setting. */
@Composable
fun ptCardGround(opacity: Float = 1f): Color =
    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = opacity.coerceIn(0f, 1f))

/** A section's name: small, quiet, above the thing it names. */
@Composable
fun PtSectionLabel(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
