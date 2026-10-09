cmake_minimum_required(VERSION 3.13)
project(appmodules)

find_package(ReactAndroid REQUIRED CONFIG)
find_package(fbjni REQUIRED CONFIG)

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
