cmake_minimum_required(VERSION 3.22.1)
project(tw_whisper LANGUAGES C CXX)

# whisper.cpp v1.7.4 (ggml vendored in-tree) + a thin JNI bridge, built as one
# shared library. Upstream's Android sample hand-lists the CPU sources for the
# same reason: the full ggml CMakeLists pulls in option/detection machinery and
# per-backend libraries we deliberately do not compile here. Only the CPU
# backend is enabled so nothing links against OpenMP, BLAS, Vulkan or a GPU SDK
# that is absent from the NDK.
set(WHISPER_VENDOR_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)
set(GGML_VENDOR_DIR    ${CMAKE_CURRENT_SOURCE_DIR}/ggml)

set(CMAKE_C_STANDARD   11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/jni/whisper_jni.cpp
    ${WHISPER_VENDOR_DIR}/src/whisper.cpp
    ${GGML_VENDOR_DIR}/src/ggml.c
    ${GGML_VENDOR_DIR}/src/ggml-alloc.c
    ${GGML_VENDOR_DIR}/src/ggml-backend.cpp
    ${GGML_VENDOR_DIR}/src/ggml-backend-reg.cpp
    ${GGML_VENDOR_DIR}/src/ggml-quants.c
    ${GGML_VENDOR_DIR}/src/ggml-threading.cpp
    ${GGML_VENDOR_DIR}/src/ggml-cpu/ggml-cpu.c
    ${GGML_VENDOR_DIR}/src/ggml-cpu/ggml-cpu.cpp
    ${GGML_VENDOR_DIR}/src/ggml-cpu/ggml-cpu-aarch64.cpp
    ${GGML_VENDOR_DIR}/src/ggml-cpu/ggml-cpu-hbm.cpp
    ${GGML_VENDOR_DIR}/src/ggml-cpu/ggml-cpu-quants.c
    ${GGML_VENDOR_DIR}/src/ggml-cpu/ggml-cpu-traits.cpp
)

add_library(whisper_jni SHARED ${SOURCES})

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_VENDOR_DIR}/include
    ${GGML_VENDOR_DIR}/include
    ${GGML_VENDOR_DIR}/src
    ${GGML_VENDOR_DIR}/src/ggml-cpu
)

# CPU is the only registered backend; the other GGML_USE_* switches stay off so
# ggml-backend-reg.cpp compiles out the CUDA/Metal/Vulkan/... hookups.
target_compile_definitions(whisper_jni PRIVATE
    GGML_USE_CPU
    $<$<CONFIG:Release>:NDEBUG>
)

target_compile_options(whisper_jni PRIVATE
    -O3
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -Wno-unused-function
)

find_library(LOG_LIB log)
target_link_libraries(whisper_jni PRIVATE
    ${LOG_LIB}
    android
)

# The bridge exports only the JNI entry points; everything else is hidden so the
# ggml/whisper symbols do not leak into the process global namespace.
target_link_options(whisper_jni PRIVATE
    -Wl,--gc-sections
    -Wl,--build-id=sha1
)
