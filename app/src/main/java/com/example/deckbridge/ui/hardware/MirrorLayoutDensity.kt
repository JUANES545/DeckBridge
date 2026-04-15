package com.example.deckbridge.ui.hardware

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual density for [HardwareMirrorPanel].
 * - [Comfortable]: default portrait-oriented spacing.
 * - [LandscapeCompact]: legacy uniform compact knobs (unused by home; kept for API stability).
 * - [LandscapeSidebar]: landscape + side rail — shorter 3×3 (wide tiles), primary knob larger, tighter chrome.
 */
enum class MirrorLayoutDensity {
    Comfortable,
    LandscapeCompact,
    LandscapeSidebar,
    /** Home dashboard — hero grid, generous gaps. */
    DashboardPortrait,
    /** Home dashboard landscape — wide tiles + prominent knobs column. */
    DashboardLandscape,
}

internal data class MirrorLayoutTokens(
    val cardCorner: Dp,
    val cardElevation: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val sectionGap: Dp,
    val knobPadRowGap: Dp,
    val padCellGap: Dp,
    val knobColumnWeight: Float,
    /** Width / height for each pad cell; > 1 yields wider-than-tall tiles (shorter grid). */
    val padCellAspectRatio: Float,
    val knobOuterBox: Dp,
    val knobInner: Dp,
    val knobDot: Dp,
    val knobArcStroke: Dp,
    val knobBorder: Dp,
    val knobStackSpacing: Dp,
    val useDistinctPrimaryKnob: Boolean,
    val knobPrimaryOuter: Dp,
    val knobPrimaryInner: Dp,
    val knobPrimaryDot: Dp,
    val knobPrimaryArcStroke: Dp,
    val knobPrimaryBorder: Dp,
    val padCorner: Dp,
    val padPaddingH: Dp,
    val padPaddingV: Dp,
    val padColumnGap: Dp,
    val padMediaCorner: Dp,
    val padBorderActive: Dp,
    val padBorderIdle: Dp,
    val statusIsDenseTypography: Boolean,
    val headerExtraDense: Boolean,
    val padTitleMinimal: Boolean,
    /** Larger emoji/glyph in the pad media area (landscape sidebar). */
    val padGlyphProminent: Boolean,
    /**
     * When > 0, the knob column uses [Arrangement.spacedBy] between knobs instead of
     * [Arrangement.SpaceEvenly] + fill height (dashboard home vertical centering).
     */
    val knobColumnInterItemSpacing: Dp,
)

/** Widest knob column budget for dashboard layout + [dashboardPadCellSideDp] (must stay in sync). */
internal fun MirrorLayoutTokens.dashboardKnobTrackWidth(): Dp =
    maxOf(knobPrimaryOuter, knobOuterBox) + 22.dp

