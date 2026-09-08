import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun localPropertyOrEnv(name: String): String =
    localProperties.getProperty(name)?.trim()?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.trim()?.takeIf { it.isNotBlank() }
        ?: ""

fun environmentOrLocalProperty(name: String): String =
    System.getenv(name)?.trim()?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.trim()?.takeIf { it.isNotBlank() }
        ?: ""

val canonicalReleaseApiBaseUrl = "https://api.nugulmap.com"
val configuredReleaseApiBaseUrl = environmentOrLocalProperty("NUGUL_API_BASE_URL")
    .ifBlank { canonicalReleaseApiBaseUrl }
    .trim()
    .trimEnd('/')
val releaseApiBaseUrlProblem = configuredReleaseApiBaseUrl
    .takeUnless { it == canonicalReleaseApiBaseUrl }
    ?.let {
        "Release API base URL must be exactly $canonicalReleaseApiBaseUrl."
    }

val releaseSigningValues = linkedMapOf(
    "ANDROID_KEYSTORE_PATH" to environmentOrLocalProperty("ANDROID_KEYSTORE_PATH"),
    "ANDROID_KEYSTORE_PASSWORD" to environmentOrLocalProperty("ANDROID_KEYSTORE_PASSWORD"),
    "ANDROID_KEY_ALIAS" to environmentOrLocalProperty("ANDROID_KEY_ALIAS"),
    "ANDROID_KEY_PASSWORD" to environmentOrLocalProperty("ANDROID_KEY_PASSWORD"),
)
val releaseStoreFile = releaseSigningValues.getValue("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = releaseSigningValues.getValue("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningValues.getValue("ANDROID_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValues.getValue("ANDROID_KEY_PASSWORD")
val releaseStorePath = releaseStoreFile.takeIf { it.isNotBlank() }?.let(rootProject::file)
val missingReleaseSigningNames = releaseSigningValues.filterValues { it.isBlank() }.keys
val releaseSigningProblem = when {
    missingReleaseSigningNames.isNotEmpty() ->
        "Release signing is not configured. Missing: ${missingReleaseSigningNames.joinToString()}."
    releaseStorePath?.isFile != true ->
        "Release signing is not configured. ANDROID_KEYSTORE_PATH must point to an existing file."
    else -> null
}
val hasReleaseSigning = releaseSigningProblem == null

val requestedReleaseEntryPoints = setOf("assembleRelease", "bundleRelease")
val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
val requestedReleaseTask = requestedTaskNames.any { it.contains("Release", ignoreCase = true) }
if (requestedReleaseTask && releaseApiBaseUrlProblem != null) {
    throw GradleException(releaseApiBaseUrlProblem)
}
if (requestedTaskNames.any(requestedReleaseEntryPoints::contains) && releaseSigningProblem != null) {
    throw GradleException(releaseSigningProblem)
}

android {
    namespace = "com.nugulmap.nativeapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nugulmap.nativeapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a")
        }

        val kakaoNativeAppKey = localPropertyOrEnv("KAKAO_NATIVE_APP_KEY")
            .trim()
        buildConfigField("String", "NUGUL_API_BASE_URL", "\"$canonicalReleaseApiBaseUrl\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = requireNotNull(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            val debugApiBaseUrl = environmentOrLocalProperty("NUGUL_DEBUG_API_BASE_URL")
                .ifBlank { localPropertyOrEnv("NUGUL_API_BASE_URL").ifBlank { "https://api.nugulmap.com" } }
                .trim()
                .trimEnd('/')
            buildConfigField("String", "NUGUL_API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            buildConfigField("String", "NUGUL_API_BASE_URL", "\"$canonicalReleaseApiBaseUrl\"")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val verifyReleaseSigningConfiguration = tasks.register("verifyReleaseSigningConfiguration") {
    group = "verification"
    description = "Fails unless the Play upload-key signing configuration is complete."
    doLast {
        if (releaseSigningProblem != null) {
            throw GradleException(releaseSigningProblem)
        }
    }
}

tasks.register("checkReleaseSigning") {
    group = "verification"
    description = "Compatibility alias for verifyReleaseSigningConfiguration."
    dependsOn(verifyReleaseSigningConfiguration)
}

tasks.matching { it.name in requestedReleaseEntryPoints }.configureEach {
    dependsOn(verifyReleaseSigningConfiguration)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kakao.map)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(kotlin("test"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
