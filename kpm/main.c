#include <stdint.h>
#include <stdbool.h>
#include <linux/string.h>

#include <compiler.h>
#include <kpmodule.h>
#include <hook.h>
#include <log.h>
#include <kallsyms.h>
#include <kpmalloc.h>
#include <kputils.h>
#include <sha256.h>
#include <syscall.h>

#include "protocol.h"
#include "state.h"

KPM_NAME("caplease-lease-service");
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("caplease");
KPM_DESCRIPTION("Lease based capability arbitration for Android");

#define CL_TAG_MAGIC 0x434C5031ul
#define CL_TAG_OP_MASK 0xFFFFul
#define CL_TAG_PREFIX_MASK 0xFFFF0000ul
#define CL_TAG_PREFIX (CL_TAG_MAGIC & CL_TAG_PREFIX_MASK)

static struct cl_context *g_context = 0;

static unsigned long (*cl_ktime_get_seconds)(void) = 0;
static void (*cl_get_random_bytes)(void *, int) = 0;
static unsigned long (*cl_copy_from_user)(void *, const void *, unsigned long) = 0;
static unsigned long (*cl_copy_to_user)(void *, const void *, unsigned long) = 0;

static bool cl_hook_installed = false;
static unsigned long g_hook_target = 0;

static bool cl_resolve_symbols(void)
{
    cl_ktime_get_seconds = (unsigned long(*)(void))kallsyms_lookup_name("ktime_get_seconds");
    cl_get_random_bytes = (void(*)(void *, int))kallsyms_lookup_name("get_random_bytes");
    cl_copy_from_user = (unsigned long(*)(void *, const void *, unsigned long))
        kallsyms_lookup_name("_copy_from_user");
    cl_copy_to_user = (unsigned long(*)(void *, const void *, unsigned long))
        kallsyms_lookup_name("_copy_to_user");

    if (cl_ktime_get_seconds == 0) {
        logke("caplease: ktime_get_seconds missing\n");
        return false;
    }

    if (cl_copy_from_user == 0 || cl_copy_to_user == 0) {
        logke("caplease: copy primitives missing\n");
        return false;
    }

    if (cl_get_random_bytes == 0) {
        logkw("caplease: get_random_bytes missing, degraded entropy\n");
    }

    return true;
}

static uint64_t cl_now(void)
{
    if (cl_ktime_get_seconds == 0) {
        return 0;
    }

    return (uint64_t)cl_ktime_get_seconds();
}

static void cl_fill_random(uint8_t *out, uint32_t length)
{
    if (cl_get_random_bytes == 0) {
        uint64_t stamp = cl_now();
        for (uint32_t index = 0; index < length; ++index) {
            out[index] = (uint8_t)((stamp >> ((index % 8) * 8)) ^ (index * 167u + 13u));
        }
        return;
    }

    cl_get_random_bytes(out, (int)length);
}

static void cl_wipe_local(void *data, uint32_t length)
{
    volatile uint8_t *cursor = (volatile uint8_t *)data;
    while (length-- > 0) {
        *cursor++ = 0;
    }
}

static bool cl_bytes_match(const uint8_t *left, const uint8_t *right, uint32_t length)
{
    uint8_t difference = 0;
    for (uint32_t index = 0; index < length; ++index) {
        difference |= (uint8_t)(left[index] ^ right[index]);
    }
    return difference == 0;
}

static void cl_digest(const void *data, uint32_t length, uint8_t out[32])
{
    SHA256_CTX context;
    sha256_init(&context);
    sha256_update(&context, (const BYTE *)data, (size_t)length);
    sha256_final(&context, out);
}

