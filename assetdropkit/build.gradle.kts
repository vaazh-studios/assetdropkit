import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.bcv)
    alias(libs.plugins.dokka)
}

group = "com.vocabloot"
version = "0.1.0"

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "com.vocabloot.assetdropkit"
        compileSdk = 36
        minSdk = 24
        compilerOptions { jvmTarget = JvmTarget.JVM_21 }
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            api(libs.kotlinx.io.core)
            implementation(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(project(":assetdropkit-test"))
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        androidMain.dependencies {
            api(libs.play.asset.delivery.ktx)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "assetdropkit", version.toString())
    pom {
        name = "AssetDropKit"
        description = "Kotlin Multiplatform asset packs downloaded on first use from the stores: Google Play Asset Delivery on Android, Apple-hosted Background Assets on iOS 26+ and On-Demand Resources on iOS 17 and 18. No server, no hosting bill. Built for Vocabloot (https://vocabloot.com)."
        inceptionYear = "2026"
        url = "https://vaazh-studios.github.io/assetdropkit/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "vaazh-studios"
                name = "Vaazh Studios"
                url = "https://github.com/vaazh-studios"
            }
        }
        scm {
            url = "https://github.com/vaazh-studios/assetdropkit"
            connection = "scm:git:git://github.com/vaazh-studios/assetdropkit.git"
            developerConnection = "scm:git:ssh://git@github.com/vaazh-studios/assetdropkit.git"
        }
    }
}

@OptIn(kotlinx.validation.ExperimentalBCVApi::class)
apiValidation {
    klib { enabled = true }
}

dokka {
    moduleName.set("AssetDropKit")
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
        sourceLink {
            localDirectory.set(file("src"))
            remoteUrl("https://github.com/vaazh-studios/assetdropkit/tree/main/assetdropkit/src")
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration.html {
        footerMessage.set("AssetDropKit, Apache 2.0, Vaazh Studios")
    }
}
