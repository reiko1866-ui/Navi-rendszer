import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    localFile.inputStream().use { localProps.load(it) }
}

fun setting(name: String): String {
    val raw = localProps.getProperty(name) ?: System.getenv(name) ?: ""
    return raw.trim()
}

val mapboxPublicToken = setting("MAPBOX_PUBLIC_TOKEN").filter { it != '"' && it != '\n' && it != '\r' && it != '<' && it != '>' && it != '&' }

val generateMapboxToken = tasks.register("generateMapboxToken") {
    val outFile = layout.buildDirectory.file("generated/mapboxToken/res/values/mapbox_access_token.xml")
    outputs.file(outFile)
    doLast {
        val file = outFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="mapbox_access_token" translatable="false">$mapboxPublicToken</string>
            </resources>
            """.trimIndent()
        )
    }
}

val syncVoiceAssets = tasks.register<Copy>("syncVoiceAssets") {
    from(rootProject.file("../hang"))
    into(layout.buildDirectory.dir("generated/voice-assets/hang"))
    include("hozzarendeles.json", "esemenyek.json", "clips/**")
}

android {
    namespace = "hu.toonnav.map"
    compileSdk = 35

    defaultConfig {
        applicationId = "hu.toonnav.map"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"$mapboxPublicToken\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/mapboxToken/res"))
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/voice-assets"))
}

tasks.named("preBuild").configure {
    dependsOn(generateMapboxToken, syncVoiceAssets)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Navigation SDK 3.30.7 ships with Maps SDK 11.30.7. Do not pin a different Maps version.
    implementation("com.mapbox.navigationcore:android-ndk27:3.30.7")
    implementation("com.mapbox.navigationcore:ui-maps-ndk27:3.30.7")
    testImplementation("junit:junit:4.13.2")
}
