#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <sddl.h>
#include <bcrypt.h>
#include <psapi.h>

#include <llama.h>
#include <nlohmann/json.hpp>

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <optional>
#include <random>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

using json = nlohmann::json;
using namespace std::chrono_literals;

namespace {

constexpr uint32_t FRAME_MAGIC = 0x4c42524f; // ORBL, little endian
constexpr uint16_t PROTOCOL_MAJOR = 1;
constexpr uint16_t PROTOCOL_MINOR = 0;
constexpr uint32_t MAX_REQUEST_PAYLOAD = 2u * 1024u * 1024u;
constexpr uint32_t MAX_EVENT_PAYLOAD = 64u * 1024u;
constexpr const char * RUNTIME_BUILD = "orbisllm-phase1-b10701";
constexpr const char * LLAMA_COMMIT = "cc231cb0da565440cf6a3e5b55dfeba477972cb6";
constexpr const char * TEMPLATE_REVISION = "nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16@dfaf35de3e30f1867dd8dbc38a7fc9fb52d3914f";
constexpr const char * TEMPLATE_SHA256 = "ab7813c3abdd9cb655905a410728b26c7884eca45ddfab8d9f931553485a7862";

enum class MessageType : uint16_t {
    HELLO = 1,
    HELLO_ACK = 2,
    LOAD_MODEL = 3,
    MODEL_PROGRESS = 4,
    CREATE_CONTEXT = 5,
    READY = 6,
    GENERATE = 7,
    REQUEST_ACCEPTED = 8,
    PROMPT_PROGRESS = 9,
    REASONING_DELTA = 10,
    FINAL_DELTA = 11,
    CONTRACT_COMPLETE = 12,
    REQUEST_COMPLETE = 13,
    CANCEL = 14,
    CANCEL_REQUESTED = 15,
    CANCEL_ACK = 16,
    RELEASE_CONTEXT = 17,
    UNLOAD_MODEL = 18,
    GET_STATUS = 19,
    STATUS = 20,
    RESOURCE_SNAPSHOT = 21,
    ERROR_MESSAGE = 22,
    SHUTDOWN = 23,
    SHUTDOWN_ACK = 24,
};

#pragma pack(push, 1)
struct FrameHeader {
    uint32_t magic;
    uint16_t protocol_major;
    uint16_t protocol_minor;
    uint16_t message_type;
    uint16_t flags;
    uint32_t payload_bytes;
    uint64_t sequence;
    uint8_t request_id[16];
};
#pragma pack(pop)
static_assert(sizeof(FrameHeader) == 40);

std::wstring widen(const std::string & value) {
    if (value.empty()) return {};
    int needed = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
            static_cast<int>(value.size()), nullptr, 0);
    if (needed <= 0) throw std::runtime_error("invalid UTF-8 path/argument");
    std::wstring result(static_cast<size_t>(needed), L'\0');
    MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
            static_cast<int>(value.size()), result.data(), needed);
    return result;
}

std::string narrow(const std::wstring & value) {
    if (value.empty()) return {};
    int needed = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(),
            static_cast<int>(value.size()), nullptr, 0, nullptr, nullptr);
    if (needed <= 0) throw std::runtime_error("invalid UTF-16 path/argument");
    std::string result(static_cast<size_t>(needed), '\0');
    WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(),
            static_cast<int>(value.size()), result.data(), needed, nullptr, nullptr);
    return result;
}

std::string lower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

std::string random_hex(size_t bytes) {
    std::random_device source;
    std::ostringstream text;
    text << std::hex << std::setfill('0');
    for (size_t i = 0; i < bytes; ++i) text << std::setw(2) << (source() & 0xffu);
    return text.str();
}

std::string sha256_hex(const void * data, size_t length) {
    BCRYPT_ALG_HANDLE algorithm = nullptr;
    BCRYPT_HASH_HANDLE hash = nullptr;
    DWORD object_size = 0;
    DWORD received = 0;
    if (BCryptOpenAlgorithmProvider(&algorithm, BCRYPT_SHA256_ALGORITHM, nullptr, 0) < 0) {
        throw std::runtime_error("BCrypt SHA-256 provider unavailable");
    }
    auto close_algorithm = [&] { if (algorithm) BCryptCloseAlgorithmProvider(algorithm, 0); };
    if (BCryptGetProperty(algorithm, BCRYPT_OBJECT_LENGTH,
            reinterpret_cast<PUCHAR>(&object_size), sizeof(object_size), &received, 0) < 0) {
        close_algorithm();
        throw std::runtime_error("BCrypt SHA-256 object length failed");
    }
    std::vector<uint8_t> object(object_size);
    std::array<uint8_t, 32> digest{};
    if (BCryptCreateHash(algorithm, &hash, object.data(), object_size, nullptr, 0, 0) < 0
            || BCryptHashData(hash, reinterpret_cast<PUCHAR>(const_cast<void *>(data)),
                    static_cast<ULONG>(length), 0) < 0
            || BCryptFinishHash(hash, digest.data(), static_cast<ULONG>(digest.size()), 0) < 0) {
        if (hash) BCryptDestroyHash(hash);
        close_algorithm();
        throw std::runtime_error("BCrypt SHA-256 failed");
    }
    BCryptDestroyHash(hash);
    close_algorithm();
    std::ostringstream text;
    text << std::hex << std::setfill('0');
    for (uint8_t byte : digest) text << std::setw(2) << static_cast<unsigned>(byte);
    return text.str();
}

std::string trim(std::string value) {
    auto space = [](unsigned char c) { return std::isspace(c) != 0; };
    value.erase(value.begin(), std::find_if(value.begin(), value.end(),
            [&](char c) { return !space(static_cast<unsigned char>(c)); }));
    value.erase(std::find_if(value.rbegin(), value.rend(),
            [&](char c) { return !space(static_cast<unsigned char>(c)); }).base(), value.end());
    return value;
}

std::array<uint8_t, 16> request_bytes(const FrameHeader & header) {
    std::array<uint8_t, 16> result{};
    std::memcpy(result.data(), header.request_id, result.size());
    return result;
}

bool all_zero(const std::array<uint8_t, 16> & value) {
    return std::all_of(value.begin(), value.end(), [](uint8_t byte) { return byte == 0; });
}

bool read_exact(HANDLE pipe, void * destination, size_t bytes) {
    auto * cursor = static_cast<uint8_t *>(destination);
    while (bytes > 0) {
        DWORD count = 0;
        if (!ReadFile(pipe, cursor, static_cast<DWORD>(std::min<size_t>(bytes, 1u << 20)),
                &count, nullptr) || count == 0) return false;
        cursor += count;
        bytes -= count;
    }
    return true;
}

