package com.zoomplay.app

/** Règles de rédaction des titres : détection et correction automatique. */
object TitleFix {
    private val bitrate = Regex("\\(\\s*\\d{2,3}\\s*k(bps)?\\s*\\)", RegexOption.IGNORE_CASE)
    private val copyMark = Regex("(\\s*\\(\\s*\\d{1,2}\\s*\\))+\\s*$")
    private val spaces = Regex("\\s+")
    private val openSp = Regex("\\(\\s+")
    private val closeSp = Regex("\\s+\\)")
    private val glued = Regex("(?<=[\\p{L}\\p{N}])\\(")
    private const val allowed = " -'’&,.()!+"

    /** Version corrigée d'un titre (ou le titre inchangé s'il est déjà correct). */
    fun clean(src: String): String {
        var s = src.replace('_', ' ')
        s = bitrate.replace(s, "")
        s = copyMark.replace(s, "")
        s = s.replace('[', '(').replace(']', ')').replace('{', '(').replace('}', ')')
        s = s.replace('–', '-').replace('—', '-')
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
