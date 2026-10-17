plugins {
    id("com.android.application")
}

android {
    namespace = "dev.doji.adx"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.doji.adx"
        minSdk = 28
        targetSdk = 37
        versionCode = 15
        versionName = "0.15.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("org.luckypray:dexkit:2.3.0")
    testImplementation("junit:junit:4.13.2")
}
