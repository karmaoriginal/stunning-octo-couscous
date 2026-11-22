plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.kromiv.core.media"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.ui)
    api(libs.androidx.media3.common)

    api(libs.coil3.compose)
    implementation(libs.coil3.gif)
    implementation(libs.coil3.network.okhttp)
    implementation(libs.avif.coder.coil)

    implementation(libs.okhttp)
    implementation(libs.ffmpeg.kit.video)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
}
