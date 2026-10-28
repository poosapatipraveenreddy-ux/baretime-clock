plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun prop(name: String, default: String) = (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: default

android {
    namespace = "app.baretime.clock"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.baretime.clock"
        minSdk = 26
        targetSdk = 34
        versionCode = prop("versionCode", "1").toInt()
        versionName = prop("versionName", "1.0.0")
        buildConfigField("String", "SITE_URL", "\"${prop("siteUrl", "")}\"")
        buildConfigField("String", "GITHUB_REPO", "\"${prop("githubRepo", "")}\"")
        // Raise when the app gains a feature that newer website files depend on.
        buildConfigField("int", "BRIDGE_LEVEL", "2")
    }

    // One fixed key lives in GitHub Secrets. Without it the build is signed with a
    // throwaway debug key: it installs, but later builds can't update over it.
    val ks = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ks != null) create("release") {
            storeFile = file(ks)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (ks != null) "release" else "debug")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// The website (../../web) is bundled into the APK as the offline copy of the designer.
val syncWeb by tasks.registering(Copy::class) {
    from(rootProject.file("../web"))
    into(layout.buildDirectory.dir("generated/web-assets/web"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/web-assets"))
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(syncWeb) }

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
