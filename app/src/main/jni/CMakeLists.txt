cmake_minimum_required(VERSION 3.13)
project(appmodules)

find_package(ReactAndroid REQUIRED CONFIG)
find_package(fbjni REQUIRED CONFIG)

# 仓库根：从 app/src/main/jni 自身位置相对推导，不写死盘符 / 用户名。
# app/src/main/jni -> main -> src -> app -> 仓库根，共四级。
get_filename_component(YANJI_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
get_filename_component(YANJI_RN_DIR "${YANJI_REPO_ROOT}/node_modules/react-native" ABSOLUTE)

if(NOT EXISTS "${YANJI_RN_DIR}/ReactAndroid/cmake-utils/folly-flags.cmake")
    message(FATAL_ERROR
        "react-native was not found at ${YANJI_RN_DIR}. Expected the repository to "
        "keep React Native under node_modules/react-native.")
endif()

# folly-flags.cmake / react-native-flags.cmake 必须先于 add_library 引入：
# 它们本身只提供 folly_FLAGS 变量与 target_compile_reactnative_options 函数，
# 编译选项要在 target 上显式生效。顺序反了（先 add_library 再 add_compile_options）
# 时 folly_FLAGS 不会进入编译命令行 —— 表现是能编过、能装上，运行期在
# folly::dynamic 析构上 abort（详见下面 folly_FLAGS 的注释）。
include("${YANJI_RN_DIR}/ReactAndroid/cmake-utils/folly-flags.cmake")
include("${YANJI_RN_DIR}/ReactCommon/cmake-utils/react-native-flags.cmake")

add_library(${CMAKE_PROJECT_NAME} SHARED
    FollyF14LinkCheck.cpp
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
    ${folly_FLAGS}
    # -DNDEBUG 与 -DREACT_NATIVE_DEBUG 必须成对出现，缺一不可：
    #
    # NDEBUG：folly::kIsDebug 就是 !NDEBUG。带上它，F14Table 的调试用插入扰动代码
    #   （FOLLY_F14_PERTURB_INSERTION_ORDER）不再实例化，也就不会引用
    #   folly::f14::detail::tlsMinstdRand / tlsPendingSafeInserts —— 这两个符号
    #   同样不在 libreactnative.so 的导出表里，链接期必然缺。
    #   它只影响 folly 的 if constexpr 分支，不改类布局。
    #
    # REACT_NATIVE_DEBUG：react/debug/flags.h 会按 NDEBUG 反推
    #   REACT_NATIVE_DEBUG / REACT_NATIVE_PRODUCTION，而这两个宏改变的是**类布局**：
    #     - Sealable 生产版是空类，调试版带 mutable std::atomic<bool> sealed_（8 字节），
    #       而 Props 是 virtual 继承它的；
    #     - RN_DEBUG_STRING_CONVERTIBLE 从 0 变 1，Props / BaseViewProps /
    #       HostPlatformViewProps 等一整套 Props 层级多出一批虚函数。
    #   libreactnative.so 是没带 NDEBUG 编的（REACT_NATIVE_DEBUG），本库只带 NDEBUG
    #   就会让 Props 比它小 8 字节，运行期表现为 libreactnative.so 里的
    #   HostPlatformViewProps 构造函数按自己的偏移写成员，写到堆外：SIGSEGV。
    #   所以显式钉死 REACT_NATIVE_DEBUG，把 NDEBUG 的影响限制在 folly 的
    #   if constexpr 与 assert 上。
    #   剩下的 F14LinkCheck<Mode>::check() 见 FollyF14LinkCheck.cpp。
    -DNDEBUG
    -DREACT_NATIVE_DEBUG
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    fbjni::fbjni
    ReactAndroid::jsi
    ReactAndroid::reactnative
)

# react-native-flags.cmake 里的 target_compile_reactnative_options 会额外定义
# RN_SERIALIZABLE_STATE 与 HERMES_V1_ENABLED。这两个宏直接改变 StateData /
# ConcreteComponentDescriptor 的 ABI；libreactnative.so 是带着它们编出来的，
# 本库少一个就会在 Fabric 构造组件描述符时解析 BaseViewProps 崩溃（SIGSEGV）。
# 所以直接调用 RN 自己的函数，不在这里复制宏列表。
target_compile_reactnative_options(${CMAKE_PROJECT_NAME} PRIVATE)

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
    react-native-blur-overlay:RNBlurOverlaySpec
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
