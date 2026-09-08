package com.nugulmap.nativeapp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ProductionLoggingContractTest {
    @Test
    fun `production sources do not contain verbose debug or info logging`() {
        val sourceRoot = File("src/main")
        val forbiddenLogging = Regex("""(?:Log\.(?:v|d|i)|println\(|System\.(?:out|err))""")
        val violations = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .mapNotNull { source ->
                source.readLines()
                    .mapIndexedNotNull { index, line ->
                        if (forbiddenLogging.containsMatchIn(line)) {
                            "${source.relativeTo(sourceRoot)}:${index + 1}"
                        } else {
                            null
                        }
                    }
                    .takeIf(List<String>::isNotEmpty)
            }
            .flatten()
            .toList()

        assertTrue(
            violations.isEmpty(),
            "Remove production verbose/debug/info logging: ${violations.joinToString()}",
        )
    }
}