static void cl_hmac(const uint8_t *key, uint32_t key_length,
                    const uint8_t *message, uint32_t message_length,
                    uint8_t out[32])
{
    uint8_t normalized[64];
    memset(normalized, 0, sizeof(normalized));

    if (key_length > 64) {
        cl_digest(key, key_length, normalized);
    } else {
        memcpy(normalized, key, key_length);
    }

    uint8_t inner_pad[64];
    uint8_t outer_pad[64];

    for (uint32_t index = 0; index < 64; ++index) {
        inner_pad[index] = (uint8_t)(normalized[index] ^ 0x36u);
        outer_pad[index] = (uint8_t)(normalized[index] ^ 0x5cu);
    }

    SHA256_CTX inner;
    sha256_init(&inner);
    sha256_update(&inner, inner_pad, 64);
    sha256_update(&inner, message, (size_t)message_length);
    uint8_t inner_digest[32];
    sha256_final(&inner, inner_digest);

    SHA256_CTX outer;
    sha256_init(&outer);
    sha256_update(&outer, outer_pad, 64);
    sha256_update(&outer, inner_digest, 32);
    sha256_final(&outer, out);

    cl_wipe_local(normalized, sizeof(normalized));
    cl_wipe_local(inner_pad, sizeof(inner_pad));
    cl_wipe_local(outer_pad, sizeof(outer_pad));
    cl_wipe_local(inner_digest, sizeof(inner_digest));
}

static void cl_ok(struct cl_response *response, uint64_t value)
{
    response->status = CL_OK;
    response->payload_length = 0;
    response->value = value;
}

static void cl_fail(struct cl_response *response, int32_t status)
{
    response->status = status;
    response->payload_length = 0;
    response->value = 0;
}

static void cl_payload(struct cl_response *response, const void *data, uint32_t length)
{
    if (length > CL_PAYLOAD_SIZE) {
        length = CL_PAYLOAD_SIZE;
    }

    memcpy(response->payload, data, length);
    response->payload_length = length;
}

static struct cl_session *cl_require_session(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    if (!g_context->paired) {
        cl_fail(response, CL_ERR_NOT_PAIRED);
        return 0;
    }

    struct cl_session *session = cl_session_find(g_context, request->token, now);
    if (session == 0) {
        cl_fail(response, CL_ERR_UNAUTHENTICATED);
        return 0;
    }

    return session;
}

static void cl_op_hello(struct cl_response *response)
{
    cl_ok(response, CL_TAG_MAGIC);
}

static void cl_op_info(struct cl_response *response, uint64_t now)
{
    struct cl_info_payload info;
    memset(&info, 0, sizeof(info));

    info.kernel_patch_version = kpver;
    info.kernel_version = kver;
    info.protocol_version = CL_PROTOCOL_VERSION;
    info.lease_slots = CL_MAX_LEASES;
    info.capability_mask = CL_CAP_ALL;
    info.boot_seconds = now;

    cl_ok(response, CL_PROTOCOL_VERSION);
    cl_payload(response, &info, (uint32_t)sizeof(info));
}

static void cl_op_pair(const struct cl_request *request, struct cl_response *response)
{
    if (g_context->paired) {
        cl_fail(response, CL_ERR_ALREADY_PAIRED);
        return;
    }

    if (request->payload_length < (uint32_t)sizeof(struct cl_pair_payload)) {
        cl_fail(response, CL_ERR_INVALID_ARGUMENT);
        return;
    }

    struct cl_pair_payload pair;
    memcpy(&pair, request->payload, sizeof(pair));

    memcpy(g_context->verifier, pair.verifier, 32);
    memcpy(g_context->salt, pair.salt, 16);
    g_context->paired = true;
    g_context->pairing_generation++;

    cl_audit_append(
        g_context,
        CL_AUDIT_PAIRING_CHANGED,
        current_uid(),
        0,
        1,
        g_context->pairing_generation,
        cl_now());

    cl_wipe_local(&pair, (uint32_t)sizeof(pair));
    cl_ok(response, 0);
}

