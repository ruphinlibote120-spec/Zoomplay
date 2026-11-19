package com.zoomplay.app

import java.text.Normalizer

/** « Zoom Play music », « Zoom Next », « Zoom Prev », « Zoom Pause ». */
object VoiceCommands {
    private val re = Regex(
        "\\bzoo?u?m+e?(?:\\s*play)?\\s+(play\\s*music|play|music|musique|lecture|next|suivant|suivante|prev|previous|precedent|precedente|pause|stop)\\b"
    )

    fun parse(text: String): String? {
        val s = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val m = re.find(s) ?: return null
        val w = m.groupValues[1]
        return when {
            w.startsWith("play") || w == "music" || w == "musique" || w == "lecture" -> "play"
            w.startsWith("next") || w.startsWith("suivant") -> "next"
            w.startsWith("prev") || w.startsWith("precedent") -> "prev"
            else -> "pause"
        }
    }
}
