// Top-level build file: hier werden nur Plugin-Versionen deklariert,
// angewendet wird jeweils im app-Modul (siehe app/build.gradle.kts).
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
