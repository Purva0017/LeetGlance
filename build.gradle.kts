// Top-level build file where you can add configuration options common to all sub-projects/modules.
//plugins {
//    alias(libs.plugins.android.application) apply false
//    alias(libs.plugins.kotlin.compose) apply false
//}

// Root-level build.gradle.kts

plugins {
    id("com.android.application")                           version "8.3.2"  apply false
    id("com.android.library")                               version "8.3.2"  apply false
    id("org.jetbrains.kotlin.android")                      version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization")         version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose")               version "2.0.21" apply false
}