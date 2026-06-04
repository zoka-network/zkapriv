plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.zoka.zkapriv.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ZOKA_PRIVATE_SEND_MODE", "\"remote-seed\"")
        buildConfigField("int", "ZOKA_PRIVATE_PROOF_VERSION", "1")
        buildConfigField("String", "ZOKA_PRIVATE_PROOF_PROFILE", "\"private_tx\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("staging") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("String", "ZOKA_PRIVATE_SEND_MODE", "\"local-self-custody\"")
            // Option B test: prove on-device with the v1 profile, which the nodes
            // already verify (they lack a matching v2-i1 CRS). The checked=false
            // param-load fix in the native lib still applies, so this measures
            // whether v1 on-device proving is fast enough without a node-side
            // CRS deployment. Flip back to 2 / private_tx_v2_i1 once the nodes
            // carry the v2-i1 bundle.
            buildConfigField("int", "ZOKA_PRIVATE_PROOF_VERSION", "1")
            buildConfigField("String", "ZOKA_PRIVATE_PROOF_PROFILE", "\"private_tx\"")
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":core"))
    implementation(project(":security"))

    // Hilt
    implementation("com.google.dagger:hilt-android:2.47")
    kapt("com.google.dagger:hilt-android-compiler:2.47")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Crypto - BouncyCastle for Ed25519 signing and Blake3 hashing
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
}