// A synchronous duplex pipe serializes a blocking ReadFile against writes issued by the
// generation thread. Polling for available bytes keeps the control reader non-blocking while a
// request streams events; no Hytale thread is involved in this native loop.
bool read_exact_available(HANDLE pipe, void * destination, size_t bytes,
        const std::atomic<bool> & stopping) {
    auto * cursor = static_cast<uint8_t *>(destination);
    while (bytes > 0 && !stopping) {
        DWORD available = 0;
        if (!PeekNamedPipe(pipe, nullptr, 0, nullptr, &available, nullptr)) return false;
        if (available == 0) {
            std::this_thread::sleep_for(2ms);
            continue;
        }
        DWORD target = static_cast<DWORD>(std::min<size_t>(bytes, available));
        DWORD count = 0;
        if (!ReadFile(pipe, cursor, target, &count, nullptr) || count == 0) return false;
        cursor += count;
        bytes -= count;
    }
    return bytes == 0;
}

void write_exact(HANDLE pipe, const void * source, size_t bytes) {
    const auto * cursor = static_cast<const uint8_t *>(source);
    while (bytes > 0) {
        DWORD count = 0;
        if (!WriteFile(pipe, cursor, static_cast<DWORD>(std::min<size_t>(bytes, 1u << 20)),
                &count, nullptr) || count == 0) {
            throw std::runtime_error("named-pipe write failed");
        }
        cursor += count;
        bytes -= count;
    }
}

struct SecurityDescriptor {
    PSECURITY_DESCRIPTOR descriptor = nullptr;
    SECURITY_ATTRIBUTES attributes{};

    SecurityDescriptor() {
        HANDLE token = nullptr;
        if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) {
            throw std::runtime_error("OpenProcessToken failed");
        }
        DWORD bytes = 0;
        GetTokenInformation(token, TokenUser, nullptr, 0, &bytes);
        std::vector<uint8_t> buffer(bytes);
        if (!GetTokenInformation(token, TokenUser, buffer.data(), bytes, &bytes)) {
            CloseHandle(token);
            throw std::runtime_error("GetTokenInformation failed");
        }
        CloseHandle(token);
        auto * user = reinterpret_cast<TOKEN_USER *>(buffer.data());
        LPWSTR sid = nullptr;
        if (!ConvertSidToStringSidW(user->User.Sid, &sid)) {
            throw std::runtime_error("ConvertSidToStringSid failed");
        }
        std::wstring sddl = L"D:P(A;;GA;;;" + std::wstring(sid) + L")";
        LocalFree(sid);
        if (!ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl.c_str(),
                SDDL_REVISION_1, &descriptor, nullptr)) {
            throw std::runtime_error("private pipe security descriptor failed");
        }
        attributes.nLength = sizeof(attributes);
        attributes.lpSecurityDescriptor = descriptor;
        attributes.bInheritHandle = FALSE;
    }

    ~SecurityDescriptor() { if (descriptor) LocalFree(descriptor); }
};

template <typename T>
T symbol(HMODULE module, const char * name) {
    auto address = GetProcAddress(module, name);
    if (!address) throw std::runtime_error(std::string("runtime symbol missing: ") + name);
    return reinterpret_cast<T>(address);
}

struct LlamaApi {
    HMODULE ggml = nullptr;
    HMODULE llama = nullptr;
    HMODULE cudart = nullptr;

    decltype(&llama_version) version = nullptr;
    decltype(&llama_backend_init) backend_init = nullptr;
    decltype(&llama_backend_free) backend_free = nullptr;
    decltype(&llama_model_default_params) model_default_params = nullptr;
    decltype(&llama_context_default_params) context_default_params = nullptr;
    decltype(&llama_sampler_chain_default_params) sampler_chain_default_params = nullptr;
    decltype(&llama_model_load_from_file) model_load_from_file = nullptr;
    decltype(&llama_model_free) model_free = nullptr;
    decltype(&llama_init_from_model) init_from_model = nullptr;
    decltype(&llama_free) context_free = nullptr;
    decltype(&llama_model_get_vocab) model_get_vocab = nullptr;
    decltype(&llama_model_desc) model_desc = nullptr;
    decltype(&llama_model_size) model_size = nullptr;
    decltype(&llama_model_n_params) model_n_params = nullptr;
    decltype(&llama_model_chat_template) model_chat_template = nullptr;
    decltype(&llama_supports_gpu_offload) supports_gpu_offload = nullptr;
    decltype(&llama_get_memory) get_memory = nullptr;
    decltype(&llama_memory_clear) memory_clear = nullptr;
    decltype(&llama_tokenize) tokenize = nullptr;
    decltype(&llama_batch_get_one) batch_get_one = nullptr;
    decltype(&llama_decode) decode = nullptr;
    decltype(&llama_vocab_is_eog) vocab_is_eog = nullptr;
    decltype(&llama_token_to_piece) token_to_piece = nullptr;
    decltype(&llama_sampler_chain_init) sampler_chain_init = nullptr;
    decltype(&llama_sampler_chain_add) sampler_chain_add = nullptr;
    decltype(&llama_sampler_init_penalties) sampler_init_penalties = nullptr;
    decltype(&llama_sampler_init_top_k) sampler_init_top_k = nullptr;
    decltype(&llama_sampler_init_top_p) sampler_init_top_p = nullptr;
    decltype(&llama_sampler_init_temp) sampler_init_temp = nullptr;
    decltype(&llama_sampler_init_dist) sampler_init_dist = nullptr;
    decltype(&llama_sampler_init_greedy) sampler_init_greedy = nullptr;
    decltype(&llama_sampler_init_grammar) sampler_init_grammar = nullptr;
    decltype(&llama_sampler_sample) sampler_sample = nullptr;
    decltype(&llama_sampler_free) sampler_free = nullptr;
    decltype(&llama_vocab_n_tokens) vocab_n_tokens = nullptr;
    decltype(&llama_n_ctx) n_ctx = nullptr;
    decltype(&llama_n_batch) n_batch = nullptr;
    decltype(&llama_n_ubatch) n_ubatch = nullptr;

    using BackendLoadAllFromPath = void (*)(const char *);
    BackendLoadAllFromPath backend_load_all_from_path = nullptr;
    using CudaMemGetInfo = int (*)(size_t *, size_t *);
    CudaMemGetInfo cuda_mem_get_info = nullptr;

