pluginManagement {
    // React Native Gradle Plugin：reanimated / svg / safe-area-context 等原生库
    // 在自身 build 脚本里 apply("com.facebook.react")，必须通过 includeBuild 提供。
    // 注意：本仓库 settings.gradle.kts 位于仓库根（RN 模板位于 android/ 子目录），
    // 因此这里是 node_modules/... 而非 ../node_modules/...。
    includeBuild("node_modules/@react-native/gradle-plugin")
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // PREFER_SETTINGS（RN 官方模板同款）：settings 里的仓库优先，但允许工程自带仓库。
    // 不能是 FAIL_ON_PROJECT_REPOS：node_modules 下的 RN 原生库（reanimated / svg /
    // safe-area-context …）在自身构建脚本里声明 repositories，严格模式会直接让配置失败。
    // 依赖来源仍以 settings 的 google + mavenCentral 为准，项目仓库只作兜底。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.facebook.react.settings")
}

// 原生库链接：按官方 linkLibraries() 的等价语义手工 include 各库的 android/ 工程。
// 不走 autolinkLibrariesFromCommand()：那条路要额外起一个 node 子进程跑 `react-native config`
// 再解析 JSON，路径经 CLI 输出回传时还要过一次 Gradle 的 -Dfile.encoding=GBK 解码
// （见 gradle.properties 注释），多一层字符集风险。手工 include 路径确定、无子进程，
// 新增原生库时在此追加一组 include + projectDir 即可。
include(":react-native-reanimated")
project(":react-native-reanimated").projectDir = file("node_modules/react-native-reanimated/android")
include(":react-native-worklets")
project(":react-native-worklets").projectDir = file("node_modules/react-native-worklets/android")
include(":react-native-svg")
project(":react-native-svg").projectDir = file("node_modules/react-native-svg/android")
include(":react-native-safe-area-context")
project(":react-native-safe-area-context").projectDir = file("node_modules/react-native-safe-area-context/android")

rootProject.name = "Yanji"
include(":react-native-blur-overlay")
project(":react-native-blur-overlay").projectDir = file("node_modules/react-native-blur-overlay/android")
include(":app")
include(":macrobenchmark")
include(":baselineprofile")
