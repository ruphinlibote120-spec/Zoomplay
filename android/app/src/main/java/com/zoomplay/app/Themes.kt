package com.zoomplay.app

/** Les styles d'affichage de Zoomplay. */
class ZTheme(
    val name: String, val styleRes: Int, val dark: Boolean,
    val bg: Int, val surface2: Int, val accent: Int, val top: Int
)

object Themes {
    val all = listOf(
        ZTheme("Zoomplay (nuit et or)", R.style.Theme_Zoomplay_T01, true, 0xFF0E1220.toInt(), 0xFF1F2540.toInt(), 0xFFFFC53D.toInt(), 0xFF2B4A9A.toInt()),
        ZTheme("Minuit cyan", R.style.Theme_Zoomplay_T02, true, 0xFF000000.toInt(), 0xFF17171C.toInt(), 0xFF19D3C5.toInt(), 0xFF0F3A44.toInt()),
        ZTheme("Océan", R.style.Theme_Zoomplay_T03, true, 0xFF07161F.toInt(), 0xFF12303F.toInt(), 0xFF2EC4F1.toInt(), 0xFF0F5C78.toInt()),
        ZTheme("Forêt", R.style.Theme_Zoomplay_T04, true, 0xFF0B1510.toInt(), 0xFF1A2E24.toInt(), 0xFF4ADE80.toInt(), 0xFF1C5A3A.toInt()),
        ZTheme("Rubis", R.style.Theme_Zoomplay_T05, true, 0xFF170A0E.toInt(), 0xFF331822.toInt(), 0xFFFF4D6A.toInt(), 0xFF7A1F36.toInt()),
        ZTheme("Violet", R.style.Theme_Zoomplay_T06, true, 0xFF120B1E.toInt(), 0xFF271B45.toInt(), 0xFFA78BFA.toInt(), 0xFF4B2C99.toInt()),
        ZTheme("Coucher de soleil", R.style.Theme_Zoomplay_T07, true, 0xFF1A0F0A.toInt(), 0xFF36201A.toInt(), 0xFFFF8A3D.toInt(), 0xFF8A3A12.toInt()),
        ZTheme("Rose néon", R.style.Theme_Zoomplay_T08, true, 0xFF160A17.toInt(), 0xFF331A3B.toInt(), 0xFFFF4FD8.toInt(), 0xFF7A1F72.toInt()),
        ZTheme("Graphite", R.style.Theme_Zoomplay_T09, true, 0xFF121212.toInt(), 0xFF272727.toInt(), 0xFFE0E0E0.toInt(), 0xFF3A3A3A.toInt()),
        ZTheme("Bleu royal", R.style.Theme_Zoomplay_T10, true, 0xFF0A1030.toInt(), 0xFF19265E.toInt(), 0xFF5B8CFF.toInt(), 0xFF2748C8.toInt()),
        ZTheme("Turquoise", R.style.Theme_Zoomplay_T11, true, 0xFF04171A.toInt(), 0xFF103238.toInt(), 0xFF14B8A6.toInt(), 0xFF0E6B63.toInt()),
        ZTheme("Café", R.style.Theme_Zoomplay_T12, true, 0xFF17100C.toInt(), 0xFF30231A.toInt(), 0xFFD9A066.toInt(), 0xFF6B4423.toInt()),
        ZTheme("Lavande nuit", R.style.Theme_Zoomplay_T13, true, 0xFF0F1020.toInt(), 0xFF232647.toInt(), 0xFF8B9BFF.toInt(), 0xFF3E4AA8.toInt()),
        ZTheme("Citron vert", R.style.Theme_Zoomplay_T14, true, 0xFF0D1305.toInt(), 0xFF202B10.toInt(), 0xFFB6F03A.toInt(), 0xFF4A6B0E.toInt()),
        ZTheme("Rouge et noir", R.style.Theme_Zoomplay_T15, true, 0xFF0A0A0A.toInt(), 0xFF211616.toInt(), 0xFFE53935.toInt(), 0xFF7B1713.toInt()),
        ZTheme("Or royal", R.style.Theme_Zoomplay_T16, true, 0xFF0F0D08.toInt(), 0xFF271F12.toInt(), 0xFFE6B422.toInt(), 0xFF6F5410.toInt()),
        ZTheme("Crépuscule", R.style.Theme_Zoomplay_T17, true, 0xFF1A1026.toInt(), 0xFF33224A.toInt(), 0xFFFF7A59.toInt(), 0xFF8B2F5E.toInt()),
        ZTheme("Papier clair", R.style.Theme_Zoomplay_T18, false, 0xFFF6F3EC.toInt(), 0xFFECE7DC.toInt(), 0xFFE29A00.toInt(), 0xFFF0D9A0.toInt()),
        ZTheme("Neige bleue", R.style.Theme_Zoomplay_T19, false, 0xFFF3F7FC.toInt(), 0xFFE6EEF8.toInt(), 0xFF2F6BFF.toInt(), 0xFFBFD4FF.toInt()),
        ZTheme("Menthe claire", R.style.Theme_Zoomplay_T20, false, 0xFFF1FAF6.toInt(), 0xFFE0F3EA.toInt(), 0xFF12A36B.toInt(), 0xFFA8E6CB.toInt()),
        ZTheme("Rose clair", R.style.Theme_Zoomplay_T21, false, 0xFFFFF4F7.toInt(), 0xFFFDE6EC.toInt(), 0xFFE8457A.toInt(), 0xFFFBB6CB.toInt()),
        ZTheme("Lavande claire", R.style.Theme_Zoomplay_T22, false, 0xFFF6F3FF.toInt(), 0xFFEBE5FB.toInt(), 0xFF7C4DFF.toInt(), 0xFFCDBDFB.toInt()),
        ZTheme("Sable", R.style.Theme_Zoomplay_T23, false, 0xFFFAF3E8.toInt(), 0xFFF0E4D0.toInt(), 0xFFC26A1B.toInt(), 0xFFEBC99A.toInt()),
        ZTheme("Ciel clair", R.style.Theme_Zoomplay_T24, false, 0xFFEFF9FC.toInt(), 0xFFDDF0F7.toInt(), 0xFF0891B2.toInt(), 0xFFA5E3F2.toInt()),
        ZTheme("Pierre claire", R.style.Theme_Zoomplay_T25, false, 0xFFF2F2F2.toInt(), 0xFFE4E4E4.toInt(), 0xFF424242.toInt(), 0xFFD6D6D6.toInt())
    )

    fun current(store: Store): ZTheme = all[store.theme.coerceIn(0, all.size - 1)]
}
