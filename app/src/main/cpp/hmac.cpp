#include "hmac.h"

#include <cstring>

namespace caplease::crypto {

namespace {

constexpr std::size_t kInnerPad = 0x36;
constexpr std::size_t kOuterPad = 0x5c;

}

void wipe(void* data, std::size_t length) noexcept
{
    if (data == nullptr) {
        return;
    }

    volatile std::uint8_t* cursor = static_cast<volatile std::uint8_t*>(data);
    while (length-- > 0) {
        *cursor++ = 0;
    }
}

bool constantTimeEquals(const void* left, const void* right, std::size_t length) noexcept
{
    if (left == nullptr || right == nullptr) {
        return false;
    }

    const auto* a = static_cast<const std::uint8_t*>(left);
    const auto* b = static_cast<const std::uint8_t*>(right);

    std::uint8_t difference = 0;
    for (std::size_t i = 0; i < length; ++i) {
        difference |= static_cast<std::uint8_t>(a[i] ^ b[i]);
    }

    return difference == 0;
}

void hmacSha256(
    const void* key,
    std::size_t keyLength,
    const void* message,
    std::size_t messageLength,
    std::uint8_t out[kMacSize]) noexcept
{
    std::uint8_t normalized[kBlockSize] = {};

    if (keyLength > kBlockSize) {
        sha256(key, keyLength, normalized);
    } else if (key != nullptr && keyLength > 0) {
        std::memcpy(normalized, key, keyLength);
    }

    std::uint8_t innerPad[kBlockSize];
    std::uint8_t outerPad[kBlockSize];

    for (std::size_t i = 0; i < kBlockSize; ++i) {
        innerPad[i] = static_cast<std::uint8_t>(normalized[i] ^ kInnerPad);
        outerPad[i] = static_cast<std::uint8_t>(normalized[i] ^ kOuterPad);
    }

    std::uint8_t innerDigest[kDigestSize];

    Sha256 inner;
    inner.update(innerPad, kBlockSize);
    inner.update(message, messageLength);
    inner.finish(innerDigest);

    Sha256 outer;
    outer.update(outerPad, kBlockSize);
    outer.update(innerDigest, kDigestSize);
    outer.finish(out);

    wipe(normalized, sizeof(normalized));
    wipe(innerPad, sizeof(innerPad));
    wipe(outerPad, sizeof(outerPad));
    wipe(innerDigest, sizeof(innerDigest));
}

void pbkdf2Sha256(
    const void* password,
    std::size_t passwordLength,
    const void* salt,
    std::size_t saltLength,
    std::uint32_t iterations,
    std::uint8_t* out,
    std::size_t outLength) noexcept
{
    if (out == nullptr || outLength == 0 || iterations == 0) {
        return;
    }

    const std::size_t blockCount = (outLength + kDigestSize - 1) / kDigestSize;

    std::uint8_t blockSalt[1024];
    if (saltLength > sizeof(blockSalt) - 4) {
        return;
    }

    if (salt != nullptr && saltLength > 0) {
        std::memcpy(blockSalt, salt, saltLength);
    }

    std::uint8_t accumulator[kDigestSize];
    std::uint8_t current[kDigestSize];

    for (std::size_t block = 0; block < blockCount; ++block) {
        const std::uint32_t counter = static_cast<std::uint32_t>(block) + 1u;

        blockSalt[saltLength + 0] = static_cast<std::uint8_t>(counter >> 24u);
        blockSalt[saltLength + 1] = static_cast<std::uint8_t>(counter >> 16u);
        blockSalt[saltLength + 2] = static_cast<std::uint8_t>(counter >> 8u);
        blockSalt[saltLength + 3] = static_cast<std::uint8_t>(counter);

        hmacSha256(password, passwordLength, blockSalt, saltLength + 4, current);
        std::memcpy(accumulator, current, kDigestSize);

        for (std::uint32_t round = 1; round < iterations; ++round) {
            hmacSha256(password, passwordLength, current, kDigestSize, current);
            for (std::size_t i = 0; i < kDigestSize; ++i) {
                accumulator[i] = static_cast<std::uint8_t>(accumulator[i] ^ current[i]);
            }
        }

        const std::size_t offset = block * kDigestSize;
        const std::size_t remaining = outLength - offset;
        const std::size_t take = remaining < kDigestSize ? remaining : kDigestSize;
        std::memcpy(out + offset, accumulator, take);
    }

    wipe(blockSalt, sizeof(blockSalt));
    wipe(accumulator, sizeof(accumulator));
    wipe(current, sizeof(current));
}

}