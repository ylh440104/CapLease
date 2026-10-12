cmake_minimum_required(VERSION 3.22.1)

project(caplease LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(caplease SHARED
    sha256.cpp
    hmac.cpp
    supercall.cpp
    bridge.cpp
)

target_compile_options(caplease PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -Wshadow
    -Wconversion
    -Wsign-conversion
    -fno-exceptions
    -fno-rtti
    -fvisibility=hidden
    -fvisibility-inlines-hidden
)

target_link_libraries(caplease PRIVATE log)