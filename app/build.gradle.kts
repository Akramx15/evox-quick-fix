plugins {
    id("com.android.application")
}

android {
    namespace = "com.codex.evoxquickfix"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.codex.evoxquickfix"
        minSdk = 36
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.0-beta.1"

        testInstrumentationRunner = "android.app.Instrumentation"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
    implementation("io.github.libxposed:service:101.0.0")
    testImplementation("junit:junit:4.13.2")
}
