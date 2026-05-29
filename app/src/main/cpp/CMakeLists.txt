cmake_minimum_required(VERSION 3.22.1)
project(gesture_engine)
add_library(gesture_engine SHARED gesture_engine.cpp)
target_link_libraries(gesture_engine android log)
