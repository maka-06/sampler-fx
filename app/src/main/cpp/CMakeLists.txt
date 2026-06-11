cmake_minimum_required(VERSION 3.22.1)
project(sampleapp)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe est fourni via Prefab (dépendance Gradle com.google.oboe:oboe)
find_package(oboe REQUIRED CONFIG)

add_library(sampleapp SHARED
        native-lib.cpp
        AudioEngine.cpp
        SampleBuffer.cpp
        effects/EffectChain.cpp
        effects/Biquad.cpp
        effects/Distortion.cpp
        effects/Delay.cpp
        effects/Reverb.cpp
        effects/PitchShifter.cpp
)

target_include_directories(sampleapp PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)

target_link_libraries(sampleapp
        oboe::oboe
        ${log-lib}
)
