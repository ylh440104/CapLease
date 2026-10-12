#pragma once

#include <cstddef>
#include <cstdint>

#include "sha256.h"

namespace caplease::crypto {

inline constexpr std::size_t kMacSize = kDigestSize;

void hmacSha256(
    const void* key,
    std::size_t keyLength,
    const void* message,
    std::size_t messageLength,
    std::uint8_t out[kMacSize]) noexcept;

void pbkdf2Sha256(
    const void* password,
    std::size_t passwordLength,
    const void* salt,
    std::size_t saltLength,
    std::uint32_t iterations,
    std::uint8_t* out,
    std::size_t outLength) noexcept;

bool constantTimeEquals(const void* left, const void* right, std::size_t length) noexcept;

void wipe(void* data, std::size_t length) noexcept;

}