    void load(const std::filesystem::path & runtime_dir) {
        SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_DEFAULT_DIRS | LOAD_LIBRARY_SEARCH_USER_DIRS);
        DLL_DIRECTORY_COOKIE cookie = AddDllDirectory(runtime_dir.wstring().c_str());
        // Some authenticated/reparse-backed Windows volumes reject AddDllDirectory even
        // though direct loading is supported. This process has already hash-allow-listed
        // every runtime DLL, so a process-local SetDllDirectory fallback remains bounded.
        if (!cookie && !SetDllDirectoryW(runtime_dir.wstring().c_str())) {
            throw std::runtime_error("failed to configure verified runtime DLL directory");
        }
        DWORD flags = cookie
                ? LOAD_LIBRARY_SEARCH_DEFAULT_DIRS | LOAD_LIBRARY_SEARCH_USER_DIRS
                : LOAD_WITH_ALTERED_SEARCH_PATH;
        ggml = LoadLibraryExW((runtime_dir / L"ggml.dll").c_str(), nullptr,
                flags);
        llama = LoadLibraryExW((runtime_dir / L"llama.dll").c_str(), nullptr,
                flags);
        if (!ggml || !llama) throw std::runtime_error("verified llama.cpp DLL set failed to load");
        backend_load_all_from_path = symbol<BackendLoadAllFromPath>(ggml,
                "ggml_backend_load_all_from_path");
        version = symbol<decltype(version)>(llama, "llama_version");
        backend_init = symbol<decltype(backend_init)>(llama, "llama_backend_init");
        backend_free = symbol<decltype(backend_free)>(llama, "llama_backend_free");
        model_default_params = symbol<decltype(model_default_params)>(llama, "llama_model_default_params");
        context_default_params = symbol<decltype(context_default_params)>(llama, "llama_context_default_params");
        sampler_chain_default_params = symbol<decltype(sampler_chain_default_params)>(llama, "llama_sampler_chain_default_params");
        model_load_from_file = symbol<decltype(model_load_from_file)>(llama, "llama_model_load_from_file");
        model_free = symbol<decltype(model_free)>(llama, "llama_model_free");
        init_from_model = symbol<decltype(init_from_model)>(llama, "llama_init_from_model");
        context_free = symbol<decltype(context_free)>(llama, "llama_free");
        model_get_vocab = symbol<decltype(model_get_vocab)>(llama, "llama_model_get_vocab");
        model_desc = symbol<decltype(model_desc)>(llama, "llama_model_desc");
        model_size = symbol<decltype(model_size)>(llama, "llama_model_size");
        model_n_params = symbol<decltype(model_n_params)>(llama, "llama_model_n_params");
        model_chat_template = symbol<decltype(model_chat_template)>(llama, "llama_model_chat_template");
        supports_gpu_offload = symbol<decltype(supports_gpu_offload)>(llama, "llama_supports_gpu_offload");
        get_memory = symbol<decltype(get_memory)>(llama, "llama_get_memory");
        memory_clear = symbol<decltype(memory_clear)>(llama, "llama_memory_clear");
        tokenize = symbol<decltype(tokenize)>(llama, "llama_tokenize");
        batch_get_one = symbol<decltype(batch_get_one)>(llama, "llama_batch_get_one");
        decode = symbol<decltype(decode)>(llama, "llama_decode");
        vocab_is_eog = symbol<decltype(vocab_is_eog)>(llama, "llama_vocab_is_eog");
        token_to_piece = symbol<decltype(token_to_piece)>(llama, "llama_token_to_piece");
        sampler_chain_init = symbol<decltype(sampler_chain_init)>(llama, "llama_sampler_chain_init");
        sampler_chain_add = symbol<decltype(sampler_chain_add)>(llama, "llama_sampler_chain_add");
        sampler_init_penalties = symbol<decltype(sampler_init_penalties)>(llama, "llama_sampler_init_penalties");
        sampler_init_top_k = symbol<decltype(sampler_init_top_k)>(llama, "llama_sampler_init_top_k");
        sampler_init_top_p = symbol<decltype(sampler_init_top_p)>(llama, "llama_sampler_init_top_p");
        sampler_init_temp = symbol<decltype(sampler_init_temp)>(llama, "llama_sampler_init_temp");
        sampler_init_dist = symbol<decltype(sampler_init_dist)>(llama, "llama_sampler_init_dist");
        sampler_init_greedy = symbol<decltype(sampler_init_greedy)>(llama, "llama_sampler_init_greedy");
        sampler_init_grammar = symbol<decltype(sampler_init_grammar)>(llama, "llama_sampler_init_grammar");
        sampler_sample = symbol<decltype(sampler_sample)>(llama, "llama_sampler_sample");
        sampler_free = symbol<decltype(sampler_free)>(llama, "llama_sampler_free");
        vocab_n_tokens = symbol<decltype(vocab_n_tokens)>(llama, "llama_vocab_n_tokens");
        n_ctx = symbol<decltype(n_ctx)>(llama, "llama_n_ctx");
        n_batch = symbol<decltype(n_batch)>(llama, "llama_n_batch");
        n_ubatch = symbol<decltype(n_ubatch)>(llama, "llama_n_ubatch");

        backend_load_all_from_path(narrow(runtime_dir.wstring()).c_str());
        backend_init();
        cudart = LoadLibraryExW((runtime_dir / L"cudart64_12.dll").c_str(), nullptr,
                LOAD_LIBRARY_SEARCH_DEFAULT_DIRS | LOAD_LIBRARY_SEARCH_USER_DIRS);
        if (cudart) cuda_mem_get_info = reinterpret_cast<CudaMemGetInfo>(
                GetProcAddress(cudart, "cudaMemGetInfo"));
    }

    ~LlamaApi() {
        if (backend_free) backend_free();
        if (cudart) FreeLibrary(cudart);
        if (llama) FreeLibrary(llama);
        if (ggml) FreeLibrary(ggml);
    }
};

struct ResourceSnapshot {
    int64_t free_vram_mib = -1;
    int64_t used_vram_mib = -1;
    int64_t total_vram_mib = -1;
    uint64_t working_set_mib = 0;
    uint64_t private_mib = 0;
    std::string measurement = "UNKNOWN";
};

ResourceSnapshot resources(const LlamaApi & api, bool query_cuda = true) {
    ResourceSnapshot result;
    PROCESS_MEMORY_COUNTERS_EX memory{};
    memory.cb = sizeof(memory);
    if (GetProcessMemoryInfo(GetCurrentProcess(),
            reinterpret_cast<PROCESS_MEMORY_COUNTERS *>(&memory), sizeof(memory))) {
        result.working_set_mib = memory.WorkingSetSize / (1024u * 1024u);
        result.private_mib = memory.PrivateUsage / (1024u * 1024u);
    }
    if (query_cuda && api.cuda_mem_get_info) {
        size_t free_bytes = 0;
        size_t total_bytes = 0;
        if (api.cuda_mem_get_info(&free_bytes, &total_bytes) == 0 && total_bytes > 0) {
            result.free_vram_mib = static_cast<int64_t>(free_bytes / (1024u * 1024u));
            result.total_vram_mib = static_cast<int64_t>(total_bytes / (1024u * 1024u));
            result.used_vram_mib = result.total_vram_mib - result.free_vram_mib;
            result.measurement = "CUDA_GLOBAL_AUTHORITATIVE_PROCESS_VRAM_UNKNOWN_WDDM";
        }
    }
    return result;
}

