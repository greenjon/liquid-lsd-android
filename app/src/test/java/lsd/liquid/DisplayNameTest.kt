package lsd.liquid

import lsd.liquid.ui.displayName
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {
    @Test
    fun playlistNamesReadAsTitles() {
        assertEquals("Beat Spiral", displayName("web-beat-spiral"))
        assertEquals("Domain Warp Fluid", displayName("stock-domain_warp_fluid"))
        assertEquals("-", displayName(null))
    }
}
