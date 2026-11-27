plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val distributionStore = providers.environmentVariable("HUIXUE_STORE_FILE").orNull

composeCompiler {
    // AGP's built-in Kotlin 2.2.10 has no published compose-group-mapping artifact.
    // This only disables extra Compose stack-trace mappings; normal R8 mappings remain.
    includeComposeMappingFile.set(false)
}

android {
    namespace = "com.example.blehr"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.blehr"
        minSdk = 26
        targetSdk = 37
        versionCode = 29
        versionName = "0.28"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (distributionStore != null) {
            create("distribution") {
                storeFile = file(distributionStore)
                storePassword = providers.environmentVariable("HUIXUE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("HUIXUE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("HUIXUE_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            if (distributionStore != null) signingConfig = signingConfigs.getByName("distribution")
            // Optimize the whole application with Android's standard access rules.
            // A dependency-only scope caused IllegalAccessError during AndroidX startup.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation(libs.junit)
    // Android supplies org.json at runtime; JVM tests need its real implementation.
    testImplementation("org.json:json:20260814")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Windows 的非 ASCII 输出路径可能让 Gradle 测试进程找不到类；允许本机单独指定构建目录。
providers.gradleProperty("huixueBuildDir").orNull?.let {
    layout.buildDirectory.set(file(it))
}
