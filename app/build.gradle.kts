import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Build identity. CI passes the GitHub run number, so every build installs over the previous one.
val baseVersion = "0.2"
val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1
val gitSha = System.getenv("GITHUB_SHA")?.take(7) ?: "local"

// Secrets come from the environment (CI) or from the git-ignored local.properties (local builds).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun secret(env: String, localKey: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(localKey)?.takeIf { it.isNotBlank() }

val keystorePath = secret("SIGNING_KEYSTORE_PATH", "signing.storeFile")
val keystorePassword = secret("SIGNING_KEYSTORE_PASSWORD", "signing.storePassword")
val signingKeyAlias = secret("SIGNING_KEY_ALIAS", "signing.keyAlias") ?: "glasshome"
val hasReleaseSigning = keystorePath != null && keystorePassword != null
val tmdbToken = secret("TMDB_API_TOKEN", "tmdb.token") ?: ""

android {
    namespace = "io.github.kesemb2.glasshome"
    compileSdk = 37

    defaultConfig {
        // Must never change once installed.
        applicationId = "io.github.kesemb2.glasshome"
        minSdk = 28
        targetSdk = 37
        versionCode = buildNumber
        versionName = "$baseVersion.$buildNumber"

        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "TMDB_TOKEN", "\"$tmdbToken\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = signingKeyAlias
                // PKCS12 keystores use one password for the store and the key.
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Compose's PathIterator helper. Only loaded below API 34 and only by Path.iterator(), which this app
        // never calls; stripping it keeps the APK free of native code (CI enforces this).
        jniLibs.excludes += "**/libandroidx.graphics.path.so"
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "/kotlin/**",
            "DebugProbesKt.bin",
        )
    }

    dependenciesInfo {
        // No Play Store: skip the encrypted dependency block in the APK.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.coil.core)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // Android's org.json is a stub on the JVM; unit tests use the reference implementation.
    testImplementation(libs.org.json)
}
