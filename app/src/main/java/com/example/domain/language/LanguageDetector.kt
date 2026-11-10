package com.example.domain.language

import com.example.domain.model.AlexLanguage

object LanguageDetector {

    private val romanizedBanglaKeywords = setOf(
        "kemon", "acho", "achen", "tumi", "apni", "amar", "mon", "bhalo", "nei",
        "ki", "korcho", "koro", "bolte", "paro", "dhonnobad", "shubho", "bhalobashi",
        "kothay", "kokhon", "keno", "dada", "bondhu", "ajke", "kal", "khabar"
    )

    private val romanizedHindiKeywords = setOf(
        "kaise", "kaisa", "kaisi", "ho", "hai", "mera", "meri", "aaj", "kya",
        "kar", "rahe", "rahi", "namaste", "shukriya", "dhanyawad", "batao",
        "bataiye", "theek", "accha", "nahi", "aur", "abhi", "kal", "dost"
    )

    /**
     * Automatically detects whether text is Bangla (বাংলা), Hindi (हिन्दी), or English.
     * Supports native Unicode scripts as well as common Romanized Bangla/Hindi phrases.
     */
    fun detectLanguage(text: String): AlexLanguage {
        if (text.isBlank()) return AlexLanguage.ENGLISH

        var bengaliCharCount = 0
        var devanagariCharCount = 0
        var latinCharCount = 0

        for (ch in text) {
            when (Character.UnicodeBlock.of(ch)) {
                Character.UnicodeBlock.BENGALI -> bengaliCharCount++
                Character.UnicodeBlock.DEVANAGARI -> devanagariCharCount++
                Character.UnicodeBlock.BASIC_LATIN,
                Character.UnicodeBlock.LATIN_1_SUPPLEMENT -> {
                    if (ch.isLetter()) latinCharCount++
                }
            }
        }

        if (bengaliCharCount > 0 && bengaliCharCount >= devanagariCharCount) {
            return AlexLanguage.BANGLA
        }
        if (devanagariCharCount > 0 && devanagariCharCount > bengaliCharCount) {
            return AlexLanguage.HINDI
        }

        // Check Romanized Bangla / Hindi tokens if written in Latin script
        val tokens = text.lowercase()
            .split(Regex("[^a-zA-Z]+"))
            .filter { it.isNotBlank() }

        val banglaMatches = tokens.count { it in romanizedBanglaKeywords }
        val hindiMatches = tokens.count { it in romanizedHindiKeywords }

        if (banglaMatches >= 2 && banglaMatches > hindiMatches) {
            return AlexLanguage.BANGLA
        }
        if (hindiMatches >= 2 && hindiMatches > banglaMatches) {
            return AlexLanguage.HINDI
        }

        return AlexLanguage.ENGLISH
    }

    /**
     * Returns localized greeting in the requested language for ALEX AI.
     */
    fun getLocalizedGreeting(language: AlexLanguage, userTitle: String = "Boss"): String {
        return when (language) {
            AlexLanguage.BANGLA -> "হ্যালো $userTitle! আমি ALEX। আমি লাইভে আছি এবং শুনছি—আজ তোমার জন্য কী করতে পারি?"
            AlexLanguage.HINDI -> "नमस्ते $userTitle! मैं ALEX हूँ। मैं लाइव हूँ और आपकी बात सुन रही हूँ—आज मैं आपकी क्या मदद कर सकती हूँ?"
            AlexLanguage.ENGLISH, AlexLanguage.AUTO -> "Hello $userTitle! I'm ALEX, online and listening. What can I do for you today?"
        }
    }
}
