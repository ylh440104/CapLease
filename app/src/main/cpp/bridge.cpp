#include <jni.h>

#include <cstring>
#include <cstdint>

#include "hmac.h"
#include "protocol.h"
#include "sha256.h"
#include "supercall.h"
#include "types.h"

namespace {

using caplease::Exchange;
using caplease::Op;
using caplease::Request;
using caplease::Response;
using caplease::Status;
using caplease::Supercall;

using caplease::kAuthenticatePayloadSize;
using caplease::kChallengePayloadSize;
using caplease::kInfoPayloadSize;
using caplease::kLeaseRecordSize;
using caplease::kAuditRecordSize;
using caplease::kNonceSize;
using caplease::kPairPayloadSize;
using caplease::kProtocolMagic;
using caplease::kProtocolVersion;
using caplease::kTokenSize;

constexpr jint kOk = 0;

std::uint8_t g_sessionToken[kTokenSize] = {};
bool g_sessionActive = false;

struct RawLeaseRecord {
    std::uint64_t leaseId;
    std::uint64_t capabilities;
    std::uint64_t expirySeconds;
    std::uint64_t cookie;
    std::uint32_t ownerPid;
    std::uint32_t ownerUid;
    std::uint32_t flags;
    std::uint32_t reserved;
};

static_assert(sizeof(RawLeaseRecord) == kLeaseRecordSize, "lease record layout mismatch");

struct RawAuditRecord {
    std::uint64_t timestampSeconds;
    std::uint64_t value;
    std::uint32_t kind;
    std::uint32_t actorUid;
    std::uint32_t actorPid;
    std::uint32_t detail;
};

static_assert(sizeof(RawAuditRecord) == kAuditRecordSize, "audit record layout mismatch");

void prepare(Request& request, Op op) noexcept
{
    std::memset(&request, 0, sizeof(request));
    request.magic = kProtocolMagic;
    request.version = kProtocolVersion;
    request.op = static_cast<std::uint32_t>(op);
    std::memcpy(request.token, g_sessionToken, kTokenSize);
}

void attachPayload(Request& request, const void* payload, std::size_t length) noexcept
{
    if (payload == nullptr || length == 0 || length > caplease::kPayloadSize) {
        return;
    }

    std::memcpy(request.payload, payload, length);
    request.payloadLength = static_cast<std::uint32_t>(length);
}

jint submit(const Request& request, Response& response) noexcept
{
    Exchange exchange{};
    exchange.request = request;

    const long result = Supercall::exchange(&exchange);

    response = exchange.response;

    if (result < 0) {
        return static_cast<jint>(Status::NotReady);
    }

    return static_cast<jint>(response.status);
}

bool readBytes(JNIEnv* env, jbyteArray source, void* destination, std::size_t expected) noexcept
{
    if (env == nullptr || source == nullptr || destination == nullptr) {
        return false;
    }

    const jsize length = env->GetArrayLength(source);
    if (length < 0 || static_cast<std::size_t>(length) != expected) {
        return false;
    }

    env->GetByteArrayRegion(
        source,
        0,
        static_cast<jsize>(expected),
        static_cast<jbyte*>(destination));

    return env->ExceptionCheck() == JNI_FALSE;
}

jstring makeString(JNIEnv* env, const char* text) noexcept
{
    return env->NewStringUTF(text);
}

}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeProbe(JNIEnv*, jclass) noexcept
{
    if (!Supercall::available()) {
        return static_cast<jint>(Status::NotReady);
    }

    Request request;
    prepare(request, Op::Hello);

    Response response;
    return submit(request, response);
}

