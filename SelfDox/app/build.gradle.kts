plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.selfdox.rtspserver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.selfdox.rtspserver"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
        jniLibs {
            pickFirsts += "**/*.so"
        }
    }
}

dependencies {
    // DECISION: Using exact versions from spec — do not upgrade without testing
    implementation("com.github.pedroSG94:RTSP-Server:1.3.6")
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.1")

    // Jetpack Lifecycle
    implementation("androidx.lifecycle:lifecycle-service:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-viewbinding")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Coroutines + Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Ktor for REST API
    // DECISION: Adding explicitly — if duplicate class errors occur, remove these
    implementation("io.ktor:ktor-server-netty:2.3.13")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.13")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
