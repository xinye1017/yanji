cmake_minimum_required(VERSION 3.13)
project(appmodules)

find_package(ReactAndroid REQUIRED CONFIG)
find_package(fbjni REQUIRED CONFIG)

# 仓库根：从 app/src/main/jni 自身位置相对推导，不写死盘符 / 用户名。
# app/src/main/jni -> main -> src -> app -> 仓库根，共四级。
get_filename_component(YANJI_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)

# codegen 源码会间接 include folly/portability/Config.h，而 folly-config.h 只在
# react-native 自身构建时生成，prefab 包里并不提供。RN 各原生库的解法就是统一带
# -DFOLLY_NO_CONFIG=1 绕过（见 folly-flags.cmake），这里沿用同一份定义，不复制数值。
get_filename_component(YANJI_RN_DIR "${YANJI_REPO_ROOT}/node_modules/react-native" ABSOLUTE)
if(NOT EXISTS "${YANJI_RN_DIR}/ReactAndroid/cmake-utils/folly-flags.cmake")
    message(FATAL_ERROR
        "react-native was not found at ${YANJI_RN_DIR}. Expected the repository to "
        "keep React Native under node_modules/react-native.")
endif()
include("${YANJI_RN_DIR}/ReactAndroid/cmake-utils/folly-flags.cmake")
add_compile_options(${folly_FLAGS})

add_library(${CMAKE_PROJECT_NAME} SHARED
    OnLoad.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME}
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
    -std=c++20
    -fexceptions
    -frtti
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    fbjni::fbjni
    ReactAndroid::jsi
    ReactAndroid::reactnative
)

# ---------------------------------------------------------------------------
# RN 原生库的 C++ codegen：TurboModule provider + Fabric 组件描述符。
#
# 官方模板里这一层由 RNGP 生成 Android-autolinking.cmake + autolinking.cpp，
# 用 add_subdirectory 把各库的 react_codegen_<lib> OBJECT 库链进 libappmodules.so。
# 本工程刻意不给 :app 应用 RNGP（见 build.gradle.kts 顶部注释），所以在这里等价补齐：
# 直接把各库生成在 node_modules/<lib>/android/build/generated/source/codegen/jni
# 下的源码编进本库。
#
# 这不是可选的优化。C++ TurboModuleManager::getTurboModule 对每个 Java TurboModule
# 都会调 DefaultTurboModuleManagerDelegate::javaModuleProvider，也就是本工程的
# OnLoad.cpp。该 provider 只认 FBReactNativeSpec_*（RN 内核模块）；少了下面这几段，
# WorkletsModule / ReanimatedModule / RNSVGRenderableModule / RNCSafeAreaContext
# 全部解析成 nullptr，JS 侧 NativeModules.WorkletsModule === null，启动即崩在
# worklets 的 loadUnpackersWithCode of undefined。
#
# 生成目录是各库 generateCodegenArtifactsFromSchema 的产物；app/build.gradle.kts
# 已让 :app 的 CMake 配置任务依赖这些任务，因此这里目录必然存在。缺失一律硬失败，
# 绝不静默跳过（静默跳过的表现就是上面那种「能装上、一进去就闪退」的运行期崩溃）。
# ---------------------------------------------------------------------------
set(YANJI_RN_AUTOLINKED_LIBRARIES
    react-native-worklets:rnworklets
    react-native-reanimated:rnreanimated
    react-native-svg:rnsvg
    react-native-safe-area-context:safeareacontext
)

set(YANJI_RN_CODEGEN_SRCS)

foreach(yarni_entry ${YANJI_RN_AUTOLINKED_LIBRARIES})
    string(REPLACE ":" ";" yarni_parts "${yarni_entry}")
    list(GET yarni_parts 0 YANJI_RN_LIB_DIR)
    list(GET yarni_parts 1 YANJI_RN_LIB_NAME)

    set(YANJI_RN_CODEGEN_DIR
        "${YANJI_REPO_ROOT}/node_modules/${YANJI_RN_LIB_DIR}/android/build/generated/source/codegen/jni"
    )

    if(NOT EXISTS "${YANJI_RN_CODEGEN_DIR}/${YANJI_RN_LIB_NAME}-generated.cpp")
        message(FATAL_ERROR
            "React Native codegen for '${YANJI_RN_LIB_NAME}' was not found in "
            "${YANJI_RN_CODEGEN_DIR}. :app's CMake configuration must depend on "
            ":${YANJI_RN_LIB_DIR}:generateCodegenArtifactsFromSchema.")
    endif()

    file(GLOB YANJI_RN_LIB_SRCS CONFIGURE_DEPENDS
        "${YANJI_RN_CODEGEN_DIR}/*.cpp"
        "${YANJI_RN_CODEGEN_DIR}/react/renderer/components/${YANJI_RN_LIB_NAME}/*.cpp"
    )

    list(APPEND YANJI_RN_CODEGEN_SRCS ${YANJI_RN_LIB_SRCS})
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE "${YANJI_RN_CODEGEN_DIR}")
endforeach()

target_sources(${CMAKE_PROJECT_NAME} PRIVATE ${YANJI_RN_CODEGEN_SRCS})