std::string render_nemotron(const json & messages, bool reasoning) {
    if (!messages.is_array() || messages.empty()) {
        throw std::runtime_error("canonical messages are required");
    }
    std::string prompt;
    size_t index = 0;
    if (messages[0].value("role", "") == "system") {
        prompt += "<|im_start|>system\n" + messages[0].value("content", "")
                + "<|im_end|>\n";
        index = 1;
    }
    for (; index < messages.size(); ++index) {
        const json & message = messages[index];
        std::string role = message.value("role", "");
        std::string content = message.value("content", "");
        if (role == "assistant") {
            content = trim(content);
            if (content.find("<think>") == std::string::npos
                    && content.find("</think>") == std::string::npos) {
                content = "<think></think>" + content;
            }
            prompt += "<|im_start|>assistant\n" + content + "<|im_end|>\n";
        } else if (role == "tool") {
            prompt += "<|im_start|>user\n<tool_response>\n" + content
                    + "\n</tool_response>\n<|im_end|>\n";
        } else {
            prompt += "<|im_start|>" + role + "\n" + content + "<|im_end|>\n";
        }
    }
    prompt += reasoning
            ? "<|im_start|>assistant\n<think>\n"
            : "<|im_start|>assistant\n<think></think>";
    return prompt;
}

constexpr const char * NPC_DECISION_GBNF = R"gbnf(
root ::= ws "{" ws intent ws "," ws spoken ws "," ws emotion ws "," ws event ws "," ws actions ws "," ws evidence ws "}" ws
intent ::= "\"intent\"" ws ":" ws string
spoken ::= "\"spokenText\"" ws ":" ws string
emotion ::= "\"emotion\"" ws ":" ws string
event ::= "\"paralinguisticEvent\"" ws ":" ws string
actions ::= "\"actions\"" ws ":" ws ("[]" | "[" ws action ws "]")
action ::= "{" ws "\"actionId\"" ws ":" ws string ws "," ws "\"targetStableId\"" ws ":" ws string ws "," ws "\"parameters\"" ws ":" ws object ws "}"
evidence ::= "\"groundingEvidenceRefs\"" ws ":" ws array
value ::= object | array | string | number | ("true" | "false" | "null")
object ::= "{" ws (string ws ":" ws value (ws "," ws string ws ":" ws value)*)? ws "}"
array ::= "[" ws (value (ws "," ws value)*)? ws "]"
string ::= "\"" ([^"\\] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4}))* "\""
number ::= "-"? ([0-9] | [1-9] [0-9]*) ("." [0-9]+)? ([eE] [+-]? [0-9]+)?
ws ::= [ \t\n\r]*
)gbnf";

class Runtime {
public:
    Runtime(HANDLE pipe, LlamaApi & api, std::string nonce, std::string manifest_hash,
            std::filesystem::path approved_model, std::string approved_model_hash)
        : pipe_(pipe), api_(api), nonce_(std::move(nonce)),
          manifest_hash_(lower(std::move(manifest_hash))),
          approved_model_(std::filesystem::weakly_canonical(std::move(approved_model))),
          approved_model_hash_(lower(std::move(approved_model_hash))),
          process_generation_(random_hex(16)) { }

    ~Runtime() { shutdown(); }

    void run() {
        while (!shutting_down_) {
            FrameHeader header{};
            if (!read_exact_available(pipe_, &header, sizeof(header), shutting_down_)) break;
            if (header.magic != FRAME_MAGIC || header.protocol_major != PROTOCOL_MAJOR
                    || header.payload_bytes > MAX_REQUEST_PAYLOAD
                    || header.sequence <= inbound_sequence_) {
                fail_protocol(header, "invalid frame header/version/sequence/size");
                break;
            }
            inbound_sequence_ = header.sequence;
            std::string payload(header.payload_bytes, '\0');
            if (!payload.empty() && !read_exact_available(
                    pipe_, payload.data(), payload.size(), shutting_down_)) break;
            try {
                std::cerr << "ORBISLLM_COMMAND type=" << header.message_type
                          << " sequence=" << header.sequence
                          << " payloadBytes=" << header.payload_bytes << std::endl;
                handle(header, payload.empty() ? json::object() : json::parse(payload));
            } catch (const std::exception & failure) {
                send_error(request_bytes(header), "COMMAND_FAILURE", failure.what(), false);
            }
        }
        shutdown();
    }

private:
    HANDLE pipe_;
    LlamaApi & api_;
    std::string nonce_;
    std::string manifest_hash_;
    std::filesystem::path approved_model_;
    std::string approved_model_hash_;
    std::string process_generation_;
    std::atomic<uint64_t> outbound_sequence_{0};
    uint64_t inbound_sequence_ = 0;
    std::mutex writer_mutex_;
    std::mutex lifecycle_mutex_;
    std::mutex resource_mutex_;
    ResourceSnapshot cached_resource_{};
    llama_model * model_ = nullptr;
    llama_context * context_ = nullptr;
    const llama_vocab * vocab_ = nullptr;
    std::thread generation_thread_;
    std::atomic<bool> active_{false};
    std::atomic<bool> cancel_{false};
    std::array<uint8_t, 16> active_request_{};
    std::string active_request_text_;
    std::atomic<bool> shutting_down_{false};
    int gpu_layers_ = 0;
    int context_size_ = 4096;
    int batch_size_ = 512;
    int microbatch_size_ = 128;
    int threads_ = 8;
    std::string model_id_;
    uint64_t context_epoch_ = 0;
    uint64_t model_epoch_ = 0;

    void send(MessageType type, const std::array<uint8_t, 16> & request, json payload) {
        payload["processGeneration"] = process_generation_;
        std::string body = payload.dump();
        if (body.size() > MAX_EVENT_PAYLOAD) {
            throw std::runtime_error("event payload exceeded bounded protocol ceiling");
        }
        FrameHeader header{};
        header.magic = FRAME_MAGIC;
        header.protocol_major = PROTOCOL_MAJOR;
        header.protocol_minor = PROTOCOL_MINOR;
        header.message_type = static_cast<uint16_t>(type);
        header.payload_bytes = static_cast<uint32_t>(body.size());
        header.sequence = ++outbound_sequence_;
        std::memcpy(header.request_id, request.data(), request.size());
        std::lock_guard lock(writer_mutex_);
        write_exact(pipe_, &header, sizeof(header));
        if (!body.empty()) write_exact(pipe_, body.data(), body.size());
    }

    void send_error(const std::array<uint8_t, 16> & request, std::string category,
            std::string detail, bool retriable) {
        try {
            send(MessageType::ERROR_MESSAGE, request, {
                {"category", std::move(category)}, {"detail", detail.substr(0, 600)},
                {"retriable", retriable}, {"state", state()}
            });
        } catch (...) { }
    }

    void fail_protocol(const FrameHeader & header, const std::string & detail) {
        send_error(request_bytes(header), "LLAMA_IPC_PROTOCOL_FAILURE", detail, false);
    }

    void handle(const FrameHeader & header, const json & body) {
        auto type = static_cast<MessageType>(header.message_type);
        auto request = request_bytes(header);
        switch (type) {
            case MessageType::HELLO: hello(request, body); break;
            case MessageType::LOAD_MODEL: load_model(request, body); break;
            case MessageType::CREATE_CONTEXT: create_context(request, body); break;
            case MessageType::GENERATE: generate(request, body); break;
            case MessageType::CANCEL: cancel(request, body); break;
            case MessageType::RELEASE_CONTEXT: release_context(request); break;
            case MessageType::UNLOAD_MODEL: unload_model(request); break;
            case MessageType::GET_STATUS: status(request); break;
            case MessageType::SHUTDOWN:
                shutting_down_ = true;
                cancel_ = true;
                if (generation_thread_.joinable()) generation_thread_.join();
                send(MessageType::SHUTDOWN_ACK, request, {{"state", "STOPPED"}});
                break;
            default: throw std::runtime_error("unsupported protocol message type");
        }
    }

    void hello(const std::array<uint8_t, 16> & request, const json & body) {
        if (body.value("nonce", "") != nonce_) throw std::runtime_error("launch nonce mismatch");
        if (lower(body.value("runtimeManifestHash", "")) != manifest_hash_) {
            throw std::runtime_error("runtime manifest hash mismatch");
        }
        send(MessageType::HELLO_ACK, request, {
            {"protocolMajor", PROTOCOL_MAJOR}, {"protocolMinor", PROTOCOL_MINOR},
            {"runtimeBuild", RUNTIME_BUILD}, {"llamaCommit", LLAMA_COMMIT},
            {"llamaVersion", api_.version()}, {"runtimeManifestHash", manifest_hash_},
            {"templateRevision", TEMPLATE_REVISION}, {"templateHash", TEMPLATE_SHA256},
            {"gpuOffloadSupported", api_.supports_gpu_offload()},
            {"backend", api_.supports_gpu_offload() ? "CUDA_OR_CPU" : "CPU"},
            {"pid", GetCurrentProcessId()}, {"state", state()}
        });
    }

    static bool load_progress(float progress, void * user_data) {
        auto * pair = static_cast<std::pair<Runtime *, std::array<uint8_t, 16>> *>(user_data);
        static thread_local int last_percent = -5;
        int percent = static_cast<int>(progress * 100.0f);
        if (percent >= last_percent + 5 || percent == 100) {
            last_percent = percent;
            pair->first->send(MessageType::MODEL_PROGRESS, pair->second,
                    {{"percent", percent}, {"state", "LOADING_MODEL"}});
        }
        return true;
    }

    void load_model(const std::array<uint8_t, 16> & request, const json & body) {
        std::lock_guard lock(lifecycle_mutex_);
        if (active_) throw std::runtime_error("cannot load model while request is active");
        auto requested_path = std::filesystem::weakly_canonical(
                std::filesystem::path(widen(body.value("modelPath", ""))));
        if (requested_path != approved_model_) throw std::runtime_error("unapproved model path");
        if (lower(body.value("modelSha256", "")) != approved_model_hash_) {
            throw std::runtime_error("unapproved model hash");
        }
        if (model_) {
            if (gpu_layers_ == body.value("gpuLayers", gpu_layers_)) {
                send(MessageType::READY, request, ready_payload());
                return;
            }
            free_context();
            api_.model_free(model_);
            model_ = nullptr;
            vocab_ = nullptr;
        }
        gpu_layers_ = std::clamp(body.value("gpuLayers", 0), 0, 999);
        model_id_ = body.value("modelId", "nemotron-3-nano-4b-q4_k_m");
        auto before = fresh_resources();
        send_resource(request, "BEFORE_MODEL_LOAD", before);
        auto parameters = api_.model_default_params();
        parameters.n_gpu_layers = gpu_layers_;
        std::pair<Runtime *, std::array<uint8_t, 16>> progress(this, request);
        parameters.progress_callback = &Runtime::load_progress;
        parameters.progress_callback_user_data = &progress;
        auto started = std::chrono::steady_clock::now();
        model_ = api_.model_load_from_file(narrow(approved_model_.wstring()).c_str(), parameters);
        if (!model_) throw std::runtime_error("libllama model load failed");
        vocab_ = api_.model_get_vocab(model_);
        ++model_epoch_;
        char description[512]{};
        api_.model_desc(model_, description, sizeof(description));
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - started).count();
        auto after = fresh_resources();
        send_resource(request, "AFTER_MODEL_LOAD", after);
        send(MessageType::READY, request, {
            {"state", "MODEL_LOADED"}, {"modelId", model_id_},
            {"modelDescription", description}, {"modelTensorBytes", api_.model_size(model_)},
            {"parameterCount", api_.model_n_params(model_)}, {"gpuLayers", gpu_layers_},
            {"modelEpoch", model_epoch_}, {"loadMillis", elapsed},
            {"embeddedTemplatePresent", api_.model_chat_template(model_, nullptr) != nullptr},
            {"approvedTemplateHash", TEMPLATE_SHA256}
        });
    }

    void create_context(const std::array<uint8_t, 16> & request, const json & body) {
        std::lock_guard lock(lifecycle_mutex_);
        if (!model_) throw std::runtime_error("model must be loaded before context creation");
        if (active_) throw std::runtime_error("cannot replace context while request is active");
        context_size_ = std::clamp(body.value("contextSize", 4096), 512, 32768);
        batch_size_ = std::clamp(body.value("batchSize", 512), 32, context_size_);
        microbatch_size_ = std::clamp(body.value("microbatchSize", 128), 16, batch_size_);
        threads_ = std::clamp(body.value("threads", 8), 1, 64);
        free_context();
        auto before = fresh_resources();
        send_resource(request, "BEFORE_CONTEXT_CREATE", before);
        auto parameters = api_.context_default_params();
        parameters.n_ctx = static_cast<uint32_t>(context_size_);
        parameters.n_batch = static_cast<uint32_t>(batch_size_);
        parameters.n_ubatch = static_cast<uint32_t>(microbatch_size_);
        parameters.n_seq_max = 1;
        parameters.n_threads = threads_;
        parameters.n_threads_batch = threads_;
        parameters.no_perf = false;
        context_ = api_.init_from_model(model_, parameters);
        if (!context_) throw std::runtime_error("libllama context creation failed");
        ++context_epoch_;
        auto after = fresh_resources();
        send_resource(request, "AFTER_CONTEXT_CREATE", after);
        send(MessageType::READY, request, ready_payload());
    }

    void generate(const std::array<uint8_t, 16> & request, json body) {
        auto received = std::chrono::steady_clock::now();
        std::cerr << "ORBISLLM_GENERATE_RECEIVED request="
                  << body.value("requestId", "") << std::endl;
        if (active_.exchange(true)) throw std::runtime_error("one active decode is permitted");
        if (!model_ || !context_) {
            active_ = false;
            throw std::runtime_error("model/context not ready");
        }
        if (generation_thread_.joinable()) generation_thread_.join();
        active_request_ = request;
        active_request_text_ = body.value("requestId", "");
        cancel_ = false;
        generation_thread_ = std::thread([this, request, body = std::move(body), received]() mutable {
            std::cerr << "ORBISLLM_GENERATION_THREAD_STARTED request="
                      << body.value("requestId", "") << std::endl;
            try { run_generation(request, body, received); }
            catch (const std::exception & failure) {
                active_ = false;
                send_error(request, "GENERATION_FAILURE", failure.what(), true);
            }
            active_ = false;
            active_request_.fill(0);
            active_request_text_.clear();
        });
    }

    void run_generation(const std::array<uint8_t, 16> & request, const json & body,
            std::chrono::steady_clock::time_point received) {
        auto started = std::chrono::steady_clock::now();
        const auto sidecar_queue_millis = elapsed(received);
        std::cerr << "ORBISLLM_GENERATION_STAGE stage=RESOURCE_BEFORE" << std::endl;
        auto before = cached_resources();
        std::cerr << "ORBISLLM_GENERATION_STAGE stage=RESOURCE_CACHED" << std::endl;
        send_resource(request, "BEFORE_REQUEST", before);
        const auto resource_millis = elapsed(started);
        std::cerr << "ORBISLLM_GENERATION_STAGE stage=RESOURCE_SENT" << std::endl;
        bool reasoning = body.value("reasoningMode", "DISABLED") == "ENABLED";
        bool structured = body.value("structured", false);
        std::string contract = body.value("outputContractId", "dialogue-text-v1");
        int maximum_tokens = std::clamp(body.value("maxTokens", 80), 1, 1024);
        double temperature = std::clamp(body.value("temperature", 0.3), 0.0, 2.0);
        double top_p = std::clamp(body.value("topP", 1.0), 0.0, 1.0);
        int top_k = std::clamp(body.value("topK", 40), 0, 200);
        uint32_t seed = body.value("seed", 0xffffffffu);
        auto template_started = std::chrono::steady_clock::now();
        std::string prompt = render_nemotron(body.at("messages"), reasoning);
        const auto template_millis = elapsed(template_started);
        std::cerr << "ORBISLLM_GENERATION_STAGE stage=TOKENIZE promptBytes="
                  << prompt.size() << std::endl;
        auto tokenize_started = std::chrono::steady_clock::now();
        std::string prompt_hash = sha256_hex(prompt.data(), prompt.size());
        int count = api_.tokenize(vocab_, prompt.data(), static_cast<int32_t>(prompt.size()),
                nullptr, 0, true, true);
        if (count >= 0) throw std::runtime_error("tokenizer sizing contract changed");
        std::vector<llama_token> tokens(static_cast<size_t>(-count));
        count = api_.tokenize(vocab_, prompt.data(), static_cast<int32_t>(prompt.size()),
                tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        if (count < 0) throw std::runtime_error("prompt tokenization failed");
        tokens.resize(static_cast<size_t>(count));
        const auto tokenization_millis = elapsed(tokenize_started);
        if (tokens.size() + static_cast<size_t>(maximum_tokens) > api_.n_ctx(context_)) {
            throw std::runtime_error("compiled prompt/output exceeds context allocation");
        }
        auto kv_clear_started = std::chrono::steady_clock::now();
        api_.memory_clear(api_.get_memory(context_), true);
        const auto kv_clear_millis = elapsed(kv_clear_started);
        send(MessageType::REQUEST_ACCEPTED, request, {
            {"requestId", body.value("requestId", "")},
            {"turnId", body.value("turnId", "")},
            {"responseId", body.value("responseId", "")},
            {"branchEpoch", body.value("branchEpoch", 0ll)},
            {"modelEpoch", model_epoch_}, {"contextEpoch", context_epoch_},
            {"promptHash", prompt_hash}, {"promptTokens", tokens.size()},
            {"templateHash", TEMPLATE_SHA256}, {"reasoningMode", reasoning ? "ENABLED" : "DISABLED"},
            {"outputContractId", contract}, {"state", "PREFILL"}
        });
        auto prefill_started = std::chrono::steady_clock::now();
        for (size_t offset = 0; offset < tokens.size(); offset += microbatch_size_) {
            if (cancel_) return cancelled(request, "PREFILL", started);
            size_t amount = std::min<size_t>(microbatch_size_, tokens.size() - offset);
            llama_batch batch = api_.batch_get_one(tokens.data() + offset,
                    static_cast<int32_t>(amount));
            int code = api_.decode(context_, batch);
            if (code != 0) throw std::runtime_error("libllama prefill decode failed code="
                    + std::to_string(code));
            send(MessageType::PROMPT_PROGRESS, request, {
                {"processedTokens", offset + amount}, {"totalTokens", tokens.size()},
                {"cancellationSafeBoundary", true},
                {"elapsedMillis", elapsed(prefill_started)}
            });
            if (cancel_) return cancelled(request, "PREFILL", started);
        }
        const auto prompt_millis = elapsed(prefill_started);
        const auto after_prefill = std::chrono::steady_clock::now();
        auto chain_parameters = api_.sampler_chain_default_params();
        chain_parameters.no_perf = false;
        llama_sampler * sampler = api_.sampler_chain_init(chain_parameters);
        if (!sampler) throw std::runtime_error("sampler chain creation failed");
        auto free_sampler = [&] { api_.sampler_free(sampler); };
        if (structured) {
            llama_sampler * grammar = api_.sampler_init_grammar(vocab_, NPC_DECISION_GBNF, "root");
            if (!grammar) {
                free_sampler();
                throw std::runtime_error("prevalidated npc-decision-v1 grammar initialization failed");
            }
            api_.sampler_chain_add(sampler, grammar);
        }
        api_.sampler_chain_add(sampler, api_.sampler_init_penalties(
                api_.vocab_n_tokens(vocab_), 64, 1.1f, 0.0f, 0.0f));
        if (temperature <= 0.0001) {
            api_.sampler_chain_add(sampler, api_.sampler_init_greedy());
        } else {
            api_.sampler_chain_add(sampler, api_.sampler_init_top_k(top_k));
            api_.sampler_chain_add(sampler, api_.sampler_init_top_p(
                    static_cast<float>(top_p), 1));
            api_.sampler_chain_add(sampler, api_.sampler_init_temp(
                    static_cast<float>(temperature)));
            api_.sampler_chain_add(sampler, api_.sampler_init_dist(seed));
        }
        std::string final_text;
        std::string reasoning_pending;
        bool waiting_for_reasoning_end = reasoning;
        int reasoning_events = 0;
        int reasoning_tokens = 0;
        int generated_tokens = 0;
        int final_tokens = 0;
        long long first_token_millis = -1;
        long long first_decode_millis = -1;
        std::string finish_reason = "length";
        for (int index = 0; index < maximum_tokens; ++index) {
            if (cancel_) {
                free_sampler();
                return cancelled(request, "GENERATION", started);
            }
            llama_token token = api_.sampler_sample(sampler, context_, -1);
            if (api_.vocab_is_eog(vocab_, token)) {
                finish_reason = "stop";
                break;
            }
            char buffer[1024];
            int piece_size = api_.token_to_piece(vocab_, token, buffer,
                    static_cast<int32_t>(sizeof(buffer)), 0, true);
            std::string piece;
            if (piece_size < 0) {
                std::vector<char> large(static_cast<size_t>(-piece_size));
                piece_size = api_.token_to_piece(vocab_, token, large.data(),
                        static_cast<int32_t>(large.size()), 0, true);
                if (piece_size < 0) { free_sampler(); throw std::runtime_error("token decode failed"); }
                piece.assign(large.data(), static_cast<size_t>(piece_size));
            } else {
                piece.assign(buffer, static_cast<size_t>(piece_size));
            }
            ++generated_tokens;
            if (first_token_millis < 0) {
                first_token_millis = elapsed(started);
                first_decode_millis = elapsed(after_prefill);
            }
            if (waiting_for_reasoning_end) {
                reasoning_pending += piece;
                auto end = reasoning_pending.find("</think>");
                if (end == std::string::npos) {
                    ++reasoning_tokens;
                    if (reasoning_pending.size() >= 64) {
                        ++reasoning_events;
                        send(MessageType::REASONING_DELTA, request,
                                {{"characters", reasoning_pending.size()},
                                 {"tokenCount", reasoning_tokens}});
                        reasoning_pending.clear();
                    }
                } else {
                    reasoning_tokens += 1;
                    ++reasoning_events;
                    send(MessageType::REASONING_DELTA, request,
                            {{"characters", end}, {"tokenCount", reasoning_tokens},
                             {"terminal", true}});
                    std::string remainder = reasoning_pending.substr(end + 8);
                    reasoning_pending.clear();
                    waiting_for_reasoning_end = false;
                    if (!remainder.empty()) {
                        final_text += remainder;
                        ++final_tokens;
                        if (!structured) send(MessageType::FINAL_DELTA, request,
                                {{"text", remainder}, {"tokenIndex", generated_tokens}});
                    }
                }
            } else {
                final_text += piece;
                ++final_tokens;
                if (!structured) send(MessageType::FINAL_DELTA, request,
                        {{"text", piece}, {"tokenIndex", generated_tokens}});
            }
            llama_batch batch = api_.batch_get_one(&token, 1);
            int code = api_.decode(context_, batch);
            if (code != 0) { free_sampler(); throw std::runtime_error(
                    "libllama generation decode failed code=" + std::to_string(code)); }
            if (cancel_) { free_sampler(); return cancelled(request, "GENERATION", started); }
        }
        free_sampler();
        if (waiting_for_reasoning_end) {
            throw std::runtime_error("reasoning stream completed without final dialogue");
        }
        if (structured) {
            send(MessageType::CONTRACT_COMPLETE, request, {
                {"text", final_text}, {"outputContractId", contract},
                {"finishReason", finish_reason}
            });
        }
        auto completed_millis = elapsed(started);
        // The next request may be dispatched as soon as this terminal event is observed.
        // Mark the decode idle first; generate() will still join this thread before reuse.
        active_ = false;
        send(MessageType::REQUEST_COMPLETE, request, {
            {"text", final_text}, {"finishReason", finish_reason},
            {"promptTokens", tokens.size()}, {"completionTokens", generated_tokens},
            {"reasoningTokens", reasoning_tokens}, {"reasoningEvents", reasoning_events},
            {"finalAnswerTokens", final_tokens},
            {"ttftMillis", first_token_millis < 0 ? completed_millis : first_token_millis},
            {"completionMillis", completed_millis}, {"promptEvaluationMillis", prompt_millis},
            {"sidecarQueueMillis", sidecar_queue_millis},
            {"resourceDispatchMillis", resource_millis},
            {"templateMillis", template_millis},
            {"tokenizationMillis", tokenization_millis},
            {"kvClearMillis", kv_clear_millis},
            {"prefillMillis", prompt_millis},
            {"firstDecodeMillis", first_decode_millis},
            {"tokensPerSecond", completed_millis <= 0 ? 0.0
                    : generated_tokens * 1000.0 / completed_millis},
            {"state", "READY"}
        });
        send_resource(request, "AFTER_REQUEST", cached_resources());
    }

    void cancelled(const std::array<uint8_t, 16> & request, const char * stage,
            std::chrono::steady_clock::time_point started) {
        api_.memory_clear(api_.get_memory(context_), true);
        // CANCEL_ACK is the proof that the one-decode runtime is safe to reuse.
        active_ = false;
        send(MessageType::CANCEL_ACK, request, {
            {"requestId", active_request_text_}, {"stage", stage},
            {"drainMillis", elapsed(started)}, {"resourcesReleased", true},
            {"state", "READY"}
        });
        send_resource(request, "AFTER_CANCEL", cached_resources());
    }

    void cancel(const std::array<uint8_t, 16> & request, const json & body) {
        if (!active_) {
            send(MessageType::CANCEL_ACK, request, {
                {"requestId", body.value("requestId", "")}, {"alreadyTerminal", true},
                {"resourcesReleased", true}, {"state", state()}
            });
            return;
        }
        if (request != active_request_) throw std::runtime_error("cancel request ID mismatch");
        cancel_ = true;
        send(MessageType::CANCEL_REQUESTED, request, {
            {"requestId", active_request_text_}, {"reason", body.value("reason", "UNKNOWN")},
            {"state", "CANCELLING"}
        });
    }

    void release_context(const std::array<uint8_t, 16> & request) {
        std::lock_guard lock(lifecycle_mutex_);
        if (active_) throw std::runtime_error("cannot release active context");
        if (generation_thread_.joinable()) generation_thread_.join();
        free_context();
        send(MessageType::READY, request, {{"state", model_ ? "MODEL_LOADED" : "STARTED"},
                {"contextReleased", true}, {"modelRetained", model_ != nullptr}});
        send_resource(request, "AFTER_CONTEXT_RELEASE", fresh_resources());
    }

    void unload_model(const std::array<uint8_t, 16> & request) {
        std::lock_guard lock(lifecycle_mutex_);
        if (active_) throw std::runtime_error("cannot unload active model");
        if (generation_thread_.joinable()) generation_thread_.join();
        free_context();
        if (model_) api_.model_free(model_);
        model_ = nullptr;
        vocab_ = nullptr;
        model_id_.clear();
        send(MessageType::READY, request, {{"state", "STARTED"}, {"modelUnloaded", true}});
        send_resource(request, "AFTER_MODEL_UNLOAD", fresh_resources());
    }

    void status(const std::array<uint8_t, 16> & request) {
        json payload = ready_payload();
        payload["state"] = state();
        payload["activeRequest"] = active_request_text_;
        payload["cancelRequested"] = cancel_.load();
        payload["pid"] = GetCurrentProcessId();
        payload["llamaVersion"] = api_.version();
        payload["runtimeBuild"] = RUNTIME_BUILD;
        send(MessageType::STATUS, request, payload);
        send_resource(request, "STATUS", active_ ? cached_resources() : fresh_resources());
    }

    json ready_payload() const {
        return {
            {"state", context_ ? "READY" : model_ ? "MODEL_LOADED" : "STARTED"},
            {"modelLoaded", model_ != nullptr}, {"contextLoaded", context_ != nullptr},
            {"modelId", model_id_}, {"gpuLayers", gpu_layers_},
            {"modelEpoch", model_epoch_}, {"contextEpoch", context_epoch_},
            {"contextSize", context_ ? api_.n_ctx(context_) : 0},
            {"batchSize", context_ ? api_.n_batch(context_) : 0},
            {"microbatchSize", context_ ? api_.n_ubatch(context_) : 0},
            {"active", active_.load()}, {"templateHash", TEMPLATE_SHA256}
        };
    }

    void send_resource(const std::array<uint8_t, 16> & request,
            const char * boundary, const ResourceSnapshot & snapshot) {
        send(MessageType::RESOURCE_SNAPSHOT, request, {
            {"boundary", boundary}, {"freeVramMiB", snapshot.free_vram_mib},
            {"usedVramMiB", snapshot.used_vram_mib},
            {"totalVramMiB", snapshot.total_vram_mib},
            {"processWorkingSetMiB", snapshot.working_set_mib},
            {"processPrivateMiB", snapshot.private_mib},
            {"processVramMiB", -1}, {"measurementStatus", snapshot.measurement},
            {"modelLoaded", model_ != nullptr}, {"contextLoaded", context_ != nullptr},
            {"activeRequest", active_.load()}, {"gpuLayers", gpu_layers_}
        });
    }

    ResourceSnapshot fresh_resources() {
        ResourceSnapshot snapshot = resources(api_, true);
        std::lock_guard lock(resource_mutex_);
        cached_resource_ = snapshot;
        return snapshot;
    }

    ResourceSnapshot cached_resources() {
        std::lock_guard lock(resource_mutex_);
        ResourceSnapshot snapshot = cached_resource_;
        snapshot.measurement = cached_resource_.measurement == "UNKNOWN"
                ? "UNKNOWN" : "CACHED_LIFECYCLE_CUDA_GLOBAL_PROCESS_VRAM_UNKNOWN_WDDM";
        return snapshot;
    }

    std::string state() const {
        if (shutting_down_) return "STOPPING";
        if (active_ && cancel_) return "CANCELLING";
        if (active_) return "GENERATING";
        if (context_) return "READY";
        if (model_) return "MODEL_LOADED";
        return "STARTED";
    }

    static long long elapsed(std::chrono::steady_clock::time_point started) {
        return std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - started).count();
    }

    void free_context() {
        if (context_) api_.context_free(context_);
        context_ = nullptr;
    }

    void shutdown() {
        if (!shutting_down_.exchange(true)) cancel_ = true;
        if (generation_thread_.joinable()) generation_thread_.join();
        std::lock_guard lock(lifecycle_mutex_);
        free_context();
        if (model_) api_.model_free(model_);
        model_ = nullptr;
        vocab_ = nullptr;
    }
};

