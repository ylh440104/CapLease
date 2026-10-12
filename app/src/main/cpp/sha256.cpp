#include "sha256.h"

#include <cstring>

namespace caplease::crypto {

namespace {

constexpr std::array<std::uint32_t, 64> kRoundConstants = {
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u,
    0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u,
    0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu,
    0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u,
    0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u,
    0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u,
    0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u,
    0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u,
    0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u,
};

constexpr std::array<std::uint32_t, 8> kInitialState = {
    0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
    0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u,
};

constexpr std::uint32_t rotateRight(std::uint32_t value, unsigned int count) noexcept
{
    return (value >> count) | (value << (32u - count));
}

constexpr std::uint32_t bigSigma0(std::uint32_t value) noexcept
{
    return rotateRight(value, 2u) ^ rotateRight(value, 13u) ^ rotateRight(value, 22u);
}

constexpr std::uint32_t bigSigma1(std::uint32_t value) noexcept
{
    return rotateRight(value, 6u) ^ rotateRight(value, 11u) ^ rotateRight(value, 25u);
}

constexpr std::uint32_t smallSigma0(std::uint32_t value) noexcept
{
    return rotateRight(value, 7u) ^ rotateRight(value, 18u) ^ (value >> 3u);
}

constexpr std::uint32_t smallSigma1(std::uint32_t value) noexcept
{
    return rotateRight(value, 17u) ^ rotateRight(value, 19u) ^ (value >> 10u);
}

constexpr std::uint32_t choose(std::uint32_t x, std::uint32_t y, std::uint32_t z) noexcept
{
    return (x & y) ^ (~x & z);
}

constexpr std::uint32_t majority(std::uint32_t x, std::uint32_t y, std::uint32_t z) noexcept
{
    return (x & y) ^ (x & z) ^ (y & z);
}

std::uint32_t loadBigEndian(const std::uint8_t* data) noexcept
{
    return (static_cast<std::uint32_t>(data[0]) << 24u)
         | (static_cast<std::uint32_t>(data[1]) << 16u)
         | (static_cast<std::uint32_t>(data[2]) << 8u)
         | static_cast<std::uint32_t>(data[3]);
}

void storeBigEndian(std::uint8_t* out, std::uint32_t value) noexcept
{
    out[0] = static_cast<std::uint8_t>(value >> 24u);
    out[1] = static_cast<std::uint8_t>(value >> 16u);
    out[2] = static_cast<std::uint8_t>(value >> 8u);
    out[3] = static_cast<std::uint8_t>(value);
}

void compress(std::uint32_t state[8], const std::uint8_t* block) noexcept
{
    std::uint32_t schedule[64];

    for (std::size_t i = 0; i < 16; ++i) {
        schedule[i] = loadBigEndian(block + i * 4);
    }

    for (std::size_t i = 16; i < 64; ++i) {
        schedule[i] = smallSigma1(schedule[i - 2]) + schedule[i - 7]
                    + smallSigma0(schedule[i - 15]) + schedule[i - 16];
    }

    std::uint32_t a = state[0];
    std::uint32_t b = state[1];
    std::uint32_t c = state[2];
    std::uint32_t d = state[3];
    std::uint32_t e = state[4];
    std::uint32_t f = state[5];
    std::uint32_t g = state[6];
    std::uint32_t h = state[7];

    for (std::size_t i = 0; i < 64; ++i) {
        const std::uint32_t t1 = h + bigSigma1(e) + choose(e, f, g) + kRoundConstants[i] + schedule[i];
        const std::uint32_t t2 = bigSigma0(a) + majority(a, b, c);
        h = g;
        g = f;
        f = e;
        e = d + t1;
        d = c;
        c = b;
        b = a;
        a = t1 + t2;
    }

    state[0] += a;
    state[1] += b;
    state[2] += c;
    state[3] += d;
    state[4] += e;
    state[5] += f;
    state[6] += g;
    state[7] += h;
}

}

Sha256::Sha256() noexcept
{
    reset();
}

void Sha256::reset() noexcept
{
    state_ = kInitialState;
    pending_.fill(0);
    pendingLength_ = 0;
    totalLength_ = 0;
}

void Sha256::absorb(const std::uint8_t* block) noexcept
{
    compress(state_.data(), block);
}

void Sha256::update(const void* data, std::size_t length) noexcept
{
    if (data == nullptr || length == 0) {
        return;
    }

    const auto* bytes = static_cast<const std::uint8_t*>(data);
    totalLength_ += static_cast<std::uint64_t>(length);

    if (pendingLength_ > 0) {
        const std::size_t needed = kBlockSize - pendingLength_;
        const std::size_t take = length < needed ? length : needed;

        std::memcpy(pending_.data() + pendingLength_, bytes, take);
        pendingLength_ += take;
        bytes += take;
        length -= take;

        if (pendingLength_ == kBlockSize) {
            absorb(pending_.data());
            pendingLength_ = 0;
        }
    }

    while (length >= kBlockSize) {
        absorb(bytes);
        bytes += kBlockSize;
        length -= kBlockSize;
    }

    if (length > 0) {
        std::memcpy(pending_.data(), bytes, length);
        pendingLength_ = length;
    }
}

void Sha256::finish(std::uint8_t out[kDigestSize]) noexcept
{
    const std::uint64_t bitLength = totalLength_ * 8u;

    std::uint8_t padding[kBlockSize * 2] = {};
    padding[0] = 0x80u;

    const std::size_t remainder = static_cast<std::size_t>(totalLength_ % kBlockSize);
    const std::size_t padLength = (remainder < 56) ? (56 - remainder) : (120 - remainder);

    std::uint8_t trailer[8];
    for (std::size_t i = 0; i < 8; ++i) {
        trailer[i] = static_cast<std::uint8_t>(bitLength >> (56u - i * 8u));
    }

    update(padding, padLength);
    update(trailer, sizeof(trailer));

    for (std::size_t i = 0; i < 8; ++i) {
        storeBigEndian(out + i * 4, state_[i]);
    }
}

void sha256(const void* data, std::size_t length, std::uint8_t out[kDigestSize]) noexcept
{
    Sha256 context;
    context.update(data, length);
    context.finish(out);
}

}