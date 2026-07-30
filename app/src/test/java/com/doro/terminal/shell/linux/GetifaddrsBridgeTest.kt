package com.doro.terminal.shell.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GetifaddrsBridgeTest {
    @Test
    fun preloadExpressionPreservesExistingLibraries() {
        assertEquals(
            "/usr/local/lib/doro/libgetifaddrs_bridge.so:/opt/extra.so",
            GetifaddrsBridge.preloadExpression("/opt/extra.so")
        )
    }

    @Test
    fun preloadExpressionHasNoTrailingSeparator() {
        assertFalse(GetifaddrsBridge.preloadExpression("").endsWith(":"))
    }
}