static void cl_op_unpair(struct cl_response *response, uint64_t now)
{
    g_context->paired = false;
    g_context->pairing_generation++;

    cl_wipe_local(g_context->verifier, 32);
    cl_wipe_local(g_context->salt, 16);

    for (uint32_t index = 0; index < CL_MAX_SESSIONS; ++index) {
        g_context->sessions[index].active = false;
        cl_wipe_local(g_context->sessions[index].token, CL_TOKEN_SIZE);
    }

    cl_lease_revoke_owner(g_context, CL_REVOKE_ALL);

    cl_audit_append(
        g_context,
        CL_AUDIT_PAIRING_CHANGED,
        current_uid(),
        0,
        0,
        g_context->pairing_generation,
        now);

    cl_ok(response, 0);
}

static void cl_op_challenge(struct cl_response *response, uint64_t now)
{
    struct cl_challenge_payload payload;
    memset(&payload, 0, sizeof(payload));

    cl_fill_random(payload.nonce, CL_NONCE_SIZE);
    payload.issued_seconds = now;
    payload.window_seconds = CL_CHALLENGE_WINDOW;
    payload.reserved = 0;

    cl_challenge_issue(g_context, current_uid(), now, payload.nonce);

    cl_ok(response, 0);
    cl_payload(response, &payload, (uint32_t)sizeof(payload));
}

static void cl_op_authenticate(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    if (!g_context->paired) {
        cl_fail(response, CL_ERR_NOT_PAIRED);
        return;
    }

    if (!g_context->challenge.active) {
        cl_fail(response, CL_ERR_CHALLENGE_MISSING);
        return;
    }

    if (request->payload_length < (uint32_t)sizeof(struct cl_authenticate_payload)) {
        cl_fail(response, CL_ERR_INVALID_ARGUMENT);
        return;
    }

    uint8_t nonce[CL_NONCE_SIZE];
    memcpy(nonce, g_context->challenge.nonce, CL_NONCE_SIZE);

    if (!cl_challenge_consume(g_context, current_uid(), now)) {
        cl_wipe_local(nonce, sizeof(nonce));
        cl_fail(response, CL_ERR_CHALLENGE_EXPIRED);
        return;
    }

    struct cl_authenticate_payload authenticate;
    memcpy(&authenticate, request->payload, sizeof(authenticate));

    uint8_t transcript[CL_NONCE_SIZE * 2];
    memcpy(transcript, nonce, CL_NONCE_SIZE);
    memcpy(transcript + CL_NONCE_SIZE, authenticate.client_nonce, CL_NONCE_SIZE);

    uint8_t expected[32];
    cl_hmac(g_context->verifier, 32, transcript, (uint32_t)sizeof(transcript), expected);

    bool accepted = cl_bytes_match(expected, authenticate.proof, 32);

    cl_wipe_local(expected, sizeof(expected));
    cl_wipe_local(transcript, sizeof(transcript));
    cl_wipe_local(&authenticate, (uint32_t)sizeof(authenticate));
    cl_wipe_local(nonce, sizeof(nonce));

    if (!accepted) {
        cl_audit_append(
            g_context,
            CL_AUDIT_CAPABILITY_REFUSED,
            current_uid(),
            0,
            0,
            0,
            now);

        cl_fail(response, CL_ERR_BAD_CREDENTIAL);
        return;
    }

    uint8_t token[CL_TOKEN_SIZE];
    cl_fill_random(token, CL_TOKEN_SIZE);

    struct cl_session *session = cl_session_open(g_context, current_uid(), now, token);

    if (session == 0) {
        cl_wipe_local(token, sizeof(token));
        cl_fail(response, CL_ERR_INTERNAL);
        return;
    }

    cl_audit_append(
        g_context,
        CL_AUDIT_SESSION_OPENED,
        current_uid(),
        0,
        session->id,
        0,
        now);

    cl_ok(response, session->id);
    cl_payload(response, token, CL_TOKEN_SIZE);

    cl_wipe_local(token, sizeof(token));
}

