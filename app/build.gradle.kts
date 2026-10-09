plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.baselineprofile)
}

val releaseSigningStoreFile = providers.environmentVariable("YANJI_SIGNING_STORE_FILE").orNull
val releaseSigningStorePassword = providers.environmentVariable("YANJI_SIGNING_STORE_PASSWORD").orNull
val releaseSigningKeyAlias = providers.environmentVariable("YANJI_SIGNING_KEY_ALIAS").orNull
val releaseSigningKeyPassword = providers.environmentVariable("YANJI_SIGNING_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigningConfig = releaseSigningValues.all { !it.isNullOrBlank() }

require(!hasAnyReleaseSigningValue || hasCompleteReleaseSigningConfig) {
    "Release signing is only partially configured. Set all YANJI_SIGNING_* environment variables or none."
}

android {
    namespace = "com.example.yanji"
    compileSdk = 36
    // 与根 build.gradle.kts 的 yanjiNdkVersion 同源：node_modules 下的 RN 原生库读不到
    // 这里的值，必须由根工程统一下发，否则它们会各自落到「已安装的最新 NDK」而与
    // react-android AAR 自带的 libc++_shared.so 符号集错配，运行期 dlopen 崩溃。
    ndkVersion = rootProject.extra["yanjiNdkVersion"] as String
    defaultConfig {
        applicationId = "com.example.yanji"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
        // 插桩测试入口（app/src/androidTest）。之前从未声明过，因此 androidTest 目录一直是空的。
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                // 与 ReactAndroid AAR 及各 RN 原生库一致：共用同一份 libc++_shared.so。
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    testOptions {
        // YANJI-A 审计实验（2026-09-16）：把该开关置为 false 后，SecretStoreTest 有 5 个
        // 用例在 main 代码的 android.util.Log.* 调用点抛 "not mocked" RuntimeException
        // （fail-closed 路径都会打日志）。即该开关当前是**承重**的，不是冗余配置；
        // 代价是 JVM 单测会静默跳过这些 Android API 调用。
        // 后续若给数据层引入日志 seam（内部 Logger 接口），可再评估关闭。
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        if (hasCompleteReleaseSigningConfig) {
            create("production") {
                storeFile = file(releaseSigningStoreFile!!)
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            // 开启代码压缩与资源压缩：缩包、去掉无用代码与调试符号。
            // 它不构成安全边界，但能显著减少无意暴露的符号与字符串。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Local/PR release builds are intentionally unsigned. The tag-only release workflow
            // supplies the production signing identity through ephemeral environment variables.
            signingConfig = signingConfigs.findByName("production")
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      aidl = false
      buildConfig = false
      shaders = false
      prefab = true
    }

    // libappmodules.so —— 第三方 RN 原生库 TurboModule provider 与 Fabric 组件描述符的
    // 宿主（源码见 src/main/jni）。必须由 Gradle 从源码构建。
    // 曾把编译产物当预编译 .so 提交进 src/main/jniLibs/：源码一改二进制不跟着变，
    // 运行期表现就是「能装上、一点进去就闪退」，且 APK 里装的永远是旧二进制、无从排查。
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
        }
    }

    lint {
        // Lint errors are a real build gate. Existing warnings stay visible in the archived report;
        // they are not silently disabled or converted into an ever-growing baseline.
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = true
        textReport = true
        htmlReport = true
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
      jniLibs {
        pickFirsts += listOf(
          "**/libc++_shared.so",
          "**/libfbjni.so",
          "**/libjsi.so",
          "**/libreactnative.so"
        )
      }
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
}


kotlin {
    jvmToolchain(17)
}

// Room schema export: versioned JSON schemas land in app/schemas/<version>.json
// (git-committed so migration tests can build any historical version in isolation).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// :app 的 CMake 配置会把各 RN 原生库生成的 C++ codegen（TurboModule provider +
// Fabric 组件描述符）编进 libappmodules.so，见 src/main/jni/CMakeLists.txt。
// 那些源码是各库 generateCodegenArtifactsFromSchema 的产物，AGP 不会自动给 :app 的
// CMake 配置任务建立这条依赖（本工程不给 :app 应用 RNGP，autolinking 整条链缺失）。
// 不显式补上，干净构建时 CMake 的 file(GLOB) 会拿到空列表，libappmodules.so 里没有
// 任何第三方 TurboModule provider，运行期 WorkletsModule 解析为 null 而启动闪退。
tasks.matching { it.name.startsWith("configureCMake") }.configureEach {
    dependsOn(
        ":react-native-worklets:generateCodegenArtifactsFromSchema",
        ":react-native-reanimated:generateCodegenArtifactsFromSchema",
        ":react-native-svg:generateCodegenArtifactsFromSchema",
        ":react-native-safe-area-context:generateCodegenArtifactsFromSchema"
    )
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
  baselineProfile(project(":baselineprofile"))
  coreLibraryDesugaring(libs.desugar.jdk.libs)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.profileinstaller)

  // React Native Add-to-App
  implementation(libs.react.android)
  implementation(libs.hermes.android)
  implementation(libs.fbjni)

  // RN 原生库（autolinking 由 settings.gradle.kts 的 ReactSettingsExtension 提供，
  // 这里显式声明子工程依赖；不应用 RN app 插件以保持既有 bundle 构建流程）
  implementation(project(":react-native-reanimated"))
  implementation(project(":react-native-worklets"))
  implementation(project(":react-native-svg"))
  implementation(project(":react-native-safe-area-context"))

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

  // Navigation：当前为 Navigation.kt 内的手写路由（YanjiTab + YanjiSubScreen），
  // 不依赖 androidx.navigation3。YANJI-A 审计已移除未使用的 navigation3 依赖。

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  // Migration testing (JVM MigrationTestHelper + bundled SQLite driver — pure JVM, no device)
  testImplementation(libs.androidx.room.testing.jvm)
  testImplementation(libs.androidx.sqlite.bundled.jvm)
}
