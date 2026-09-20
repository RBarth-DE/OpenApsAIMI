package app.aaps.core.objects.interfaces.pump.defs

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.keys.interfaces.TextRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * In `commonTest` rather than `androidHostTest`: [PluginDescription] is shared code - every plugin
 * on every platform carries one - and the test needed nothing from the Android test base.
 */
class PluginDescriptionTest {

    @Test fun mainTypeTest() {
        val pluginDescription = PluginDescription().mainType(PluginType.PUMP)
        assertEquals(PluginType.PUMP, pluginDescription.mainType)
    }

    @Test fun alwaysEnabledTest() {
        val pluginDescription = PluginDescription().alwaysEnabled(true)
        assertTrue(pluginDescription.alwaysEnabled)
    }

    @Test fun neverVisibleTest() {
        val pluginDescription = PluginDescription().neverVisible(true)
        assertTrue(pluginDescription.neverVisible)
    }

    @Test fun showInListTest() {
        val pluginDescription = PluginDescription().showInList { false }
        assertFalse(pluginDescription.showInList.invoke())
    }

    @Test fun pluginName() {
        val ref = TextRef.AndroidRes(10)
        assertEquals(ref, PluginDescription().pluginName(ref).pluginName)
    }

    @Test fun shortNameTest() {
        val ref = TextRef.AndroidRes(10)
        assertEquals(ref, PluginDescription().shortName(ref).shortName)
    }

    @Test fun enableByDefault() {
        val pluginDescription = PluginDescription().enableByDefault(true)
        assertTrue(pluginDescription.enableByDefault)
    }
}
