plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.test.chatbot"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.test.chatbot"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    
    // Копируем исходники в assets для RAG анализа
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

// Задача для копирования .kt файлов в assets/project_sources
tasks.register<Copy>("copyKotlinSourcesToAssets") {
    from("src/main/java") {
        include("**/*.kt")
    }
    into("src/main/assets/project_sources")
    
    doLast {
        println("✅ Скопировано Kotlin файлов в assets/project_sources")
    }
}

// Выполняем копирование перед сборкой
tasks.named("preBuild") {
    dependsOn("copyKotlinSourcesToAssets")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // NanoHTTPD для встроенного HTTP сервера
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // OkHttp для Todoist API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Retrofit для HTTP запросов
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    
    // Coroutines для асинхронных операций
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Lifecycle компоненты
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // DataStore для сохранения настроек
    implementation(libs.androidx.datastore.preferences)
    
    // Room (SQLite) для долговременной памяти
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}