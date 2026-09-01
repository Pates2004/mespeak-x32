cmake_minimum_required(VERSION 3.22.1)

project(mespeak-android LANGUAGES C CXX)

# libsonic is built into the JNI library so the optional rate boost works on
# every supported Android ABI without relying on a device-specific audio API.
include(FetchContent)
FetchContent_Declare(sonic-git
    GIT_REPOSITORY https://github.com/waywardgeek/sonic.git
    GIT_TAG fbf75c3d6d846bad3bb3d456cbc5d07d9fd8c104
)
FetchContent_MakeAvailable(sonic-git)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ENGINE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../engine/src)

# The original eSpeak 1.44 library sources are kept intact. Android uses the
# synchronous retrieval callback, so the legacy POSIX worker/audio backends
# are intentionally not compiled; android_stubs.cpp supplies their harmless
# no-op API surface.
set(ENGINE_SOURCES
    ${ENGINE_DIR}/speak_lib.cpp
    ${ENGINE_DIR}/compiledict.cpp
    ${ENGINE_DIR}/dictionary.cpp
    ${ENGINE_DIR}/intonation.cpp
    ${ENGINE_DIR}/readclause.cpp
    ${ENGINE_DIR}/setlengths.cpp
    ${ENGINE_DIR}/numbers.cpp
    ${ENGINE_DIR}/synth_mbrola.cpp
    ${ENGINE_DIR}/synthdata.cpp
    ${ENGINE_DIR}/synthesize.cpp
    ${ENGINE_DIR}/translate.cpp
    ${ENGINE_DIR}/mbrowrap.cpp
    ${ENGINE_DIR}/tr_languages.cpp
    ${ENGINE_DIR}/voices.cpp
    ${ENGINE_DIR}/wavegen.cpp
    ${ENGINE_DIR}/phonemelist.cpp
    ${ENGINE_DIR}/espeak_command.cpp
    ${ENGINE_DIR}/debug.cpp
    ${ENGINE_DIR}/klatt.cpp
    ${ENGINE_DIR}/android_stubs.cpp
)

add_library(ttsespeak SHARED
    jni/eSpeakService.c
    ${sonic-git_SOURCE_DIR}/sonic.c
    ${ENGINE_SOURCES}
)

target_include_directories(ttsespeak PRIVATE
    ${ENGINE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${sonic-git_SOURCE_DIR}
)

target_compile_definitions(ttsespeak PRIVATE
    # Do not define LIBRARY on POSIX: speech.h would enable the old worker.
    PATH_ESPEAK_DATA="/data/data/com.pates2004.mespeak.x32/app_data/voices"
    USE_MBROLA=0
)

target_compile_options(ttsespeak PRIVATE
    -fPIC
    -fvisibility=hidden
    -fno-exceptions
)

find_library(ANDROID_LOG log)
target_link_libraries(ttsespeak PRIVATE ${ANDROID_LOG})
