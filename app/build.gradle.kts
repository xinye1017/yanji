plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.yanji"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.yanji"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // 插桩测试入口（app/src/androidTest）。之前从未声明过，因此 androidTest 目录一直是空的。
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            // 开启代码压缩与资源压缩：缩包、去掉无用代码与调试符号。
            // 它不构成安全边界，但能显著减少无意暴露的符号与字符串。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    lint {
        abortOnError = false
        checkDependencies = true
        textReport = true
        htmlReport = true
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}


kotlin {
    jvmToolchain(17)
}

// Room schema export: versioned JSON schemas land in app/schemas/<version>.json
// (git-committed so migration tests can build any historical version in isolation).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room 2.8 是 KMP 构件，`room-runtime` 同时有 android / jvm 两个变体。
// JVM 单元测试用的 `room-testing-jvm`（MigrationTestHelper）是针对 **jvm 变体** 编译的，
// 而 app 模块默认把 `room-runtime` 解析成 **android 变体**；两者混在同一个 test 类路径上
// 会在运行期抛 `NoSuchMethodError: DatabaseConfiguration.<init>(...)`。
// 因此只在 test* 配置里把这两个坐标换成 jvm 变体，主代码仍使用 android 变体。
configurations.configureEach {
    // 注意 AGP 的单元测试配置名形如 `debugUnitTestRuntimeClasspath`，**不是**以 "test" 开头。
    if (name.contains("UnitTest")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.room:room-runtime"))
                .using(module("androidx.room:room-runtime-jvm:${libs.versions.room.get()}"))
            substitute(module("androidx.room:room-migration"))
                .using(module("androidx.room:room-migration-jvm:${libs.versions.room.get()}"))
        }
    }
}

dependencies {
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.phosphor.icon)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // 本地 JVM 单测需要真实的 org.json 实现（Android 提供的是 stub，方法一调用就抛 not mocked）
  testImplementation(libs.org.json)

  // JSON 备份导出/导入（@Serializable 来自 kotlinx-serialization-core，
  // Json 编解码器来自本库；两者都需要显式可见，不能依赖传递引入）
  implementation(libs.kotlinx.serialization.json)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  // Migration testing (JVM MigrationTestHelper + bundled SQLite driver — pure JVM, no device)
  testImplementation(libs.androidx.room.testing.jvm)
  testImplementation(libs.androidx.sqlite.bundled.jvm)
}
