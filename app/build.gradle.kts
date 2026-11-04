plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mbaliga.csapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mbaliga.csapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from the environment only (CI secrets / your shell); never from the repo.
    // Without them the release build is left unsigned and the debug-signed APK is the sideload build.
    val ksPath = System.getenv("CSAPP_KEYSTORE_PATH")
    signingConfigs {
        if (ksPath != null && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("CSAPP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CSAPP_KEY_ALIAS")
                keyPassword = System.getenv("CSAPP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            all { t ->
                // Optional offline Robolectric runtime (CI downloads it normally).
                System.getenv("ROBOLECTRIC_DEPS_DIR")?.let { d ->
                    t.systemProperty("robolectric.offline", "true")
                    t.systemProperty("robolectric.dependency.dir", d)
                }
            }
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    // Constellation-wide crash-recovery harness (dev.aarso:crash-recovery, single-sourced from
    // mbaliga/Shared-Libraries-asoc — see settings.gradle.kts). Local-only: captures an
    // uncaught crash to a private file and shows a recovery screen on next launch instead of
    // bricking. No network dependency, nothing phoned home.
    implementation("dev.aarso:crash-recovery:1.5.0")

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.okhttp)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.json.schema.validator)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room's MigrationTestHelper reads the exported schema JSONs as assets. Robolectric sees the debug
// variant's merged assets, so the (tiny) schema JSONs ride in debug builds only — never in release.
android.sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
