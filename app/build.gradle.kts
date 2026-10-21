plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release builds need an HTTPS API URL: pass it with -PapiBaseUrl=https://... (see tasks check below).
val apiBaseUrl = providers.gradleProperty("apiBaseUrl")

android {
    namespace = "com.example.disputeportal"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.disputeportal"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    // Optional release signing. Set these in ~/.gradle/gradle.properties or pass with -P; never commit them.
    signingConfigs {
        if (providers.gradleProperty("releaseKeystorePath").isPresent) {
            create("release") {
                storeFile = file(providers.gradleProperty("releaseKeystorePath").get())
                storePassword = providers.gradleProperty("releaseStorePassword").get()
                keyAlias = providers.gradleProperty("releaseKeyAlias").get()
                keyPassword = providers.gradleProperty("releaseKeyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the Android emulator's alias for the host machine.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.orNull.orEmpty()}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    // Instrumented tests run on a device. MockWebServer stands in for the API.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // Compose's test rule drives Espresso; older Espresso cannot inject input on API 37.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    // Must match the OkHttp version Retrofit resolves to (4.12.0).
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Fail release builds early when no HTTPS API URL is provided, instead of shipping a broken app.
tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease") {
        doFirst {
            require(apiBaseUrl.orNull?.startsWith("https://") == true) {
                "Pass an https apiBaseUrl, for example -PapiBaseUrl=https://api.example.com/"
            }
        }
    }
}
