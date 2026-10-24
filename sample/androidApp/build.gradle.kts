plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vocabloot.assetdropkit.sample"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vocabloot.assetdropkit.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    assetPacks += listOf(":sample:demo-pack")
}

val bundletool by configurations.creating

dependencies {
    implementation(project(":sample:shared"))
    implementation(libs.androidx.activity.compose)
    bundletool(libs.bundletool)
    bundletool("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}") // install-apks needs it; bundletool's POM leaves it out
}

// Play's local testing: on-demand packs are served from the device's storage as if from Play.
// ./gradlew :sample:androidApp:installLocalTesting (one device or emulator connected)
// The Maven bundletool has no aapt2 inside; use the newest one from the SDK's build-tools.
val aapt2: Provider<String> = androidComponents.sdkComponents.sdkDirectory.map { sdk ->
    val buildTools = sdk.asFile.resolve("build-tools")
    buildTools.listFiles().orEmpty().filter { it.resolve("aapt2").isFile }.maxByOrNull { it.name }?.resolve("aapt2")?.path
        ?: error("no aapt2 in $buildTools")
}
val apks: Provider<RegularFile> = layout.buildDirectory.file("outputs/local-testing/sample.apks")
val bundle: Provider<RegularFile> = layout.buildDirectory.file("outputs/bundle/debug/androidApp-debug.aab")
val buildLocalTestingApks by tasks.registering(JavaExec::class) {
    dependsOn("bundleDebug")
    classpath = bundletool
    mainClass.set("com.android.tools.build.bundletool.BundleToolMain")
    inputs.file(bundle)
    outputs.file(apks)
    val bundlePath = bundle.map { it.asFile.path }
    val apksPath = apks.map { it.asFile.path }
    val aapt2Path = aapt2
    argumentProviders += CommandLineArgumentProvider {
        listOf("build-apks", "--local-testing", "--overwrite", "--bundle=${bundlePath.get()}", "--output=${apksPath.get()}", "--aapt2=${aapt2Path.get()}")
    }
}
tasks.register<JavaExec>("installLocalTesting") {
    dependsOn(buildLocalTestingApks)
    classpath = bundletool
    mainClass.set("com.android.tools.build.bundletool.BundleToolMain")
    val apksPath = apks.map { it.asFile.path }
    argumentProviders += CommandLineArgumentProvider { listOf("install-apks", "--apks=${apksPath.get()}") }
}
