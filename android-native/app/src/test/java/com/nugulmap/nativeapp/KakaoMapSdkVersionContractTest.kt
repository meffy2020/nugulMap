package com.nugulmap.nativeapp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class KakaoMapSdkVersionContractTest {
    @Test
    fun `release uses Kakao Maps SDK 2_14_0 with the TLS authentication fix`() {
        val versionCatalog = File("../gradle/libs.versions.toml").readText()

        assertTrue(
            versionCatalog.contains("""kakaoMap = "2.14.0"""),
            "Kakao Maps SDK 2.14.0 is required for the upstream TLS authentication fix.",
        )
        assertTrue(
            versionCatalog.contains(
                """kakao-map = { group = "com.kakao.maps.open", name = "android", version.ref = "kakaoMap" }""",
            ),
            "The Kakao Maps artifact must continue to use the pinned version catalog entry.",
        )
    }
}
