cmake_minimum_required(VERSION 3.22.1)
project(fbng_aspect C)

if(NOT LIBRETRODROID_DIR)
    message(FATAL_ERROR "LIBRETRODROID_DIR is required")
endif()

add_library(fbng_aspect SHARED aspect_probe.c)

add_library(libretrodroid SHARED IMPORTED)
set_target_properties(libretrodroid PROPERTIES
    IMPORTED_LOCATION "${LIBRETRODROID_DIR}/liblibretrodroid.so"
)

target_link_libraries(fbng_aspect
    dl
    log
    "-Wl,--no-as-needed"
    libretrodroid
    "-Wl,--as-needed"
)

target_link_options(fbng_aspect PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
