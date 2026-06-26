import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val signingStoreFilePath = keystoreProperties.getProperty("storeFile")
    ?: System.getenv("ANDROID_KEYSTORE_FILE")
val signingStorePassword = keystoreProperties.getProperty("storePassword")
    ?: System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
val signingKeyAlias = keystoreProperties.getProperty("keyAlias")
    ?: System.getenv("ANDROID_KEY_ALIAS") ?: ""
val signingKeyPassword = keystoreProperties.getProperty("keyPassword")
    ?: System.getenv("ANDROID_KEY_PASSWORD") ?: ""

abstract class GitTagValueSource : ValueSource<String, GitTagValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        var rootDir: String
    }
    override fun obtain(): String {
        return try {
            val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                .directory(File(parameters.rootDir))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val tag = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0) tag else ""
        } catch (e: Exception) {
            ""
        }
    }
}

val gitTagProvider = providers.of(GitTagValueSource::class.java) {
    parameters.rootDir = rootProject.projectDir.absolutePath
}

val gitTag: String = gitTagProvider.get()

val appVersionName: String = (project.findProperty("versionName") as? String)
    ?: if (gitTag.isNotEmpty()) gitTag.removePrefix("v") else "0.0.0-local"

val appVersionCode: Int = (project.findProperty("versionCode") as? String)?.toIntOrNull()
    ?: if (gitTag.isNotEmpty()) {
        val version = gitTag.removePrefix("v")
        val parts = version.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        major * 1000000 + minor * 1000 + patch + 1
    } else 1

configure<ApplicationExtension> {
    namespace = "com.deedeedev.ytreader"
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (signingStoreFilePath != null) {
                storeFile = rootProject.file(signingStoreFilePath)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.deedeedev.ytreader"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingStoreFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // NewPipe Extractor
    implementation(project(":extractor"))
    implementation(libs.threetenabp)

    // Networking & Parsing
    implementation(libs.okhttp)
    implementation(libs.google.gson)
    implementation(libs.jsoup)

    // Local DB
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Encrypted preferences for API key
    implementation(libs.androidx.security.crypto)
}
