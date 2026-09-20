package app.aaps.core.interfaces.resources

import app.aaps.core.keys.StringKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * That every language the app offers turns into a usable BCP 47 tag.
 *
 * The stored values are not all tags, and the two that need fixing up were fixed up only on Android:
 * `dk` is a country code where a language code belongs, and `pt_BR` / `zh_TW` / `zh_CN` use the Java
 * underscore form. The iOS and desktop shells passed the stored value straight to the registry, so
 * those four matched no translation and gave a fully English app - indistinguishable from "not
 * translated yet", with nothing logged.
 *
 * The list is read from `StringKey.GeneralLanguage` rather than copied here, so a language added to
 * the preference is covered by these tests the moment it is added.
 */
class LanguageTagTest {

    private val offered: Set<String>
        get() = StringKey.GeneralLanguage.entries.keys

    @Test
    fun `the option list is the one under test`() {
        // Guards the guard: if the preference stops exposing its options, every case below would
        // pass vacuously.
        assertTrue(offered.isNotEmpty())
        assertTrue(offered.contains("dk"))
        assertTrue(offered.contains("pt_BR"))
    }

    @Test
    fun `follow-device has no tag of its own`() {
        assertNull(LanguageTag.of("default"))
    }

    @Test
    fun `Danish resolves to its ISO 639 code`() {
        assertEquals("da", LanguageTag.of("dk"))
    }

    @Test
    fun `a language with a region uses a hyphen`() {
        assertEquals("pt-BR", LanguageTag.of("pt_BR"))
        assertEquals("zh-TW", LanguageTag.of("zh_TW"))
        assertEquals("zh-CN", LanguageTag.of("zh_CN"))
    }

    @Test
    fun `a plain language code passes through`() {
        assertEquals("cs", LanguageTag.of("cs"))
        assertEquals("de", LanguageTag.of("de"))
    }

    /**
     * The whole point, over the real list: nothing the user can pick may reach the registry with an
     * underscore in it, because nothing is keyed that way.
     */
    @Test
    fun `no offered language produces an underscore`() {
        offered.forEach { stored ->
            val tag = LanguageTag.of(stored)
            assertFalse(tag?.contains('_') ?: false)
        }
    }

    /** ...and every one of them produces something, other than the follow-device entry. */
    @Test
    fun `every offered language produces a tag`() {
        offered.filterNot { it == LanguageTag.FOLLOW_DEVICE }.forEach { stored ->
            assertNotNull(LanguageTag.of(stored))
        }
    }
}
