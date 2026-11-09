import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val uploadSigning = Properties().apply {
    val config = rootProject.file("keystore.properties")
    if (config.isFile) config.inputStream().use { load(it) }
}
apply(from = rootProject.file("sherpa-offline.gradle"))

// Opt-in host screenshots (-PSAHARA_RENDER_UI=true), copied from Raasta Saathi's renderTest setup.
// Normal builds and tests are unchanged: nothing below is added unless the property is set.
val renderUi = providers.gradleProperty("SAHARA_RENDER_UI").map { it.toBooleanStrict() }.orElse(false).get()
val renderSdkDirectory = layout.buildDirectory.dir("sahara-render-sdk")
val prepareRenderSdk = if (renderUi) {
    val sdk = configurations.create("saharaRenderSdk") {
        isCanBeConsumed = false
        isTransitive = false
    }
    dependencies.add(sdk.name, "org.robolectric:android-all-instrumented:15-robolectric-13954326-i7")
    tasks.register<Sync>("prepareSaharaRenderSdk") {
        from(sdk)
        into(renderSdkDirectory)
        doLast {
            val file = renderSdkDirectory.get().file("android-all-instrumented-15-robolectric-13954326-i7.jar").asFile
            val digest = MessageDigest.getInstance("SHA-512")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            check(actual == "5fc64ffccf4788154162686b3966ddd63abea8326542c465e2a7f0129fc92770d0e1e8ddcfabd7f1500e491ec02e3b4104b9e51b61fccd9cf08006c9dc02987c") { "Robolectric SDK checksum mismatch" }
        }
    }
} else null
android {
    namespace = "in.wedevit.care"
    compileSdk = 36
    defaultConfig {
        applicationId = "in.wedevit.sahara"
        minSdk = 26
        targetSdk = 36
        // -PsaharaAbi=arm64-v8a builds a phones-only APK (about half the size); the default keeps every ABI for emulators and tests.
        (project.findProperty("saharaAbi") as String?)?.let { ndk { abiFilters += it.split(",") } }
        versionCode = 11
        versionName = "0.7.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("upload") {
            uploadSigning.getProperty("storeFile")?.let { storeFile = file(it) }
            storePassword = uploadSigning.getProperty("storePassword")
            keyAlias = uploadSigning.getProperty("keyAlias")
            keyPassword = uploadSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".development"
            // Android uses on-device speech in development and store builds.
            buildConfigField("boolean", "CLOUD_AUDIO_ENABLED", "true")
            buildConfigField(
                "String",
                "CONVERSATION_TRANSCRIPTION_ENDPOINT",
                "\"https://kjnhtakqzboayqorrlue.supabase.co/functions/v1/saanthvana-conversation-transcription\"",
            )
        }
        release {
            isMinifyEnabled = true
            buildConfigField("boolean", "CLOUD_AUDIO_ENABLED", "true")
            buildConfigField(
                "String",
                "CONVERSATION_TRANSCRIPTION_ENDPOINT",
                "\"https://kjnhtakqzboayqorrlue.supabase.co/functions/v1/saanthvana-conversation-transcription\"",
            )
            if (!providers.gradleProperty("unsignedRelease").map { it.toBoolean() }.getOrElse(false)) {
                signingConfig = signingConfigs.getByName("upload")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    if (renderUi) {
        sourceSets.getByName("test").java.srcDir("src/renderTest/java")
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.all {
            prepareRenderSdk?.let { task -> it.dependsOn(task) }
            it.systemProperty("robolectric.dependency.dir", renderSdkDirectory.get().asFile.absolutePath)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.systemProperty("java.awt.headless", "true")
            providers.gradleProperty("SAHARA_RENDER_DIR").orNull?.let { directory -> it.systemProperty("sahara.render.dir", directory) }
            // One JVM per test: the walkthrough seed keeps app singletons that do not survive a Robolectric reset.
            it.setForkEvery(1)
            it.maxHeapSize = "2g"
        }
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.webkit:webkit:1.14.0")
    // QR encoding for the offline emergency card (pure Java, no network, no transitive deps).
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    // Lottie for Compose (Apache-2.0); plays the small bundled res/raw/lottie_*.json animations (SaharaLottie.kt).
    implementation("com.airbnb.android:lottie-compose:6.7.1")
    // kizitonwose Calendar (MIT): swipeable month and week rows for the Calendar and the doctor's Schedule (CareCalendarGrid.kt).
    // 2.9.1 is the newest release built for Compose 1.9 / Kotlin 2.2 (2.10.x needs Compose 1.10 and Kotlin 2.3).
    // Its POM lists ui-tooling as a runtime dependency (previews only); excluded so the release app does not ship it.
    implementation("com.kizitonwose.calendar:compose:2.9.1") { exclude(group = "androidx.compose.ui", module = "ui-tooling") }
    // Coil 3 (Apache-2.0): strip-photo thumbnails off the main thread with a memory cache and a fade-in (MedicineStripPhotos.kt).
    // Local files only, no network module. 3.3.0 is the newest release built with Kotlin 2.2 (3.4+ needs Kotlin 2.3/2.4).
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20251224")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Espresso 3.7 replaces the InputManager reflection removed by Android 16.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    if (renderUi) {
        testImplementation("org.robolectric:robolectric:4.17")
        testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
        testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
        testImplementation("androidx.test:core-ktx:1.7.0")
        testImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
        testImplementation("androidx.compose.ui:ui-test-junit4")
    }
}

// Play bundle size (sa-bundle-size): drop resources that R8 finds unreferenced from the release
// build. Native libs already stay uncompressed and page-aligned (AGP default for minSdk >= 23),
// and the bundle keeps Play's default ABI, density and language splits.
android {
    buildTypes.getByName("release") { isShrinkResources = true }
}