internal fun MirrorLayoutDensity.toTokens(): MirrorLayoutTokens = when (this) {
    MirrorLayoutDensity.Comfortable -> MirrorLayoutTokens(
        cardCorner = 28.dp,
        cardElevation = 6.dp,
        paddingHorizontal = 18.dp,
        paddingVertical = 20.dp,
        sectionGap = 14.dp,
        knobPadRowGap = 14.dp,
        padCellGap = 10.dp,
        knobColumnWeight = 0.26f,
        padCellAspectRatio = 1f,
        knobOuterBox = 76.dp,
        knobInner = 58.dp,
        knobDot = 14.dp,
        knobArcStroke = 3.dp,
        knobBorder = 2.5.dp,
        knobStackSpacing = 6.dp,
        useDistinctPrimaryKnob = false,
        knobPrimaryOuter = 76.dp,
        knobPrimaryInner = 58.dp,
        knobPrimaryDot = 14.dp,
        knobPrimaryArcStroke = 3.dp,
        knobPrimaryBorder = 2.5.dp,
        padCorner = 16.dp,
        padPaddingH = 6.dp,
        padPaddingV = 5.dp,
        padColumnGap = 4.dp,
        padMediaCorner = 12.dp,
        padBorderActive = 2.dp,
        padBorderIdle = 1.dp,
        statusIsDenseTypography = false,
        headerExtraDense = false,
        padTitleMinimal = false,
        padGlyphProminent = false,
        knobColumnInterItemSpacing = 0.dp,
    )
    MirrorLayoutDensity.LandscapeCompact -> MirrorLayoutTokens(
        cardCorner = 20.dp,
        cardElevation = 3.dp,
        paddingHorizontal = 10.dp,
        paddingVertical = 10.dp,
        sectionGap = 6.dp,
        knobPadRowGap = 8.dp,
        padCellGap = 5.dp,
        knobColumnWeight = 0.24f,
        padCellAspectRatio = 1f,
        knobOuterBox = 52.dp,
        knobInner = 40.dp,
        knobDot = 10.dp,
        knobArcStroke = 2.dp,
        knobBorder = 2.dp,
        knobStackSpacing = 2.dp,
        useDistinctPrimaryKnob = false,
        knobPrimaryOuter = 52.dp,
        knobPrimaryInner = 40.dp,
        knobPrimaryDot = 10.dp,
        knobPrimaryArcStroke = 2.dp,
        knobPrimaryBorder = 2.dp,
        padCorner = 10.dp,
        padPaddingH = 4.dp,
        padPaddingV = 3.dp,
        padColumnGap = 2.dp,
        padMediaCorner = 6.dp,
        padBorderActive = 1.5.dp,
        padBorderIdle = 1.dp,
        statusIsDenseTypography = true,
        headerExtraDense = false,
        padTitleMinimal = true,
        padGlyphProminent = false,
        knobColumnInterItemSpacing = 0.dp,
    )
    MirrorLayoutDensity.LandscapeSidebar -> MirrorLayoutTokens(
        cardCorner = 18.dp,
        cardElevation = 2.dp,
        paddingHorizontal = 8.dp,
        paddingVertical = 6.dp,
        sectionGap = 4.dp,
        knobPadRowGap = 6.dp,
        padCellGap = 3.dp,
        knobColumnWeight = 0.31f,
        padCellAspectRatio = 1.62f,
        knobOuterBox = 44.dp,
        knobInner = 34.dp,
        knobDot = 8.dp,
        knobArcStroke = 1.5.dp,
        knobBorder = 1.5.dp,
        knobStackSpacing = 2.dp,
        useDistinctPrimaryKnob = true,
        knobPrimaryOuter = 60.dp,
        knobPrimaryInner = 46.dp,
        knobPrimaryDot = 11.dp,
        knobPrimaryArcStroke = 2.5.dp,
        knobPrimaryBorder = 2.dp,
        padCorner = 8.dp,
        padPaddingH = 3.dp,
        padPaddingV = 1.dp,
        padColumnGap = 0.dp,
        padMediaCorner = 4.dp,
        padBorderActive = 1.5.dp,
        padBorderIdle = 1.dp,
        statusIsDenseTypography = true,
        headerExtraDense = true,
        padTitleMinimal = true,
        padGlyphProminent = true,
        knobColumnInterItemSpacing = 0.dp,
    )
    MirrorLayoutDensity.DashboardPortrait -> dashboardHomeMirrorTokens()
    MirrorLayoutDensity.DashboardLandscape -> dashboardHomeMirrorTokens()
}

/**
 * Single token set for the home dashboard mirror: portrait and landscape share the same
 * component scale; only composition (chrome rails, padding) changes with orientation.
 */
private fun dashboardHomeMirrorTokens(): MirrorLayoutTokens = MirrorLayoutTokens(
    cardCorner = 22.dp,
    cardElevation = 0.dp,
    /** Tighter mirror inset → more room for the fixed-size knob+pad block (same in both orientations). */
    paddingHorizontal = 8.dp,
    paddingVertical = 8.dp,
    sectionGap = 10.dp,
    /** Perillas ↔ grid: aligned in magnitude with [padCellGap] for a balanced rhythm. */
    knobPadRowGap = 8.dp,
    padCellGap = 7.dp,
    knobColumnWeight = 0.26f,
    padCellAspectRatio = 1f,
    knobOuterBox = 78.dp,
    knobInner = 60.dp,
    knobDot = 15.dp,
    knobArcStroke = 2.5.dp,
    knobBorder = 2.dp,
    knobStackSpacing = 4.dp,
    useDistinctPrimaryKnob = true,
    knobPrimaryOuter = 86.dp,
    knobPrimaryInner = 66.dp,
    knobPrimaryDot = 16.dp,
    knobPrimaryArcStroke = 3.dp,
    knobPrimaryBorder = 2.5.dp,
    padCorner = 18.dp,
    padPaddingH = 6.dp,
    padPaddingV = 5.dp,
    padColumnGap = 4.dp,
    padMediaCorner = 14.dp,
    padBorderActive = 2.dp,
    padBorderIdle = 1.dp,
    statusIsDenseTypography = false,
    headerExtraDense = false,
    padTitleMinimal = false,
    padGlyphProminent = false,
    knobColumnInterItemSpacing = 10.dp,
)
