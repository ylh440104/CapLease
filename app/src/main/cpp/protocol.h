#pragma once

#include <cstddef>
#include <cstdint>

namespace caplease {

inline constexpr long kSupercallNumber = 45;

inline constexpr std::uint32_t kProtocolMagic = 0x434C5031u;
inline constexpr std::uint32_t kProtocolVersion = 2u;

inline constexpr std::size_t kTokenSize = 32;
inline constexpr std::size_t kNonceSize = 32;
inline constexpr std::size_t kPayloadSize = 512;
inline constexpr std::size_t kPackageNameMax = 128;

enum class Op : std::uint32_t {
    Hello = 1,
    Info = 2,
    Challenge = 3,
    Authenticate = 4,
    SessionDrop = 5,
    Pair = 6,
    Unpair = 7,
    LeaseAcquire = 16,
    LeaseRelease = 17,
    LeaseQuery = 18,
    LeaseExtend = 19,
    LeaseRevokeAll = 20,
    Exec = 32,
    AuditRead = 48,
    AuditClear = 49,
};

enum class Status : std::int32_t {
    Ok = 0,
    NotReady = -1,
    BadMagic = -2,
    BadVersion = -3,
    UnknownOp = -4,
    InvalidArgument = -5,
    Unauthenticated = -6,
    BadCredential = -7,
    ChallengeMissing = -8,
    ChallengeExpired = -9,
    AlreadyPaired = -10,
    NotPaired = -11,
    CapabilityDenied = -12,
    LeaseMissing = -13,
    LeaseExpired = -14,
    LeaseLimitReached = -15,
    TargetDenied = -16,
    Internal = -17,
    NotSupported = -18,
    BufferTooSmall = -19,
};

inline constexpr std::uint64_t kCapFsReadApp = 1ull << 0;
inline constexpr std::uint64_t kCapFsWriteApp = 1ull << 1;
inline constexpr std::uint64_t kCapNetRaw = 1ull << 2;
inline constexpr std::uint64_t kCapNetAdmin = 1ull << 3;
inline constexpr std::uint64_t kCapMount = 1ull << 4;
inline constexpr std::uint64_t kCapModule = 1ull << 5;
inline constexpr std::uint64_t kCapKernelLog = 1ull << 6;
inline constexpr std::uint64_t kCapPtrace = 1ull << 7;
inline constexpr std::uint64_t kCapSetIdentity = 1ull << 8;
inline constexpr std::uint64_t kCapRelaxLabel = 1ull << 9;
inline constexpr std::uint64_t kCapAuditRead = 1ull << 10;
inline constexpr std::uint64_t kCapDeviceNode = 1ull << 11;
inline constexpr std::uint64_t kCapFull = 0xFFFFFFFFull;

inline constexpr std::uint32_t kLeaseFlagOneShot = 1u << 0;
inline constexpr std::uint32_t kLeaseFlagInherit = 1u << 1;
inline constexpr std::uint32_t kLeaseFlagNoAudit = 1u << 2;

struct Request {
    std::uint32_t magic;
    std::uint32_t version;
    std::uint32_t op;
    std::uint32_t payloadLength;
    std::uint64_t arg0;
    std::uint64_t arg1;
    std::uint64_t arg2;
    std::uint64_t arg3;
    std::uint8_t token[kTokenSize];
    std::uint8_t payload[kPayloadSize];
};

struct Response {
    std::int32_t status;
    std::uint32_t payloadLength;
    std::uint64_t value;
    std::uint8_t payload[kPayloadSize];
};

struct Exchange {
    Request request;
    Response response;
};

struct LeaseRecord {
    std::uint64_t leaseId;
    std::uint64_t capabilities;
    std::uint64_t expirySeconds;
    std::uint64_t cookie;
    std::uint32_t ownerPid;
    std::uint32_t ownerUid;
    std::uint32_t flags;
    std::uint32_t reserved;
};

inline constexpr std::size_t kLeaseRecordSize = 48;

struct AuditRecord {
    std::uint64_t timestampSeconds;
    std::uint64_t value;
    std::uint32_t kind;
    std::uint32_t actorUid;
    std::uint32_t actorPid;
    std::uint32_t detail;
};

inline constexpr std::size_t kAuditRecordSize = 32;

enum class AuditKind : std::uint32_t {
    SessionOpened = 1,
    SessionClosed = 2,
    LeaseGranted = 3,
    LeaseReleased = 4,
    LeaseExpired = 5,
    CapabilityUsed = 6,
    CapabilityRefused = 7,
    ExecCompleted = 8,
    PairingChanged = 9,
};

struct InfoPayload {
    std::uint32_t kernelPatchVersion;
    std::uint32_t kernelVersion;
    std::uint32_t protocolVersion;
    std::uint32_t leaseSlots;
    std::uint64_t capabilityMask;
    std::uint64_t bootSeconds;
};

inline constexpr std::size_t kInfoPayloadSize = 32;

struct ChallengePayload {
    std::uint8_t nonce[kNonceSize];
    std::uint64_t issuedSeconds;
    std::uint32_t windowSeconds;
    std::uint32_t reserved;
};

inline constexpr std::size_t kChallengePayloadSize = 48;

struct AuthenticatePayload {
    std::uint8_t proof[kNonceSize];
    std::uint8_t clientNonce[kNonceSize];
};

inline constexpr std::size_t kAuthenticatePayloadSize = 64;

struct PairPayload {
    std::uint8_t verifier[32];
    std::uint8_t salt[16];
};

inline constexpr std::size_t kPairPayloadSize = 48;

}