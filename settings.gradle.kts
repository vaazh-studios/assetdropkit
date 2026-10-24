rootProject.name = "assetdropkit"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":assetdropkit")
include(":assetdropkit-test")
include(":sample:shared")
include(":sample:androidApp")
include(":sample:demo-pack")
