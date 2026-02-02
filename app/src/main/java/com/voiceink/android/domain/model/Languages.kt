package com.voiceink.android.domain.model

/**
 * Represents a language for transcription
 */
data class Language(
    val code: String,
    val name: String
)

/**
 * Whisper supported languages (99+ languages)
 * Source: https://github.com/openai/whisper/blob/main/whisper/tokenizer.py
 */
object WhisperLanguages {
    val AUTO_DETECT = Language("auto", "Auto-detect")

    val ALL = listOf(
        AUTO_DETECT,
        Language("en", "English"),
        Language("zh", "Chinese"),
        Language("de", "German"),
        Language("es", "Spanish"),
        Language("ru", "Russian"),
        Language("ko", "Korean"),
        Language("fr", "French"),
        Language("ja", "Japanese"),
        Language("pt", "Portuguese"),
        Language("tr", "Turkish"),
        Language("pl", "Polish"),
        Language("ca", "Catalan"),
        Language("nl", "Dutch"),
        Language("ar", "Arabic"),
        Language("sv", "Swedish"),
        Language("it", "Italian"),
        Language("id", "Indonesian"),
        Language("hi", "Hindi"),
        Language("fi", "Finnish"),
        Language("vi", "Vietnamese"),
        Language("he", "Hebrew"),
        Language("uk", "Ukrainian"),
        Language("el", "Greek"),
        Language("ms", "Malay"),
        Language("cs", "Czech"),
        Language("ro", "Romanian"),
        Language("da", "Danish"),
        Language("hu", "Hungarian"),
        Language("ta", "Tamil"),
        Language("no", "Norwegian"),
        Language("th", "Thai"),
        Language("ur", "Urdu"),
        Language("hr", "Croatian"),
        Language("bg", "Bulgarian"),
        Language("lt", "Lithuanian"),
        Language("la", "Latin"),
        Language("mi", "Maori"),
        Language("ml", "Malayalam"),
        Language("cy", "Welsh"),
        Language("sk", "Slovak"),
        Language("te", "Telugu"),
        Language("fa", "Persian"),
        Language("lv", "Latvian"),
        Language("bn", "Bengali"),
        Language("sr", "Serbian"),
        Language("az", "Azerbaijani"),
        Language("sl", "Slovenian"),
        Language("kn", "Kannada"),
        Language("et", "Estonian"),
        Language("mk", "Macedonian"),
        Language("br", "Breton"),
        Language("eu", "Basque"),
        Language("is", "Icelandic"),
        Language("hy", "Armenian"),
        Language("ne", "Nepali"),
        Language("mn", "Mongolian"),
        Language("bs", "Bosnian"),
        Language("kk", "Kazakh"),
        Language("sq", "Albanian"),
        Language("sw", "Swahili"),
        Language("gl", "Galician"),
        Language("mr", "Marathi"),
        Language("pa", "Punjabi"),
        Language("si", "Sinhala"),
        Language("km", "Khmer"),
        Language("sn", "Shona"),
        Language("yo", "Yoruba"),
        Language("so", "Somali"),
        Language("af", "Afrikaans"),
        Language("oc", "Occitan"),
        Language("ka", "Georgian"),
        Language("be", "Belarusian"),
        Language("tg", "Tajik"),
        Language("sd", "Sindhi"),
        Language("gu", "Gujarati"),
        Language("am", "Amharic"),
        Language("yi", "Yiddish"),
        Language("lo", "Lao"),
        Language("uz", "Uzbek"),
        Language("fo", "Faroese"),
        Language("ht", "Haitian Creole"),
        Language("ps", "Pashto"),
        Language("tk", "Turkmen"),
        Language("nn", "Norwegian Nynorsk"),
        Language("mt", "Maltese"),
        Language("sa", "Sanskrit"),
        Language("lb", "Luxembourgish"),
        Language("my", "Myanmar"),
        Language("bo", "Tibetan"),
        Language("tl", "Tagalog"),
        Language("mg", "Malagasy"),
        Language("as", "Assamese"),
        Language("tt", "Tatar"),
        Language("haw", "Hawaiian"),
        Language("ln", "Lingala"),
        Language("ha", "Hausa"),
        Language("ba", "Bashkir"),
        Language("jw", "Javanese"),
        Language("su", "Sundanese")
    )

    /**
     * Get commonly used languages (top 20)
     */
    val COMMON = listOf(
        AUTO_DETECT,
        Language("en", "English"),
        Language("es", "Spanish"),
        Language("fr", "French"),
        Language("de", "German"),
        Language("it", "Italian"),
        Language("pt", "Portuguese"),
        Language("zh", "Chinese"),
        Language("ja", "Japanese"),
        Language("ko", "Korean"),
        Language("ar", "Arabic"),
        Language("ru", "Russian"),
        Language("hi", "Hindi"),
        Language("nl", "Dutch"),
        Language("pl", "Polish"),
        Language("tr", "Turkish"),
        Language("vi", "Vietnamese"),
        Language("th", "Thai"),
        Language("id", "Indonesian"),
        Language("sv", "Swedish")
    )

    fun findByCode(code: String): Language? {
        return ALL.find { it.code == code }
    }
}
