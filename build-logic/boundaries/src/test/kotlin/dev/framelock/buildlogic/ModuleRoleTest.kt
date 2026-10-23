package dev.framelock.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the whole ADR-014 table: for every pair of modules, what a production and a test classpath may reach. The
 * expected sets are written out literally, independent of [ModuleRole]'s logic, so any change to that logic that
 * changes a single cell fails here. TestKit tests (`functionalTest`) prove the check is wired into real builds.
 */
class ModuleRoleTest {
    @Test
    fun `production classpaths reach exactly what ADR-014 allows`() {
        assertEquals(PRODUCTION, reachTable("compileClasspath"))
    }

    @Test
    fun `test classpaths may add shared testing and what it reaches`() {
        val expected =
            PRODUCTION.mapValues { (from, reach) ->
                if (from == ARCHITECTURE_TESTS) reach else reach + TESTING + PRODUCTION.getValue(TESTING) - from
            }
        assertEquals(expected, reachTable("testCompileClasspath"))
    }

    @Test
    fun `android variant classpaths follow the same table`() {
        assertEquals(PRODUCTION, reachTable("releaseRuntimeClasspath"))
    }

    private fun reachTable(classpath: String): Map<String, Set<String>> =
        MODULES.associateWith { from ->
            MODULES.filter { to -> to != from && ModuleRole.of(from).mayReach(ModuleRole.of(to), classpath) }.toSet()
        }

    private companion object {
        const val KERNEL = ":shared:kernel"
        const val TESTING = ":shared:testing"
        const val LIVE_DOMAIN = ":features:live:domain"
        const val LIVE_APPLICATION = ":features:live:application"
        const val LIVE_UVC = ":features:live:capture-uvc"
        const val LIVE_SYSTEM = ":features:live:system"
        const val LIVE_UI = ":features:live:ui"
        const val MODELS_DOMAIN = ":features:models:domain"
        const val MODELS_APPLICATION = ":features:models:application"
        const val MODELS_STORE = ":features:models:store"
        const val MODELS_UI = ":features:models:ui"
        const val TELEMETRY_DOMAIN = ":features:telemetry:domain"
        const val TELEMETRY_APPLICATION = ":features:telemetry:application"
        const val TELEMETRY_ROOM = ":features:telemetry:room"
        const val APP = ":app"
        const val ARCHITECTURE_TESTS = ":architecture-tests"

        val MODULES =
            listOf(
                KERNEL,
                TESTING,
                LIVE_DOMAIN,
                LIVE_APPLICATION,
                LIVE_UVC,
                LIVE_SYSTEM,
                LIVE_UI,
                MODELS_DOMAIN,
                MODELS_APPLICATION,
                MODELS_STORE,
                MODELS_UI,
                TELEMETRY_DOMAIN,
                TELEMETRY_APPLICATION,
                TELEMETRY_ROOM,
                APP,
                ARCHITECTURE_TESTS,
            )

        /** ADR-014, "Layers depend inward", with `models → live` and `telemetry → live, models`. */
        val PRODUCTION: Map<String, Set<String>> =
            mapOf(
                KERNEL to setOf(),
                TESTING to setOf(KERNEL, LIVE_DOMAIN, MODELS_DOMAIN, TELEMETRY_DOMAIN),
                LIVE_DOMAIN to setOf(KERNEL),
                LIVE_APPLICATION to setOf(KERNEL, LIVE_DOMAIN),
                LIVE_UVC to setOf(KERNEL, LIVE_DOMAIN),
                LIVE_SYSTEM to setOf(KERNEL, LIVE_DOMAIN),
                LIVE_UI to setOf(KERNEL, LIVE_DOMAIN, LIVE_APPLICATION),
                MODELS_DOMAIN to setOf(KERNEL, LIVE_DOMAIN),
                MODELS_APPLICATION to setOf(KERNEL, LIVE_DOMAIN, LIVE_APPLICATION, MODELS_DOMAIN),
                MODELS_STORE to setOf(KERNEL, LIVE_DOMAIN, MODELS_DOMAIN),
                MODELS_UI to setOf(KERNEL, LIVE_DOMAIN, LIVE_APPLICATION, MODELS_DOMAIN, MODELS_APPLICATION),
                TELEMETRY_DOMAIN to setOf(KERNEL, LIVE_DOMAIN, MODELS_DOMAIN),
                TELEMETRY_APPLICATION to
                    setOf(KERNEL, LIVE_DOMAIN, LIVE_APPLICATION, MODELS_DOMAIN, MODELS_APPLICATION, TELEMETRY_DOMAIN),
                TELEMETRY_ROOM to setOf(KERNEL, LIVE_DOMAIN, MODELS_DOMAIN, TELEMETRY_DOMAIN),
                APP to MODULES.toSet() - setOf(TESTING, APP, ARCHITECTURE_TESTS),
                ARCHITECTURE_TESTS to setOf(),
            )
    }
}
