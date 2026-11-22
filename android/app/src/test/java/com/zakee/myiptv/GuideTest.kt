package com.zakee.myiptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {
    private val json = """{"generated":0,"channels":{"A":[[200,300,"Second"],[100,200,"First"],[300,400,"Third"]]}}"""
    private val guide = Guide.parse(json)

    @Test fun findsNowAndNext() {
        val (now, next) = guide.nowNext("A", 150)
        assertEquals("First", now?.title)
        assertEquals("Second", next?.title)
    }

    @Test fun boundariesBelongToTheNextShow() {
        assertEquals("Second", guide.nowNext("A", 200).first?.title)
    }

    @Test fun beforeScheduleOnlyNext() {
        val (now, next) = guide.nowNext("A", 50)
        assertNull(now)
        assertEquals("First", next?.title)
    }

    @Test fun unknownChannelAndBadJson() {
        assertNull(guide.nowNext("B", 150).first)
        assertTrue(Guide.parse("{}").isEmpty)
    }

    @Test fun progress() {
        assertEquals(0.5f, Programme(100, 200, "x").progress(150), 0.001f)
        assertEquals(1f, Programme(100, 200, "x").progress(999), 0.001f)
    }
}
