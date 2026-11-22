package com.zakee.myiptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uTest {
    private val sample = """
        #EXTM3U
        #EXTINF:-1 tvg-id="StarVijay.in" tvg-logo="https://x/vijay.png" group-title="Tamil",Star Vijay (720p)
        https://a.example/vijay/index.m3u8
        #EXTINF:-1 tvg-id="StarVijay.in" tvg-logo="https://x/vijay.png" group-title="Tamil",Star Vijay (576p) [Not 24/7]
        #EXTVLCOPT:http-user-agent=TestAgent/1.0
        #EXTVLCOPT:http-referrer=https://ref.example/
        http://b.example/vijay
        #EXTINF:-1 tvg-id="PBSKids.us@SD" tvg-logo="" group-title="Kids (English)",PBS Kids, East
        https://c.example/pbs.m3u8
    """.trimIndent()

    @Test fun groupsBackupStreamsUnderOneChannel() {
        val list = M3u.parse(sample)
        assertEquals(2, list.size)
        assertEquals(2, list[0].streams.size)
        assertEquals("Star Vijay", list[0].name)
    }

    @Test fun readsHeadersAndNumbersChannelsInOrder() {
        val (vijay, pbs) = M3u.parse(sample)
        assertEquals("TestAgent/1.0", vijay.streams[1].userAgent)
        assertEquals("https://ref.example/", vijay.streams[1].referrer)
        assertNull(vijay.streams[0].userAgent)
        assertEquals(1, vijay.number)
        assertEquals(2, pbs.number)
    }

    @Test fun stripsFeedFromIdAndHandlesCommasAndEmptyLogo() {
        val pbs = M3u.parse(sample)[1]
        assertEquals("PBSKids.us", pbs.id)
        assertEquals("PBS Kids, East", pbs.name)
        assertNull(pbs.logo)
        assertEquals("Kids (English)", pbs.group)
    }

    @Test fun ignoresJunk() {
        assertEquals(0, M3u.parse("").size)
        assertEquals(0, M3u.parse("#EXTM3U\nhttp://orphan-url-without-info").size)
    }
}
