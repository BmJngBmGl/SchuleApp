plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.wunstorf.schulevault"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.wunstorf.schulevault"
        // Android 8.0 (API 26) als Minimum: deckt praktisch alle aktuellen
        // Geraete ab und erlaubt saubere Notification Channels von Anfang an.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Ersetzt das alte, jetzt deprecated "android { kotlinOptions { ... } }" -
// das ist die aktuell empfohlene Stelle fuer den Kotlin-Compiler-JVM-Target.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // --- Compose ---
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Core / Lifecycle / Activity ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // --- Klassische (View-basierte) Material-Components-Bibliothek: wird
    // NICHT fuer UI-Elemente genutzt (die App ist reines Compose), sondern
    // ausschliesslich weil sie das XML-Basis-Theme "Theme.Material3.DayNight.
    // NoActionBar" bereitstellt, das in res/values/themes.xml als Parent-Theme
    // referenziert wird (kurz sichtbar, bevor der erste Compose-Frame rendert).
    implementation("com.google.android.material:material:1.12.0")

    // --- Navigation zwischen den Screens ---
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // --- Storage Access Framework Hilfsklassen (DocumentFile) ---
    implementation("androidx.documentfile:documentfile:1.0.1")

    // --- WorkManager fuer die zeitversetzten Erinnerungen ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- Datum/Zeit-Handling ---
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // --- DataStore fuer gespeicherte Einstellungen (Vault-Ordner-URI etc.) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
