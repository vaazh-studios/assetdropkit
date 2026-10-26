import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.bcv)
    alias(libs.plugins.dokka)
}

group = "com.vocabloot"
version = "0.1.1"

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.vocabloot.assetdropkit.test"
        compileSdk = 36
        minSdk = 24
        compilerOptions { jvmTarget = JvmTarget.JVM_21 }
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":assetdropkit"))
            api(libs.coroutines.core)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "assetdropkit-test", version.toString())
    pom {
        name = "AssetDropKit Test"
        description = "A scriptable FakePackSource for testing code built on AssetDropKit without a store."
        inceptionYear = "2026"
        url = "https://github.com/vaazh-studios/assetdropkit"
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
