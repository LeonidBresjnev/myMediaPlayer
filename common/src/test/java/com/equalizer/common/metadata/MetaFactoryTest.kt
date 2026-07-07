package com.equalizer.common.metadata

import org.junit.Test
import org.junit.Assert.*
import java.lang.reflect.Method

class MetaFactoryTest {

    @Test
    fun testParseTrackNumber() {
        // Since parseTrackNumber is private, we use reflection or we could have made it internal/public for testing.
        // Given the instructions to be concise, I'll use reflection to test the private method.
        
        val method: Method = MetaFactory::class.java.getDeclaredMethod("parseTrackNumber", String::class.java)
        method.isAccessible = true
        
        fun callParse(input: String?): Int? = method.invoke(MetaFactory, input) as Int?

        assertEquals(1, callParse("1"))
        assertEquals(1, callParse("1/12"))
        assertEquals(2, callParse(" 02 "))
        assertEquals(null, callParse("invalid"))
        assertEquals(null, callParse(null))
        assertEquals(null, callParse(""))
        assertEquals(10, callParse("10/10"))
    }
}