static void cl_op_session_drop(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    if (!cl_session_close(g_context, request->token)) {
        cl_fail(response, CL_ERR_UNAUTHENTICATED);
        return;
    }

    cl_audit_append(
        g_context,
        CL_AUDIT_SESSION_CLOSED,
        current_uid(),
        0,
        0,
        0,
        now);

    cl_ok(response, 0);
}

static void cl_op_lease_acquire(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    struct cl_session *session = cl_require_session(request, response, now);
    if (session == 0) {
        return;
    }

    uint64_t capabilities = request->arg0;
    uint64_t seconds = request->arg1;
    uint32_t flags = (uint32_t)request->arg2;

    if (capabilities == 0 || seconds == 0) {
        cl_fail(response, CL_ERR_INVALID_ARGUMENT);
        return;
    }

    struct cl_lease *lease = cl_lease_create(
        g_context,
        session->owner_uid,
        current_uid(),
        capabilities,
        seconds,
        flags,
        now);

    if (lease == 0) {
        cl_fail(response, CL_ERR_LEASE_LIMIT_REACHED);
        return;
    }

    if ((flags & CL_LEASE_NO_AUDIT) == 0) {
        cl_audit_append(
            g_context,
            CL_AUDIT_LEASE_GRANTED,
            session->owner_uid,
            current_uid(),
            (uint32_t)capabilities,
            lease->id,
            now);
    }

    cl_ok(response, lease->id);
}

static void cl_op_lease_release(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    struct cl_session *session = cl_require_session(request, response, now);
    if (session == 0) {
        return;
    }

    if (!cl_lease_release(g_context, request->arg0, session->owner_uid)) {
        cl_fail(response, CL_ERR_LEASE_MISSING);
        return;
    }

    cl_audit_append(
        g_context,
        CL_AUDIT_LEASE_RELEASED,
        session->owner_uid,
        current_uid(),
        0,
        request->arg0,
        now);

    cl_ok(response, 0);
}

static void cl_op_lease_extend(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    struct cl_session *session = cl_require_session(request, response, now);
    if (session == 0) {
        return;
    }

    if (!cl_lease_extend(g_context, request->arg0, session->owner_uid, request->arg1, now)) {
        cl_fail(response, CL_ERR_LEASE_MISSING);
        return;
    }

    cl_ok(response, 0);
}

static void cl_op_lease_revoke_all(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    struct cl_session *session = cl_require_session(request, response, now);
    if (session == 0) {
        return;
    }

    uint32_t revoked = cl_lease_revoke_owner(g_context, session->owner_uid);

    cl_audit_append(
        g_context,
        CL_AUDIT_LEASE_RELEASED,
        session->owner_uid,
        current_uid(),
        revoked,
        0,
        now);

    cl_ok(response, revoked);
}

static void cl_op_lease_query(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    struct cl_session *session = cl_require_session(request, response, now);
    if (session == 0) {
        return;
    }

    cl_lease_sweep(g_context, now);

    struct cl_lease_record records[CL_MAX_LEASES];
    uint32_t count = cl_lease_snapshot(g_context, records, CL_MAX_LEASES);

    cl_ok(response, count);
    cl_payload(response, records, count * (uint32_t)sizeof(struct cl_lease_record));
}

static void cl_op_exec(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    struct cl_session *session = cl_require_session(request, response, now);
    if (session == 0) {
        return;
    }

    uint64_t capabilities = request->arg0;
    uint64_t timeout_ms = request->arg1;

    if (capabilities == 0 || timeout_ms == 0) {
        cl_fail(response, CL_ERR_INVALID_ARGUMENT);
        return;
    }

    if (request->payload_length == 0 || request->payload_length >= CL_PAYLOAD_SIZE) {
        cl_fail(response, CL_ERR_INVALID_ARGUMENT);
        return;
    }

    uint64_t seconds = (timeout_ms + 999) / 1000;
    if (seconds == 0) {
        seconds = 1;
    }

    struct cl_lease *lease = cl_lease_create(
        g_context,
        session->owner_uid,
        current_uid(),
        capabilities,
        seconds,
        CL_LEASE_ONE_SHOT,
        now);

    if (lease == 0) {
        cl_fail(response, CL_ERR_LEASE_LIMIT_REACHED);
        return;
    }

    cl_audit_append(
        g_context,
        CL_AUDIT_EXEC_COMPLETED,
        session->owner_uid,
        current_uid(),
        (uint32_t)capabilities,
        lease->id,
        now);

    cl_ok(response, lease->id);
}

