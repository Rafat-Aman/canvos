import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing comes from keystore.properties (local builds, never committed)
// or from CANVOS_KEYSTORE_* environment variables (CI). See README > Building.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "CANVOS_KEYSTORE_FILE")

fun schoolProp(name: String, default: String) =
    "\"" + (providers.gradleProperty("canvos.$name").orNull ?: default).replace("\"", "\\\"") + "\""

android {
    namespace = "com.rafat.canvos"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rafat.canvos"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Canvas site the app signs in to. Override in gradle.properties to build for another school.
        buildConfigField("String", "SCHOOL_URL", schoolProp("schoolUrl", "https://northsouth.instructure.com"))
        buildConfigField("String", "SCHOOL_NAME", schoolProp("schoolName", "North South University"))
        buildConfigField("String", "SCHOOL_SHORT_NAME", schoolProp("schoolShortName", "NSU"))
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "CANVOS_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "CANVOS_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "CANVOS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release key, fall back to the debug key so anyone can still build and install.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

// Live tests against a real Canvas account run only when these are set (read-only).
tasks.withType<Test>().configureEach {
    System.getenv("CANVAS_URL")?.let { environment("CANVAS_URL", it) }
    System.getenv("CANVAS_TOKEN")?.let { environment("CANVAS_TOKEN", it) }
    outputs.upToDateWhen { System.getenv("CANVAS_TOKEN") == null }
    testLogging { events("failed", "skipped"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// APKs are named canvos-<version>-<buildType>.apk
base { archivesName = "canvos-${android.defaultConfig.versionName}" }
