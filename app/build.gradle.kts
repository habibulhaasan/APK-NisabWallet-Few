plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.hasan.nisabwallet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hasan.nisabwallet"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "3.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core / Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // Non-M3 "material" is needed for PullRefreshIndicator/pullRefresh, used in
    // DashboardScreen.kt and TransactionsScreen.kt.
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle / ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Firebase (Auth + Firestore) — used directly by every ViewModel
    implementation(platform(libs.firebase.bom))

    // Coroutines <-> Firebase Task interop (.await())
    implementation(libs.kotlinx.coroutines.play.services)

    // Firebase (Auth + Firestore) — used directly by every ViewModel
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)       // Removed .ktx
    implementation(libs.firebase.firestore)  // Removed .ktx
    implementation(libs.material)

    implementation("org.jsoup:jsoup:1.17.2")
}