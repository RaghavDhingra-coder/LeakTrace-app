package com.codecrafters.leaktrace.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LeakTraceDestinationTest {
    @Test
    fun fromRoute_resolvesEveryDestination() {
        LeakTraceDestination.entries.forEach {
            assertEquals(it, LeakTraceDestination.fromRoute(it.route))
        }
    }

    @Test
    fun fromRoute_fallsBackToHomeForUnknownOrMissingRoute() {
        assertEquals(LeakTraceDestination.Home, LeakTraceDestination.fromRoute(null))
        assertEquals(LeakTraceDestination.Home, LeakTraceDestination.fromRoute("unknown"))
    }

    @Test
    fun routesAreUnique() {
        val routes = LeakTraceDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun bottomBar_holdsFiveTabsAndExcludesDetailScreens() {
        assertEquals(5, LeakTraceDestination.bottomBar.size)
        assertFalse(LeakTraceDestination.Settings in LeakTraceDestination.bottomBar)
        assertFalse(LeakTraceDestination.Account in LeakTraceDestination.bottomBar)
    }
}
