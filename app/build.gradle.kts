import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Kept outside this script so the CI can bump it on every push to main (docs/release.md).
val appVersion = Properties().apply {
    load(providers.fileContents(layout.projectDirectory.file("version.properties")).asText.get().reader())
}

android {
    namespace = "org.opensources.courses"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.opensources.courses"
        // Product decision: Android 17 (API 37) only, no backward compatibility layer.
        minSdk = 37
        targetSdk = 37
        versionCode = checkNotNull(appVersion.getProperty("versionCode")).toInt()
        versionName = checkNotNull(appVersion.getProperty("versionName"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    androidResources {
        // Interface languages (English in the unqualified `values`, see res/resources.properties).
        // The generated locale config lists them in the app's page of the Android settings.
        localeFilters += listOf("en", "fr", "de", "es", "it", "pt")
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "/META-INF/androidx/**/LICENSE.txt"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // Dependency updates are reviewed with `dependencyUpdates`: a library released upstream
        // must not break an unchanged build.
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

room {
    // Exported schemas are the reference for future migrations (see docs/adr/0020-migrations-room-sans-perte.md).
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    // QR code of the Home Assistant token, without Google Play services (works on GrapheneOS):
    // CameraX for the preview and frames, ZXing (open source, pure Java) for decoding.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.zxing.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose UI tests inject input through Espresso; versions before 3.7 call an InputManager
    // method removed in Android 17.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // MigrationTestHelper: migrations are validated against the schemas exported in app/schemas.
    androidTestImplementation(libs.androidx.room.testing)
}