std::optional<std::string> argument(int argc, char ** argv, const std::string & key) {
    for (int i = 1; i + 1 < argc; ++i) if (argv[i] == key) return argv[i + 1];
    return std::nullopt;
}

void parent_watchdog(DWORD parent_pid) {
    HANDLE parent = OpenProcess(SYNCHRONIZE, FALSE, parent_pid);
    if (!parent) ExitProcess(91);
    WaitForSingleObject(parent, INFINITE);
    CloseHandle(parent);
    ExitProcess(0);
}

} // namespace

int main(int argc, char ** argv) {
    try {
        auto pipe_name = argument(argc, argv, "--pipe").value_or("");
        auto nonce = argument(argc, argv, "--nonce").value_or("");
        auto manifest_hash = argument(argc, argv, "--manifest-hash").value_or("");
        auto runtime_dir_text = argument(argc, argv, "--runtime-dir").value_or("");
        auto model_path_text = argument(argc, argv, "--approved-model-path").value_or("");
        auto model_hash = argument(argc, argv, "--approved-model-sha256").value_or("");
        auto instance_id = argument(argc, argv, "--instance-id").value_or("");
        auto parent_text = argument(argc, argv, "--parent-pid").value_or("0");
        if (pipe_name.empty() || nonce.size() < 32 || manifest_hash.size() != 64
                || runtime_dir_text.empty() || model_path_text.empty() || model_hash.size() != 64
                || instance_id.empty()) {
            throw std::runtime_error("complete verified launch arguments are required");
        }
        DWORD parent_pid = static_cast<DWORD>(std::stoul(parent_text));
        if (parent_pid == 0) throw std::runtime_error("valid parent PID is required");
        std::thread(parent_watchdog, parent_pid).detach();
        SecurityDescriptor security;
        std::wstring mutex_name = L"Local\\OrbisLLM-" + widen(instance_id);
        HANDLE instance_mutex = CreateMutexW(&security.attributes, FALSE, mutex_name.c_str());
        if (!instance_mutex || GetLastError() == ERROR_ALREADY_EXISTS) {
            throw std::runtime_error("another OrbisLLM instance owns this data root");
        }
        std::filesystem::path runtime_dir = std::filesystem::weakly_canonical(widen(runtime_dir_text));
        LlamaApi api;
        api.load(runtime_dir);
        std::wstring full_pipe = L"\\\\.\\pipe\\" + widen(pipe_name);
        HANDLE pipe = CreateNamedPipeW(full_pipe.c_str(),
                PIPE_ACCESS_DUPLEX | FILE_FLAG_FIRST_PIPE_INSTANCE,
                PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
                1, MAX_EVENT_PAYLOAD + sizeof(FrameHeader),
                MAX_REQUEST_PAYLOAD + sizeof(FrameHeader), 0, &security.attributes);
        if (pipe == INVALID_HANDLE_VALUE) throw std::runtime_error("CreateNamedPipe failed");
        BOOL connected = ConnectNamedPipe(pipe, nullptr)
                || GetLastError() == ERROR_PIPE_CONNECTED;
        if (!connected) {
            CloseHandle(pipe);
            throw std::runtime_error("ConnectNamedPipe failed");
        }
        Runtime runtime(pipe, api, nonce, manifest_hash,
                std::filesystem::path(widen(model_path_text)), model_hash);
        runtime.run();
        FlushFileBuffers(pipe);
        DisconnectNamedPipe(pipe);
        CloseHandle(pipe);
        ReleaseMutex(instance_mutex);
        CloseHandle(instance_mutex);
        return 0;
    } catch (const std::exception & failure) {
        std::cerr << "ORBISLLM_FATAL category=STARTUP_FAILURE detail="
                  << failure.what() << std::endl;
        return 2;
    }
}
