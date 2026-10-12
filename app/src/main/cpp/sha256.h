#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace caplease::crypto {

inline constexpr std::size_t kDigestSize = 32;
inline constexpr std::size_t kBlockSize = 64;

class Sha256 {
public:
    Sha256() noexcept;

    void update(const void* data, std::size_t length) noexcept;
    void finish(std::uint8_t out[kDigestSize]) noexcept;
    void reset() noexcept;

private:
    void absorb(const std::uint8_t* block) noexcept;

    std::array<std::uint32_t, 8> state_;
    std::array<std::uint8_t, kBlockSize> pending_;
    std::size_t pendingLength_;
    std::uint64_t totalLength_;
};

void sha256(const void* data, std::size_t length, std::uint8_t out[kDigestSize]) noexcept;

}