// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  // RNGP（node_modules/@react-native/gradle-plugin）编译时用的是 AGP 9.2.1，而本工程固定
  // AGP 9.0.1 + Gradle 9.1.0。下面 plugins {} 里的 com.facebook.react 会把插件的运行时
  // 依赖带进根工程的脚本类路径；若夹带 AGP 9.2.1，:app 申请 AGP 9.0.1 时会加载到 9.2.1 的
  // version-check（要求 Gradle ≥ 9.4.1）而使整个构建失败。这里把插件类路径上的 AGP 压回
  // 本工程已固定的版本，保证整棵构建只有一个 AGP。
  configurations.named("classpath") {
    resolutionStrategy {
      force("com.android.tools.build:gradle:9.0.1")
    }
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.androidx.baselineprofile) apply false
  // 仅为把 RNGP 放进构建的插件类路径：node_modules 下的 RN 原生库
  // （reanimated / worklets / svg / safe-area-context）在自身构建脚本里用
  // 传统 apply(plugin = "com.facebook.react") 申请插件，而传统 apply 只从
  // 已解析的插件类路径查找。本工程刻意不给 :app 应用 com.facebook.react
  // （保持既有 bundle 构建流程，不走 RN 的 autolinking / codegen），
  // 因此在根工程解析一次即可让各库的传统 apply 命中。
  // ReactPlugin 在既无 com.android.application 也无 com.android.library 的
  // 工程上只创建 react / privateReact 扩展，不产生任何任务或依赖。
  id("com.facebook.react")
}

// RN 原生库（reanimated / worklets / svg）推导 react-native 安装目录时，最后的手段是
// `node --print require.resolve('react-native/package.json')`，其 stdout 会被 Gradle 以
// -Dfile.encoding=GBK（gradle.properties 为此固定，中文路径 + JDK17 argfile 解码必需）
// 解码，于是仓库路径里的「项目」二字必然变乱码、目录判定为不存在而抛异常。
// 这里显式给出绝对路径，绕开该推导。
// svg 用 safeExtGet() 读 rootProject.ext；reanimated / worklets 用 safeAppExtGet() 读
// :app 的 ext，所以两处都要设置。
val reactNativeDir: File = file("node_modules/react-native")
extra.set("REACT_NATIVE_NODE_MODULES_DIR", reactNativeDir)
project(":app").extra.set("REACT_NATIVE_NODE_MODULES_DIR", reactNativeDir)

// RN 原生库的 compileSdk 兜底值是 37（reanimated / worklets），而本工程固定 compileSdk 36。
// 统一压到 36：与 :app 保持一致，不必为编译第三方库额外装 SDK Platform 37，
// 也避开 AGP 9.0.1「仅测试到 compileSdk 36.1」的告警。各库通过 rootProject.ext 读该值。
extra.set("compileSdkVersion", 36)

/*
 * NDK 版本——全工程唯一来源。
 *
 * 这里必须是单一来源：node_modules 下的 RN 原生库（reanimated / worklets / svg /
 * safe-area-context）是独立的 Gradle library 工程，读不到 :app 的 android.ndkVersion，
 * 各自落到 AGP 默认值「已安装的最新 NDK」。本机同时装着 NDK 27.1 与 28.2，这些库便用
 * 28.2 编译，而 react-android 0.87.1 的 AAR 自带旧 NDK 产物的 libc++_shared.so
 * （实测导出 2336 个符号，与 NDK 27.1 的完全等价，比 28.2 少 4 个）。
 *
 * app/build.gradle.kts 的 packaging.jniLibs.pickFirsts 里有 libc++_shared.so 一项，合并时
 * 静默保留 AAR 那份旧的、丢掉 28.2 那份新的。运行期 dlopen libworklets.so 于是找不到
 * NDK 28.2 才导出的 __cxa_init_primary_exception，抛 UnsatisfiedLinkError，被 SoLoader
 * 的恢复流程包装成 SoLoaderDSONotFoundError，表象是「一点击就闪退」。
 *
 * 统一到 27.1 后，各原生库只引用 NDK 27.1 libc++ 的符号，而 AAR 那份全都提供。
 * :app 也从这里读同一个值，避免两处字面量漂移后再次静默错配。
 */
