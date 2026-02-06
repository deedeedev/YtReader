plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    namespace = "com.deedeedev.ytreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.deedeedev.ytreader"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // ----------------------------------------------------------------
    // 1. LE TUE DIPENDENZE ORIGINALI (NON RIMUOVERLE)
    // ----------------------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Testing (Lasciamoli, sono utili)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ----------------------------------------------------------------
    // 2. NUOVE DIPENDENZE PER IL PROGETTO "SUBREADER"
    // ----------------------------------------------------------------

    // --- NAVIGAZIONE ---
    // Gestione delle schermate con Compose
    implementation("androidx.navigation:navigation-compose:2.8.6")

    // --- NEWPIPE EXTRACTOR (YouTube Core) ---
    // Motore per estrarre info e sottotitoli senza API Key
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.25.1")
    // Libreria date/time richiesta da NewPipe
    implementation("com.jakewharton.threetenabp:threetenabp:1.4.9")

    // --- NETWORKING & PARSING ---
    // OkHttp per scaricare i file dei sottotitoli
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Jsoup per pulire l'XML sporco di YouTube
    implementation("org.jsoup:jsoup:1.17.2")

    // --- DATABASE LOCALE (ROOM) ---
    // Per salvare la libreria dei sottotitoli
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion") // Supporto Kotlin Coroutines
    ksp("androidx.room:room-compiler:$roomVersion") // Compilatore (richiede il plugin KSP aggiunto prima)
}