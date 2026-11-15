cmake_minimum_required(VERSION 3.22.1)
project(zrinpty C)

add_library(zrinpty SHARED pty.c)

find_library(log-lib log)
target_link_libraries(zrinpty ${log-lib})
