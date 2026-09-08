package com.nugulmap.nativeapp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseAbiContractTest {
    @Test
    fun `packaged abis match the arm variants supported by Kakao Map SDK`() {
        val buildScript = File("build.gradle.kts").readText()
        val abiFilters = buildScript
            .substringAfter("ndk {")
            .substringBefore("}")

        assertTrue(abiFilters.contains("""abiFilters += setOf("armeabi-v7a", "arm64-v8a")"""))
        assertFalse(abiFilters.contains("x86"))
    }
}
