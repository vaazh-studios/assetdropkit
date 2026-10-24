import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.vocabloot.assetdropkit.sample.shared"
        compileSdk = 36
        minSdk = 24
        compilerOptions { jvmTarget = JvmTarget.JVM_21 }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Swift's AssetDropAdapter implements the library's ApplePackStore.
            export(project(":assetdropkit"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":assetdropkit"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(libs.compose.components.resources)
        }
    }
}
