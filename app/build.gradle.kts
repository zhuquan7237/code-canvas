import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.nous.codecanvas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nous.codecanvas"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.1.7"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    signingConfigs {
        create("release") {
            val signingPropsFile = rootProject.file("signing.properties")
            if (signingPropsFile.exists()) {
                val props = Properties()
                signingPropsFile.inputStream().use { props.load(it) }
                val storePath = props.getProperty("storeFile")
                if (storePath != null) {
                    storeFile = file(storePath)
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            } else {
                val envStorePath = System.getenv("CODE_CANVAS_KEYSTORE_PATH")
                if (envStorePath != null && file(envStorePath).exists()) {
                    storeFile = file(envStorePath)
                    storePassword = System.getenv("CODE_CANVAS_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("CODE_CANVAS_KEY_ALIAS")
                    keyPassword = System.getenv("CODE_CANVAS_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile?.exists() == true) {
                signingConfig = releaseConfig
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    useLibrary("android.test.runner")
    useLibrary("android.test.base")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
