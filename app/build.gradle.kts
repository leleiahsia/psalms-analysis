plugins {
    id("com.android.application")
}

android {
    namespace = "com.psalmsanalysis.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.psalmsanalysis.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
