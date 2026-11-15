import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/voice-assets"))
}

tasks.named("preBuild").configure {
    dependsOn(syncVoiceAssets)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.maplibre.gl:android-sdk-vulkan:11.13.5")
    implementation("com.google.android.filament:filament-android:1.75.0")
    implementation("com.google.android.filament:gltfio-android:1.75.0")
    implementation("com.google.android.filament:filament-utils-android:1.75.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

tasks.withType<Test>().configureEach {
    doFirst {
        classpath = files(classpath.files.sortedWith(compareBy<java.io.File> { file ->
            when {
                file.name.startsWith("json-") -> 0
                file.name.contains("android.jar") || file.name.contains("mockable") -> 2
                else -> 1
            }
        }))
    }
}
