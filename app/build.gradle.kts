plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.chacho.streetbite"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.chacho.streetbite"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("com.google.maps.android:maps-compose:8.4.0")

    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.appcompat:appcompat:1.7.1")

    implementation("androidx.cardview:cardview:1.0.0")

    // Peticiones HTTP a Supabase
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    implementation("androidx.core:core-splashscreen:1.2.0")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}