plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.erol.glasskeys"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.erol.glasskeys"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