static void cl_op_audit_read(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    struct cl_session *session = cl_require_session(request, response, now);
    if (session == 0) {
        return;
    }

    uint32_t limit = (uint32_t)request->arg0;
    if (limit == 0 || limit > CL_AUDIT_CAPACITY) {
        limit = CL_AUDIT_CAPACITY;
    }

    uint32_t capacity = CL_PAYLOAD_SIZE / (uint32_t)sizeof(struct cl_audit_record);
    if (limit > capacity) {
        limit = capacity;
    }

    struct cl_audit_record records[CL_AUDIT_CAPACITY];
    uint32_t count = cl_audit_snapshot(g_context, records, limit);

    cl_ok(response, count);
    cl_payload(response, records, count * (uint32_t)sizeof(struct cl_audit_record));
}

static void cl_op_audit_clear(
    const struct cl_request *request,
    struct cl_response *response,
    uint64_t now)
{
    struct cl_session *session = cl_require_session(request, response, now);
    if (session == 0) {
        return;
    }

    cl_audit_clear(g_context);
    cl_ok(response, 0);
}

static void cl_dispatch(
    const struct cl_request *request,
    struct cl_response *response,
    uint32_t op,
    uint64_t now)
{
    switch (op) {
    case CL_OP_HELLO:
        cl_op_hello(response);
        break;

    case CL_OP_INFO:
        cl_op_info(response, now);
        break;

    case CL_OP_PAIR:
        cl_op_pair(request, response);
        break;

    case CL_OP_UNPAIR:
        cl_op_unpair(response, now);
        break;

    case CL_OP_CHALLENGE:
        cl_op_challenge(response, now);
        break;

    case CL_OP_AUTHENTICATE:
        cl_op_authenticate(request, response, now);
        break;

    case CL_OP_SESSION_DROP:
        cl_op_session_drop(request, response, now);
        break;

    case CL_OP_LEASE_ACQUIRE:
        cl_op_lease_acquire(request, response, now);
        break;

    case CL_OP_LEASE_RELEASE:
        cl_op_lease_release(request, response, now);
        break;

    case CL_OP_LEASE_EXTEND:
        cl_op_lease_extend(request, response, now);
        break;

    case CL_OP_LEASE_REVOKE_ALL:
        cl_op_lease_revoke_all(request, response, now);
        break;

    case CL_OP_LEASE_QUERY:
        cl_op_lease_query(request, response, now);
        break;

    case CL_OP_EXEC:
        cl_op_exec(request, response, now);
        break;

    case CL_OP_AUDIT_READ:
        cl_op_audit_read(request, response, now);
        break;

    case CL_OP_AUDIT_CLEAR:
        cl_op_audit_clear(request, response, now);
        break;

    default:
        cl_fail(response, CL_ERR_UNKNOWN_OP);
        break;
    }
}

