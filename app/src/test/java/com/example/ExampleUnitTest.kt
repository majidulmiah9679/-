package com.example

import com.example.core.config.AlexConfig
import com.example.domain.language.LanguageDetector
import com.example.domain.model.AlexLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `language detector identifies Bangla Unicode and Romanized Bangla`() {
        assertEquals(
            AlexLanguage.BANGLA,
            LanguageDetector.detectLanguage("হ্যালো অ্যালেক্স! আজ আমার মন ভালো নেই।")
        )
        assertEquals(
            AlexLanguage.BANGLA,
            LanguageDetector.detectLanguage("Hello Alex, tumi kemon acho ajke?")
        )
    }

    @Test
    fun `language detector identifies Hindi Unicode and Romanized Hindi`() {
        assertEquals(
            AlexLanguage.HINDI,
            LanguageDetector.detectLanguage("नमस्ते एलेक्स, आज का मौसम कैसा है?")
        )
        assertEquals(
            AlexLanguage.HINDI,
            LanguageDetector.detectLanguage("Namaste Alex, aap kaise ho aaj?")
        )
    }

    @Test
    fun `language detector identifies English naturally`() {
        assertEquals(
            AlexLanguage.ENGLISH,
            LanguageDetector.detectLanguage("What are you doing right now, ALEX?")
        )
    }

    @Test
    fun `system instruction includes Boss addressing and multilingual rules`() {
        val instruction = AlexConfig.buildSystemInstruction(
            userTitle = "Boss",
            preferredLanguage = AlexLanguage.AUTO,
            voiceName = "Aoede"
        )
        assertTrue(instruction.contains("ALEX"))
        assertTrue(instruction.contains("Boss"))
        assertTrue(instruction.contains("Bangla"))
        assertTrue(instruction.contains("Hindi"))
        assertTrue(instruction.contains("Aoede"))
    }

    @Test
    fun `sanitizeModelName upgrades deprecated gemini 2 models to gemini-3_8-flash`() {
        assertEquals(
            "gemini-3.8-flash",
            AlexConfig.sanitizeModelName("gemini-2.0-flash-live-preview-04-09")
        )
        assertEquals(
            "gemini-3.8-flash",
            AlexConfig.sanitizeModelName("gemini-2.5-flash-live-preview")
        )
        assertEquals(
            "gemini-3.8-flash",
            AlexConfig.sanitizeModelName("gemini-3.8-flash")
        )
    }
}
