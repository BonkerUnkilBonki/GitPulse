package com.codenamezeroseven.gitpulse

/** Selectable color palettes + Material You dynamic color. */
object Palette {

    data class Entry(
        val id: String,
        val label: String,
        val swatch: Int,
        val overlay: Int // 0 = dynamic color (from wallpaper)
    )

    val all = listOf(
        Entry("teal", "Teal", 0xFF006A6A.toInt(), R.style.ThemeOverlay_GitPulse_Palette_Teal),
        Entry("blue", "Ocean", 0xFF005EB2.toInt(), R.style.ThemeOverlay_GitPulse_Palette_Ocean),
        Entry("violet", "Violet", 0xFF6750A4.toInt(), R.style.ThemeOverlay_GitPulse_Palette_Violet),
        Entry("orange", "Sunset", 0xFF9C4A00.toInt(), R.style.ThemeOverlay_GitPulse_Palette_Sunset),
        Entry("green", "Forest", 0xFF386A20.toInt(), R.style.ThemeOverlay_GitPulse_Palette_Forest),
        Entry("rose", "Rose", 0xFF984858.toInt(), R.style.ThemeOverlay_GitPulse_Palette_Rose),
        Entry("dynamic", "Wallpaper", 0xFF3D4046.toInt(), 0)
    )

    fun current(): Entry = all.firstOrNull { it.id == Prefs.palette } ?: all.first()

    /** Overlay style to apply to the activity theme; 0 when using dynamic color. */
    fun overlayRes(id: String = Prefs.palette): Int =
        all.firstOrNull { it.id == id }?.overlay ?: R.style.ThemeOverlay_GitPulse_Palette_Teal
}
