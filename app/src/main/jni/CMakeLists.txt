cmake_minimum_required(VERSION 3.22)
project("llama")

add_library(llama SHARED llama_jni.cpp)
find_library(log-lib log)
target_link_libraries(llama ${log-lib})
