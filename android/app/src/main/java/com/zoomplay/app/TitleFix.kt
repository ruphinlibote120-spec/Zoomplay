package com.zoomplay.app

/** Règles de rédaction des titres : détection et correction automatique. */
object TitleFix {
    private val ic = RegexOption.IGNORE_CASE

    // (128k) [256kbps] 48kbps …
    private val bitrate = Regex("\\(\\s*\\d{2,3}\\s*k(bps)?\\s*\\)|\\b\\d{2,3}\\s*kbps\\b", ic)

    // (Audio) (Official Video) (Clip officiel) (Vidéo) … : parenthèses ne contenant que ces mots
    private val tagParen = Regex(
        "\\(\\s*(?:(?:official|officiel(?:le)?|oficial|clip|audio|vid[eé]o|music)\\s*)+\\)", ic
    )

    // Expressions toujours retirées, où qu'elles soient : "Clip officiel", "Official Video"…
    private val tagPhrase = Regex(
        "\\b(?:clip\\s+offici[ae]l(?:le)?|official\\s+(?:music\\s+)?(?:audio|video|clip)|" +
            "(?:audio|vid[eé]o)\\s+offici[ae]l(?:le)?|vid[eé]o\\s+clip)\\b", ic
    )

    // " - Audio", " | Video" au milieu ou "Audio" / "Vidéo" à la fin du titre
    private val tagSep = Regex("\\s[-|:]\\s*(?:audio|vid[eé]o)\\b", ic)
    private val tagEnd = Regex("(?:\\s+(?:audio|vid[eé]o))+\\s*$", ic)

    private val copyMark = Regex("(\\s*\\(\\s*\\d{1,2}\\s*\\))+\\s*$")
    private val emptyParen = Regex("\\(\\s*\\)")
    private val spaces = Regex("\\s+")
    private val openSp = Regex("\\(\\s+")
    private val closeSp = Regex("\\s+\\)")
    private val glued = Regex("(?<=[\\p{L}\\p{N}])\\(")
    private val invisible = Regex("[\\u200B-\\u200F\\u2060\\uFEFF]")
    private const val allowed = " -'’&,.()!+"

    /** Version corrigée d'un titre (ou le titre inchangé s'il est déjà correct). */
    fun clean(src: String): String {
        var s = java.text.Normalizer.normalize(src, java.text.Normalizer.Form.NFC)
        s = invisible.replace(s, "")
        s = s.map { if (it.isWhitespace() || it == ' ' || it == ' ') ' ' else it }.joinToString("")
        s = s.replace('_', ' ')
        s = s.replace('[', '(').replace(']', ')').replace('{', '(').replace('}', ')')
        s = s.replace('–', '-').replace('—', '-')
        s = bitrate.replace(s, "")
        s = tagParen.replace(s, "")
        s = tagPhrase.replace(s, "")
        s = emptyParen.replace(s, "")
        s = tagSep.replace(s, "")
        s = tagEnd.replace(s, "")
        s = copyMark.replace(s, "")
        val sb = StringBuilder()
        for (ch in s) if (ch.isLetterOrDigit() || allowed.indexOf(ch) >= 0) sb.append(ch)
        s = sb.toString()
        s = spaces.replace(s, " ")
        s = openSp.replace(s, "(")
        s = closeSp.replace(s, ")")
        s = glued.replace(s, " (")
        s = s.trimStart { !it.isLetterOrDigit() }
        s = s.trimEnd { it == '-' || it == ',' || it == ' ' }
        s = s.trim()
        if (s.isEmpty()) return src
        val first = s[0]
        if (first.isLetter() && first.isLowerCase()) s = first.uppercaseChar() + s.substring(1)
        return s
    }

    fun needsFix(title: String): Boolean {
        val c = clean(title)
        return c != title && c.isNotBlank()
    }

    /** Nom de fichier valide (sans caractères interdits). */
    fun sanitize(name: String): String =
        spaces.replace(name.replace(Regex("[/\\\\:*?\"<>|]"), ""), " ").trim()
}
