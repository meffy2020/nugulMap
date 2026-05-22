package com.nugulmap.nativeapp.core.auth

enum class OAuthProvider(val id: String, val displayName: String) {
    Kakao("kakao", "카카오"),
    Naver("naver", "네이버"),
    Google("google", "구글"),
}
