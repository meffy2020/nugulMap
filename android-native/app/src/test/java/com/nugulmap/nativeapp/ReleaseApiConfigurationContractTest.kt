package com.nugulmap.nativeapp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ReleaseApiConfigurationContractTest {
    @Test
    fun `release tasks fail closed unless the API base is the canonical HTTPS endpoint`() {
        val buildScript = File("build.gradle.kts").readText()

        assertTrue(
            buildScript.contains("""val canonicalReleaseApiBaseUrl = "https://api.nugulmap.com""""),
            "Declare the one canonical release API endpoint.",
        )
        assertTrue(
            buildScript.contains("val releaseApiBaseUrlProblem"),
            "Release configuration must explain a rejected API endpoint.",
        )
        assertTrue(
            buildScript.contains("requestedReleaseTask && releaseApiBaseUrlProblem != null"),
            "Every requested release task must fail before producing output for a non-canonical endpoint.",
        )
        assertTrue(
            buildScript.contains(
                "buildConfigField(\"String\", \"NUGUL_API_BASE_URL\", \"\\\"\$canonicalReleaseApiBaseUrl\\\"\")",
            ),
            "The release BuildConfig must use the canonical endpoint explicitly.",
        )
        assertTrue(
            buildScript.contains("NUGUL_DEBUG_API_BASE_URL"),
            "Debug builds must retain their emulator and local fixture override.",
        )
    }
}