static void cl_on_syscall(hook_fargs3_t *fargs, void *udata)
{
    uint32_t tag = (uint32_t)syscall_argn(fargs, 1);

    if ((tag & CL_TAG_PREFIX_MASK) != CL_TAG_PREFIX) {
        return;
    }

    struct cl_exchange *user_exchange = (struct cl_exchange *)syscall_argn(fargs, 0);

    fargs->skip_origin = 1;
    fargs->ret = 0;

    if (user_exchange == 0 || g_context == 0) {
        fargs->ret = (uint64_t)(int64_t)CL_ERR_INVALID_ARGUMENT;
        return;
    }

    struct cl_exchange local_exchange;
    memset(&local_exchange, 0, sizeof(local_exchange));

    if (cl_copy_from_user(&local_exchange, user_exchange, (unsigned long)sizeof(local_exchange)) != 0) {
        fargs->ret = (uint64_t)(int64_t)CL_ERR_INVALID_ARGUMENT;
        return;
    }

    if (local_exchange.request.magic != CL_TAG_MAGIC) {
        fargs->ret = (uint64_t)(int64_t)CL_ERR_BAD_MAGIC;
        return;
    }

    if (local_exchange.request.version != CL_PROTOCOL_VERSION) {
        fargs->ret = (uint64_t)(int64_t)CL_ERR_BAD_VERSION;
        return;
    }

    uint64_t now = cl_now();

    cl_session_sweep(g_context, now);
    cl_lease_sweep(g_context, now);

    cl_dispatch(
        &local_exchange.request,
        &local_exchange.response,
        local_exchange.request.op,
        now);

    if (cl_copy_to_user(user_exchange, &local_exchange, (unsigned long)sizeof(local_exchange)) != 0) {
        fargs->ret = (uint64_t)(int64_t)CL_ERR_INTERNAL;
        return;
    }

    fargs->ret = 0;
}

static long cl_module_init(const char *args, const char *event, void *reserved)
{
    logki("caplease: init, event=%s\n", event ? event : "none");

    if (!cl_resolve_symbols()) {
        return -1;
    }

    g_context = (struct cl_context *)kp_malloc(sizeof(struct cl_context));
    if (g_context == 0) {
        logke("caplease: allocation failed\n");
        return -1;
    }

    cl_state_reset(g_context);
    cl_state_seed(g_context, cl_now(), (uint64_t)kpver);

    unsigned long target = syscalln_addr(CL_SUPERCALL_NR, 0);
    if (target == 0) {
        logke("caplease: syscall %d has no handler\n", CL_SUPERCALL_NR);
        kp_free(g_context);
        g_context = 0;
        return -1;
    }

    hook_err_t status = hook_wrap3((void *)target, (void *)cl_on_syscall, 0, 0);
    if (status != HOOK_NO_ERR) {
        logke("caplease: hook failed, err=%d\n", status);
        kp_free(g_context);
        g_context = 0;
        return -1;
    }

    cl_hook_installed = true;
    g_hook_target = target;

    logki("caplease: ready, target=%lx kpver=%x kver=%x\n", target, kpver, kver);
    return 0;
}

static long cl_module_control0(const char *ctl_args, char *out_msg, int outlen)
{
    if (g_context == 0) {
        return -1;
    }

    logki("caplease: paired=%d sessions=%d\n", g_context->paired ? 1 : 0, CL_MAX_SESSIONS);

    return 0;
}

static long cl_module_exit(void *reserved)
{
    logki("caplease: exit\n");

    if (cl_hook_installed && g_hook_target != 0) {
        hook_unwrap((void *)g_hook_target, (void *)cl_on_syscall, 0);
        cl_hook_installed = false;
        g_hook_target = 0;
    }

    if (g_context != 0) {
        cl_lease_revoke_owner(g_context, CL_REVOKE_ALL);

        for (uint32_t index = 0; index < CL_MAX_SESSIONS; ++index) {
            g_context->sessions[index].active = false;
            cl_wipe_local(g_context->sessions[index].token, CL_TOKEN_SIZE);
        }

        cl_wipe_local(g_context, (uint32_t)sizeof(struct cl_context));
        kp_free(g_context);
        g_context = 0;
    }

    return 0;
}

KPM_INIT(cl_module_init);
KPM_CTL0(cl_module_control0);
KPM_EXIT(cl_module_exit);