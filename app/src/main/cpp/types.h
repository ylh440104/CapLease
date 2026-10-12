#pragma once

#include <cstddef>
#include <cstdint>

#include "protocol.h"

namespace caplease {

inline constexpr std::size_t kMaxLeaseSlots = 64;
inline constexpr std::uint32_t kPbkdf2Iterations = 120000;
inline constexpr std::uint64_t kDefaultLeaseSeconds = 300;
inline constexpr std::uint64_t kMaxLeaseSeconds = 86400;

enum class Capability : std::uint64_t {
    FsReadApp = kCapFsReadApp,
    FsWriteApp = kCapFsWriteApp,
    NetRaw = kCapNetRaw,
    NetAdmin = kCapNetAdmin,
    Mount = kCapMount,
    Module = kCapModule,
    KernelLog = kCapKernelLog,
    Ptrace = kCapPtrace,
    SetIdentity = kCapSetIdentity,
    RelaxLabel = kCapRelaxLabel,
    AuditRead = kCapAuditRead,
    DeviceNode = kCapDeviceNode,
};

inline constexpr std::uint64_t capabilityBit(Capability capability) noexcept
{
    return static_cast<std::uint64_t>(capability);
}

inline constexpr std::uint64_t kAllCapabilities =
    kCapFsReadApp | kCapFsWriteApp | kCapNetRaw | kCapNetAdmin | kCapMount
    | kCapModule | kCapKernelLog | kCapPtrace | kCapSetIdentity | kCapRelaxLabel
    | kCapAuditRead | kCapDeviceNode;

inline constexpr std::uint64_t kDangerousCapabilities =
    kCapModule | kCapSetIdentity | kCapRelaxLabel | kCapMount | kCapNetAdmin;

inline constexpr bool isDangerous(std::uint64_t capabilities) noexcept
{
    return (capabilities & kDangerousCapabilities) != 0;
}

struct Lease {
    std::uint64_t id;
    std::uint64_t capabilities;
    std::uint64_t expirySeconds;
    std::uint32_t flags;
    std::uint32_t ownerUid;
    std::uint32_t ownerPid;
};

struct Info {
    std::uint32_t kernelPatchVersion;
    std::uint32_t kernelVersion;
    std::uint32_t protocolVersion;
    std::uint32_t leaseSlots;
    std::uint64_t capabilityMask;
    std::uint64_t bootSeconds;
};

struct AuditEntry {
    std::uint64_t timestampSeconds;
    std::uint64_t value;
    std::uint32_t kind;
    std::uint32_t actorUid;
    std::uint32_t actorPid;
    std::uint32_t detail;
};

struct Result {
    std::int32_t status;
    std::uint64_t value;
};

struct SessionInfo {
    std::uint32_t sessionId;
    std::uint64_t issuedSeconds;
    std::uint64_t expiresSeconds;
};

}