JNIEXPORT jlongArray JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeInfo(JNIEnv* env, jclass) noexcept
{
    Request request;
    prepare(request, Op::Info);

    Response response;
    const jint status = submit(request, response);

    auto* result = env->NewLongArray(7);
    if (result == nullptr) {
        return nullptr;
    }

    jlong values[7] = {
        static_cast<jlong>(status),
        0, 0, 0, 0, 0, 0
    };

    if (status == kOk && response.payloadLength >= kInfoPayloadSize) {
        caplease::InfoPayload info{};
        std::memcpy(&info, response.payload, kInfoPayloadSize);

        values[1] = static_cast<jlong>(info.kernelPatchVersion);
        values[2] = static_cast<jlong>(info.kernelVersion);
        values[3] = static_cast<jlong>(info.protocolVersion);
        values[4] = static_cast<jlong>(info.leaseSlots);
        values[5] = static_cast<jlong>(info.capabilityMask);
        values[6] = static_cast<jlong>(info.bootSeconds);
    }

    env->SetLongArrayRegion(result, 0, 7, values);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativePair(
    JNIEnv* env,
    jclass,
    jbyteArray password,
    jbyteArray salt) noexcept
{
    if (password == nullptr || salt == nullptr) {
        return static_cast<jint>(Status::InvalidArgument);
    }

    const jsize passwordLength = env->GetArrayLength(password);
    const jsize saltLength = env->GetArrayLength(salt);

    if (passwordLength <= 0 || saltLength <= 0 || saltLength > 64) {
        return static_cast<jint>(Status::InvalidArgument);
    }

    std::uint8_t passwordBuffer[256];
    std::uint8_t saltBuffer[64];

    if (static_cast<std::size_t>(passwordLength) > sizeof(passwordBuffer)) {
        return static_cast<jint>(Status::InvalidArgument);
    }

    if (!readBytes(env, password, passwordBuffer, static_cast<std::size_t>(passwordLength))) {
        return static_cast<jint>(Status::InvalidArgument);
    }

    if (!readBytes(env, salt, saltBuffer, static_cast<std::size_t>(saltLength))) {
        caplease::crypto::wipe(passwordBuffer, sizeof(passwordBuffer));
        return static_cast<jint>(Status::InvalidArgument);
    }

    std::uint8_t secret[32];
    caplease::crypto::pbkdf2Sha256(
        passwordBuffer,
        static_cast<std::size_t>(passwordLength),
        saltBuffer,
        static_cast<std::size_t>(saltLength),
        caplease::kPbkdf2Iterations,
        secret,
        sizeof(secret));

    caplease::PairPayload payload{};
    std::memcpy(payload.verifier, secret, sizeof(secret));
    std::memcpy(payload.salt, saltBuffer, static_cast<std::size_t>(saltLength));

    Request request;
    prepare(request, Op::Pair);
    attachPayload(request, &payload, kPairPayloadSize);

    Response response;
    const jint status = submit(request, response);

    caplease::crypto::wipe(passwordBuffer, sizeof(passwordBuffer));
    caplease::crypto::wipe(secret, sizeof(secret));

    return status;
}

JNIEXPORT jint JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeUnpair(JNIEnv*, jclass) noexcept
{
    Request request;
    prepare(request, Op::Unpair);

    Response response;
    const jint status = submit(request, response);

    caplease::crypto::wipe(g_sessionToken, kTokenSize);
    g_sessionActive = false;

    return status;
}

JNIEXPORT jlongArray JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeOpenSession(
    JNIEnv* env,
    jclass,
    jbyteArray password,
    jbyteArray salt) noexcept
{
    auto* result = env->NewLongArray(4);
    if (result == nullptr) {
        return nullptr;
    }

    jlong values[4] = { static_cast<jlong>(Status::InvalidArgument), 0, 0, 0 };

    if (password == nullptr || salt == nullptr) {
        env->SetLongArrayRegion(result, 0, 4, values);
        return result;
    }

    const jsize passwordLength = env->GetArrayLength(password);
    const jsize saltLength = env->GetArrayLength(salt);

    if (passwordLength <= 0 || saltLength <= 0 || saltLength > 64) {
        env->SetLongArrayRegion(result, 0, 4, values);
        return result;
    }

    std::uint8_t passwordBuffer[256];
    std::uint8_t saltBuffer[64];

    if (static_cast<std::size_t>(passwordLength) > sizeof(passwordBuffer)
        || !readBytes(env, password, passwordBuffer, static_cast<std::size_t>(passwordLength))
        || !readBytes(env, salt, saltBuffer, static_cast<std::size_t>(saltLength))) {
        caplease::crypto::wipe(passwordBuffer, sizeof(passwordBuffer));
        env->SetLongArrayRegion(result, 0, 4, values);
        return result;
    }

    Request challengeRequest;
    prepare(challengeRequest, Op::Challenge);

    Response challengeResponse;
    jint status = submit(challengeRequest, challengeResponse);

    if (status != kOk || challengeResponse.payloadLength < kChallengePayloadSize) {
        caplease::crypto::wipe(passwordBuffer, sizeof(passwordBuffer));
        values[0] = static_cast<jlong>(status == kOk ? static_cast<jint>(Status::Internal) : status);
        env->SetLongArrayRegion(result, 0, 4, values);
        return result;
    }

    caplease::ChallengePayload challenge{};
    std::memcpy(&challenge, challengeResponse.payload, kChallengePayloadSize);

    std::uint8_t secret[32];
    caplease::crypto::pbkdf2Sha256(
        passwordBuffer,
        static_cast<std::size_t>(passwordLength),
        saltBuffer,
        static_cast<std::size_t>(saltLength),
        caplease::kPbkdf2Iterations,
        secret,
        sizeof(secret));

    std::uint8_t clientNonce[kNonceSize];
    for (std::size_t i = 0; i < kNonceSize; ++i) {
        clientNonce[i] = static_cast<std::uint8_t>(challenge.nonce[i] ^ 0x5au);
    }

    std::uint8_t transcript[kNonceSize * 2];
    std::memcpy(transcript, challenge.nonce, kNonceSize);
    std::memcpy(transcript + kNonceSize, clientNonce, kNonceSize);

    caplease::AuthenticatePayload authenticate{};
    caplease::crypto::hmacSha256(
        secret,
        sizeof(secret),
        transcript,
        sizeof(transcript),
        authenticate.proof);
    std::memcpy(authenticate.clientNonce, clientNonce, kNonceSize);

    Request authenticateRequest;
    prepare(authenticateRequest, Op::Authenticate);
    authenticateRequest.arg0 = challenge.issuedSeconds;
    attachPayload(authenticateRequest, &authenticate, kAuthenticatePayloadSize);

    Response authenticateResponse;
    status = submit(authenticateRequest, authenticateResponse);

    if (status == kOk && authenticateResponse.payloadLength >= kTokenSize) {
        std::memcpy(g_sessionToken, authenticateResponse.payload, kTokenSize);
        g_sessionActive = true;

        values[0] = kOk;
        values[1] = static_cast<jlong>(authenticateResponse.value);
        values[2] = static_cast<jlong>(challenge.issuedSeconds);
        values[3] = static_cast<jlong>(challenge.windowSeconds);
    } else {
        caplease::crypto::wipe(g_sessionToken, kTokenSize);
        g_sessionActive = false;
        values[0] = static_cast<jlong>(status);
    }

    caplease::crypto::wipe(passwordBuffer, sizeof(passwordBuffer));
    caplease::crypto::wipe(secret, sizeof(secret));
    caplease::crypto::wipe(transcript, sizeof(transcript));

    env->SetLongArrayRegion(result, 0, 4, values);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeCloseSession(JNIEnv*, jclass) noexcept
{
    if (!g_sessionActive) {
        return kOk;
    }

    Request request;
    prepare(request, Op::SessionDrop);

    Response response;
    const jint status = submit(request, response);

    caplease::crypto::wipe(g_sessionToken, kTokenSize);
    g_sessionActive = false;

    return status;
}

JNIEXPORT jlongArray JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeAcquireLease(
    JNIEnv* env,
    jclass,
    jlong capabilities,
    jlong seconds,
    jint flags,
    jstring packageName) noexcept
{
    auto* result = env->NewLongArray(3);
    if (result == nullptr) {
        return nullptr;
    }

    jlong values[3] = { static_cast<jlong>(Status::Unauthenticated), 0, 0 };

    if (!g_sessionActive) {
        env->SetLongArrayRegion(result, 0, 3, values);
        return result;
    }

    if (capabilities == 0 || seconds <= 0) {
        values[0] = static_cast<jlong>(Status::InvalidArgument);
        env->SetLongArrayRegion(result, 0, 3, values);
        return result;
    }

    std::uint8_t packageBuffer[caplease::kPackageNameMax];
    std::memset(packageBuffer, 0, sizeof(packageBuffer));

    if (packageName != nullptr) {
        const char* text = env->GetStringUTFChars(packageName, nullptr);
        if (text != nullptr) {
            std::size_t length = std::strlen(text);
            if (length >= sizeof(packageBuffer)) {
                length = sizeof(packageBuffer) - 1;
            }
            std::memcpy(packageBuffer, text, length);
            env->ReleaseStringUTFChars(packageName, text);
        }
    }

    Request request;
    prepare(request, Op::LeaseAcquire);
    request.arg0 = static_cast<std::uint64_t>(capabilities);
    request.arg1 = static_cast<std::uint64_t>(seconds);
    request.arg2 = static_cast<std::uint64_t>(flags);
    request.arg3 = 0;

    std::memcpy(
        request.payload,
        packageBuffer,
        sizeof(packageBuffer));
    request.payloadLength = static_cast<std::uint32_t>(sizeof(packageBuffer));

    Response response;
    const jint status = submit(request, response);

    values[0] = static_cast<jlong>(status);
    if (status == kOk) {
        values[1] = static_cast<jlong>(response.value);
        values[2] = static_cast<jlong>(response.payloadLength);
    }

    env->SetLongArrayRegion(result, 0, 3, values);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeReleaseLease(
    JNIEnv*,
    jclass,
    jlong leaseId) noexcept
{
    if (!g_sessionActive) {
        return static_cast<jint>(Status::Unauthenticated);
    }

    Request request;
    prepare(request, Op::LeaseRelease);
    request.arg0 = static_cast<std::uint64_t>(leaseId);

    Response response;
    return submit(request, response);
}

JNIEXPORT jint JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeExtendLease(
    JNIEnv*,
    jclass,
    jlong leaseId,
    jlong seconds) noexcept
{
    if (!g_sessionActive) {
        return static_cast<jint>(Status::Unauthenticated);
    }

    Request request;
    prepare(request, Op::LeaseExtend);
    request.arg0 = static_cast<std::uint64_t>(leaseId);
    request.arg1 = static_cast<std::uint64_t>(seconds);

    Response response;
    return submit(request, response);
}

JNIEXPORT jint JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeRevokeAll(JNIEnv*, jclass) noexcept
{
    if (!g_sessionActive) {
        return static_cast<jint>(Status::Unauthenticated);
    }

    Request request;
    prepare(request, Op::LeaseRevokeAll);

    Response response;
    return submit(request, response);
}

JNIEXPORT jlongArray JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeListLeases(JNIEnv* env, jclass) noexcept
{
    Request request;
    prepare(request, Op::LeaseQuery);

    Response response;
    const jint status = submit(request, response);

    const std::size_t maximum =
        (response.payloadLength / kLeaseRecordSize) > caplease::kMaxLeaseSlots
            ? caplease::kMaxLeaseSlots
            : (response.payloadLength / kLeaseRecordSize);

    const std::size_t count = (status == kOk) ? maximum : 0;

    auto* result = env->NewLongArray(static_cast<jsize>(1 + count * 6));
    if (result == nullptr) {
        return nullptr;
    }

    jlong* values = new jlong[1 + count * 6];
    values[0] = static_cast<jlong>(status);

    for (std::size_t index = 0; index < count; ++index) {
        RawLeaseRecord record{};
        std::memcpy(&record, response.payload + index * kLeaseRecordSize, kLeaseRecordSize);

        const std::size_t base = 1 + index * 6;
        values[base + 0] = static_cast<jlong>(record.leaseId);
        values[base + 1] = static_cast<jlong>(record.capabilities);
        values[base + 2] = static_cast<jlong>(record.expirySeconds);
        values[base + 3] = static_cast<jlong>(record.ownerUid);
        values[base + 4] = static_cast<jlong>(record.ownerPid);
        values[base + 5] = static_cast<jlong>(record.flags);
    }

    env->SetLongArrayRegion(result, 0, static_cast<jsize>(1 + count * 6), values);
    delete[] values;

    return result;
}

JNIEXPORT jint JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeExec(
    JNIEnv* env,
    jclass,
    jstring command,
    jlong capabilities,
    jint timeoutMs) noexcept
{
    if (!g_sessionActive) {
        return static_cast<jint>(Status::Unauthenticated);
    }

    if (command == nullptr || capabilities == 0 || timeoutMs <= 0) {
        return static_cast<jint>(Status::InvalidArgument);
    }

    const char* text = env->GetStringUTFChars(command, nullptr);
    if (text == nullptr) {
        return static_cast<jint>(Status::InvalidArgument);
    }

    const std::size_t length = std::strlen(text);
    if (length == 0 || length >= caplease::kPayloadSize) {
        env->ReleaseStringUTFChars(command, text);
        return static_cast<jint>(Status::InvalidArgument);
    }

    Request request;
    prepare(request, Op::Exec);
    request.arg0 = static_cast<std::uint64_t>(capabilities);
    request.arg1 = static_cast<std::uint64_t>(timeoutMs);

    std::memcpy(request.payload, text, length);
    request.payloadLength = static_cast<std::uint32_t>(length);
    env->ReleaseStringUTFChars(command, text);

    Response response;
    return submit(request, response);
}

JNIEXPORT jlongArray JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeReadAudit(
    JNIEnv* env,
    jclass,
    jint limit) noexcept
{
    auto* result = env->NewLongArray(1);
    if (result == nullptr) {
        return nullptr;
    }

    if (!g_sessionActive || limit <= 0) {
        jlong values[1] = { static_cast<jlong>(Status::Unauthenticated) };
        env->SetLongArrayRegion(result, 0, 1, values);
        return result;
    }

    Request request;
    prepare(request, Op::AuditRead);
    request.arg0 = static_cast<std::uint64_t>(limit);

    Response response;
    const jint status = submit(request, response);

    std::size_t count = response.payloadLength / kAuditRecordSize;
    if (count > static_cast<std::size_t>(limit)) {
        count = static_cast<std::size_t>(limit);
    }

    if (status != kOk) {
        count = 0;
    }

    env->DeleteLocalRef(result);

    auto* expanded = env->NewLongArray(static_cast<jsize>(1 + count * 6));
    if (expanded == nullptr) {
        return nullptr;
    }

    jlong* values = new jlong[1 + count * 6];
    values[0] = static_cast<jlong>(status);

    for (std::size_t index = 0; index < count; ++index) {
        RawAuditRecord record{};
        std::memcpy(&record, response.payload + index * kAuditRecordSize, kAuditRecordSize);

        const std::size_t base = 1 + index * 6;
        values[base + 0] = static_cast<jlong>(record.timestampSeconds);
        values[base + 1] = static_cast<jlong>(record.kind);
        values[base + 2] = static_cast<jlong>(record.actorUid);
        values[base + 3] = static_cast<jlong>(record.actorPid);
        values[base + 4] = static_cast<jlong>(record.detail);
        values[base + 5] = static_cast<jlong>(record.value);
    }

    env->SetLongArrayRegion(expanded, 0, static_cast<jsize>(1 + count * 6), values);
    delete[] values;

    return expanded;
}

JNIEXPORT jint JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeClearAudit(JNIEnv*, jclass) noexcept
{
    if (!g_sessionActive) {
        return static_cast<jint>(Status::Unauthenticated);
    }

    Request request;
    prepare(request, Op::AuditClear);

    Response response;
    return submit(request, response);
}

JNIEXPORT jboolean JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeSessionActive(JNIEnv*, jclass) noexcept
{
    return g_sessionActive ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_caplease_manager_kernel_NativeBridge_nativeStatusName(JNIEnv* env, jclass, jint status) noexcept
{
    switch (static_cast<Status>(status)) {
    case Status::Ok: return makeString(env, "OK");
    case Status::NotReady: return makeString(env, "NOT_READY");
    case Status::BadMagic: return makeString(env, "BAD_MAGIC");
    case Status::BadVersion: return makeString(env, "BAD_VERSION");
    case Status::UnknownOp: return makeString(env, "UNKNOWN_OP");
    case Status::InvalidArgument: return makeString(env, "INVALID_ARGUMENT");
    case Status::Unauthenticated: return makeString(env, "UNAUTHENTICATED");
    case Status::BadCredential: return makeString(env, "BAD_CREDENTIAL");
    case Status::ChallengeMissing: return makeString(env, "CHALLENGE_MISSING");
    case Status::ChallengeExpired: return makeString(env, "CHALLENGE_EXPIRED");
    case Status::AlreadyPaired: return makeString(env, "ALREADY_PAIRED");
    case Status::NotPaired: return makeString(env, "NOT_PAIRED");
    case Status::CapabilityDenied: return makeString(env, "CAPABILITY_DENIED");
    case Status::LeaseMissing: return makeString(env, "LEASE_MISSING");
    case Status::LeaseExpired: return makeString(env, "LEASE_EXPIRED");
    case Status::LeaseLimitReached: return makeString(env, "LEASE_LIMIT_REACHED");
    case Status::TargetDenied: return makeString(env, "TARGET_DENIED");
    case Status::NotSupported: return makeString(env, "NOT_SUPPORTED");
    case Status::BufferTooSmall: return makeString(env, "BUFFER_TOO_SMALL");
    case Status::Internal: return makeString(env, "INTERNAL");
    default: return makeString(env, "UNKNOWN");
    }
}

}