val yanjiNdkVersion = "27.1.12297006"
extra.set("yanjiNdkVersion", yanjiNdkVersion)

// :app 用 com.android.application，其余 RN 原生库用 com.android.library，两类都要压。
// 回调在各库的 plugins {} 求值时就触发，早于它们自己的 android {} 块，因此不会被覆盖
// （reanimated / worklets / svg / safe-area-context 本身都没有声明 ndkVersion）。
// 走 Groovy 动态派发而非强类型 extensions.configure<LibraryExtension>：AGP 9 会给扩展对象
// 套一层生成的装饰类，它不实现 com.android.build.gradle.LibraryExtension，强类型转换会直接
// ClassCastException。属性名与 :app 的 android.ndkVersion 是同一个。
subprojects {
  plugins.withId("com.android.library") {
    extensions.getByName("android").withGroovyBuilder {
      setProperty("ndkVersion", yanjiNdkVersion)
    }
  }
}

// RNGP 在给 :app 应用 com.facebook.react 时，会通过 configureDependencies() 给所有工程的
// 所有 configuration 装上一组依赖替换与版本强制（见 node_modules/@react-native/gradle-plugin/
// react-native-gradle-plugin/src/main/kotlin/com/facebook/react/utils/DependencyUtils.kt）。
// 本工程刻意不给 :app 应用该插件以保持既有 bundle 构建流程，所以在这里等价补齐：
//   - com.facebook.react:react-native / hermes-engine 坐标已废弃
//     （facebook/react-native#35210），node_modules 下的 RN 原生库仍按旧坐标声明依赖
//     （svg / safe-area-context 的 implementation 'com.facebook.react:react-native:+'
//     在 Maven Central 上最高只到 0.71.0-rc.0，直接解析必然失败）；
//   - worklets 声明的是无版本号的 com.facebook.react:react-android 与
//     com.facebook.react:hermes-android（源码注释即 "version substituted by RNGP"），
//     没有这层替换连版本都取不到。
val reactNativeVersion = libs.versions.reactAndroid.get()
val hermesVersion = libs.versions.hermesVersion.get()

allprojects {
  configurations.all {
    resolutionStrategy.dependencySubstitution {
      substitute(module("com.facebook.react:react-native"))
        .using(module("com.facebook.react:react-android:$reactNativeVersion"))
        .because("com.facebook.react:react-native 坐标已废弃，改用 react-android")
      substitute(module("com.facebook.react:hermes-engine"))
        .using(module("com.facebook.hermes:hermes-android:$hermesVersion"))
        .because("com.facebook.react:hermes-engine 坐标已废弃，改用 hermes-android")
      substitute(module("com.facebook.react:hermes-android"))
        .using(module("com.facebook.hermes:hermes-android:$hermesVersion"))
        .because("hermes-android 已迁移到 com.facebook.hermes 发布组")
    }
    resolutionStrategy.force("com.facebook.react:react-android:$reactNativeVersion")
    resolutionStrategy.force("com.facebook.hermes:hermes-android:$hermesVersion")
  }
}

// RNGP 在 rootProject 上注册的 PrivateReactExtension 是各 RN 原生库 codegen 任务的唯一配置源
// （ReactPlugin 只在 com.android.application 分支里把它从 app 的 react{} 扩展拷贝过来，
// 而本工程刻意不给 :app 应用 RNGP，所以只能在这里直接设值）。
// 它的 root 兜底值是 rootProject.dir/../ ——RN 模板里 android/ 是工程子目录所以恰好是工程根，
// 但本仓库 settings.gradle.kts 就在仓库根，兜底值会指到上一级 D:\，于是 codegen 的
// node 命令拿着错误路径找不到 @react-native/codegen。这里显式指到仓库根。
// 该类型在 RNGP 里声明为 Kotlin internal，构建脚本无法按类型引用，但其 JVM 字节码是 public 的，
// 因此走 Kotlin DSL 官方的 Groovy 互操作读取属性后赋值。
extensions.getByName("privateReact").withGroovyBuilder {
  (getProperty("root") as DirectoryProperty).set(rootDir)
}
