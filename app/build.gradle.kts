plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  // alias(libs.plugins.secrets)              // УДАЛЕНО
  // alias(libs.plugins.google.services)      // УДАЛЕНО
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "ru.discoveryfm.player"
  compileSdk = 35

  defaultConfig {
    applicationId = "ru.discoveryfm.player"
    minSdk = 24
    targetSdk = 35
    versionCode = 3
    versionName = "1.2.0-b1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      // Берём пароли из gradle.properties (через project.properties)
      storePassword = project.properties["STORE_PASSWORD"] as? String ?: System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = project.properties["KEY_PASSWORD"] as? String ?: System.getenv("KEY_PASSWORD")

      // Подпись v1 (JAR) + v2 — требуется для установки
      // на Xiaomi/HyperOS и старых Android-устройств
      enableV1Signing = true
      enableV2Signing = true
    }
    create("debugConfig") {
      storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"

      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Подпись debug-ключом для распространения APK напрямую
      // (Android 16 отклоняет самоподписанные сертификаты без v1)
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
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
    buildConfig = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))   // УДАЛЕНО

  // === ANDROIDX ===
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.media)

  // === COMPOSE ===
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)

  // === ROOM ===
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // === СЕТЬ ===
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.okhttp)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  ksp(libs.moshi.kotlin.codegen)

  // === КОРУТИНЫ ===
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // === ДРУГИЕ ===
  implementation(libs.coil.compose)
  implementation(libs.jsoup)

  // === FIREBASE — УДАЛЕНО ===
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.appcheck.recaptcha)

  // === ТЕСТЫ ===
  testImplementation(libs.junit)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}