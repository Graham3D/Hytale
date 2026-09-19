cmake_minimum_required(VERSION 3.24)
project(OrbisLLM LANGUAGES CXX)

# The Phase 1 build intentionally resolves the pinned libllama C ABI at runtime. This keeps the
# worker independent of MSVC import-library format while still calling libllama directly.
add_executable(OrbisLLM src/main.cpp)
target_compile_features(OrbisLLM PRIVATE cxx_std_20)
target_compile_definitions(OrbisLLM PRIVATE UNICODE _UNICODE NOMINMAX)
target_include_directories(OrbisLLM PRIVATE
    "${LLAMA_CPP_SOURCE}/include"
    "${LLAMA_CPP_SOURCE}/ggml/include"
    "${NLOHMANN_JSON_SOURCE}/single_include")
target_link_libraries(OrbisLLM PRIVATE bcrypt psapi advapi32)
