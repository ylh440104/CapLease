#include "state.h"

#include <linux/string.h>

#include <kpmalloc.h>
#include <log.h>

static void cl_wipe(void *data, uint32_t length)
{
    volatile uint8_t *cursor = (volatile uint8_t *)data;
    while (length-- > 0) {
        *cursor++ = 0;
    }
}

static bool cl_bytes_equal(const uint8_t *left, const uint8_t *right, uint32_t length)
{
    uint8_t difference = 0;
    for (uint32_t index = 0; index < length; ++index) {
        difference |= (uint8_t)(left[index] ^ right[index]);
    }
    return difference == 0;
}

void cl_state_reset(struct cl_context *context)
{
    if (context == 0) {
        return;
    }

    cl_wipe(context, (uint32_t)sizeof(struct cl_context));
    context->next_lease_id = 1;
    context->next_session_id = 1;
    context->sequence = 1;
}

void cl_state_seed(struct cl_context *context, uint64_t seed_a, uint64_t seed_b)
{
    if (context == 0) {
        return;
    }

    context->sequence = seed_a ^ seed_b;
    if (context->sequence == 0) {
        context->sequence = 1;
    }
}

static uint64_t cl_sequence_next(struct cl_context *context)
{
    context->sequence = context->sequence * 6364136223846793005ull + 1442695040888963407ull;
    return context->sequence;
}

void cl_audit_append(
    struct cl_context *context,
    uint32_t kind,
    uint32_t actor_uid,
    uint32_t actor_pid,
    uint32_t detail,
    uint64_t value,
    uint64_t now_seconds)
{
    if (context == 0) {
        return;
    }

    uint32_t slot = context->audit_head;

    context->audit[slot].timestamp_seconds = now_seconds;
    context->audit[slot].value = value;
    context->audit[slot].kind = kind;
    context->audit[slot].actor_uid = actor_uid;
    context->audit[slot].actor_pid = actor_pid;
    context->audit[slot].detail = detail;

    context->audit_head = (slot + 1) % CL_AUDIT_CAPACITY;

    if (context->audit_count < CL_AUDIT_CAPACITY) {
        context->audit_count++;
    }
}

void cl_challenge_issue(
    struct cl_context *context,
    uint32_t owner_uid,
    uint64_t now_seconds,
    const uint8_t *nonce)
{
    if (context == 0 || nonce == 0) {
        return;
    }

    memcpy(context->challenge.nonce, nonce, CL_NONCE_SIZE);
    context->challenge.issued_seconds = now_seconds;
    context->challenge.owner_uid = owner_uid;
    context->challenge.active = true;
}

bool cl_challenge_consume(
    struct cl_context *context,
    uint32_t owner_uid,
    uint64_t now_seconds)
{
    if (context == 0 || !context->challenge.active) {
        return false;
    }

    if (context->challenge.owner_uid != owner_uid) {
        return false;
    }

    uint64_t elapsed = now_seconds - context->challenge.issued_seconds;
    if (elapsed > CL_CHALLENGE_WINDOW) {
        cl_wipe(&context->challenge, (uint32_t)sizeof(context->challenge));
        return false;
    }

    cl_wipe(&context->challenge, (uint32_t)sizeof(context->challenge));
    return true;
}

void cl_session_sweep(struct cl_context *context, uint64_t now_seconds)
{
    if (context == 0) {
        return;
    }

    for (uint32_t index = 0; index < CL_MAX_SESSIONS; ++index) {
        struct cl_session *session = &context->sessions[index];
        if (session->active && now_seconds >= session->expires_seconds) {
            session->active = false;
            cl_wipe(session->token, CL_TOKEN_SIZE);
        }
    }
}

struct cl_session *cl_session_open(
    struct cl_context *context,
    uint32_t owner_uid,
    uint64_t now_seconds,
    const uint8_t *token)
{
    if (context == 0 || token == 0) {
        return 0;
    }

    cl_session_sweep(context, now_seconds);

    for (uint32_t index = 0; index < CL_MAX_SESSIONS; ++index) {
        struct cl_session *session = &context->sessions[index];
        if (session->active) {
            continue;
        }

        session->active = true;
        session->id = (uint32_t)context->next_session_id++;
        session->owner_uid = owner_uid;
        session->issued_seconds = now_seconds;
        session->expires_seconds = now_seconds + CL_SESSION_TTL;
        memcpy(session->token, token, CL_TOKEN_SIZE);

        return session;
    }

    return 0;
}

struct cl_session *cl_session_find(
    struct cl_context *context,
    const uint8_t *token,
    uint64_t now_seconds)
{
    if (context == 0 || token == 0) {
        return 0;
    }

    for (uint32_t index = 0; index < CL_MAX_SESSIONS; ++index) {
        struct cl_session *session = &context->sessions[index];
        if (!session->active) {
            continue;
        }

        if (now_seconds >= session->expires_seconds) {
            session->active = false;
            cl_wipe(session->token, CL_TOKEN_SIZE);
            continue;
        }

        if (cl_bytes_equal(session->token, token, CL_TOKEN_SIZE)) {
            return session;
        }
    }

    return 0;
}

bool cl_session_close(struct cl_context *context, const uint8_t *token)
{
    if (context == 0 || token == 0) {
        return false;
    }

    for (uint32_t index = 0; index < CL_MAX_SESSIONS; ++index) {
        struct cl_session *session = &context->sessions[index];
        if (!session->active) {
            continue;
        }

        if (cl_bytes_equal(session->token, token, CL_TOKEN_SIZE)) {
            session->active = false;
            cl_wipe(session->token, CL_TOKEN_SIZE);
            return true;
        }
    }

    return false;
}

void cl_lease_sweep(struct cl_context *context, uint64_t now_seconds)
{
    if (context == 0) {
        return;
    }

    for (uint32_t index = 0; index < CL_MAX_LEASES; ++index) {
        struct cl_lease *lease = &context->leases[index];
        if (!lease->active) {
            continue;
        }

        if (now_seconds >= lease->expiry_seconds) {
            cl_audit_append(
                context,
                CL_AUDIT_LEASE_EXPIRED,
                lease->owner_uid,
                lease->owner_pid,
                (uint32_t)lease->capabilities,
                lease->id,
                now_seconds);

            lease->active = false;
        }
    }
}

struct cl_lease *cl_lease_create(
    struct cl_context *context,
    uint32_t owner_uid,
    uint32_t owner_pid,
    uint64_t capabilities,
    uint64_t seconds,
    uint32_t flags,
    uint64_t now_seconds)
{
    if (context == 0 || capabilities == 0 || seconds == 0) {
        return 0;
    }

    if (capabilities & ~CL_CAP_ALL) {
        return 0;
    }

    if (seconds > CL_MAX_LEASE_SECONDS) {
        seconds = CL_MAX_LEASE_SECONDS;
    }

    cl_lease_sweep(context, now_seconds);

    for (uint32_t index = 0; index < CL_MAX_LEASES; ++index) {
        struct cl_lease *lease = &context->leases[index];
        if (lease->active) {
            continue;
        }

        lease->active = true;
        lease->id = context->next_lease_id++;
        lease->capabilities = capabilities;
        lease->expiry_seconds = now_seconds + seconds;
        lease->cookie = cl_sequence_next(context);
        lease->owner_uid = owner_uid;
        lease->owner_pid = owner_pid;
        lease->flags = flags;

        return lease;
    }

    return 0;
}

struct cl_lease *cl_lease_find(struct cl_context *context, uint64_t lease_id)
{
    if (context == 0) {
        return 0;
    }

    for (uint32_t index = 0; index < CL_MAX_LEASES; ++index) {
        struct cl_lease *lease = &context->leases[index];
        if (lease->active && lease->id == lease_id) {
            return lease;
        }
    }

    return 0;
}

struct cl_lease *cl_lease_find_by_owner(struct cl_context *context, uint32_t owner_uid)
{
    if (context == 0) {
        return 0;
    }

    struct cl_lease *best = 0;

    for (uint32_t index = 0; index < CL_MAX_LEASES; ++index) {
        struct cl_lease *lease = &context->leases[index];
        if (!lease->active || lease->owner_uid != owner_uid) {
            continue;
        }

        if (best == 0 || lease->expiry_seconds > best->expiry_seconds) {
            best = lease;
        }
    }

    return best;
}

bool cl_lease_release(struct cl_context *context, uint64_t lease_id, uint32_t owner_uid)
{
    if (context == 0) {
        return false;
    }

    struct cl_lease *lease = cl_lease_find(context, lease_id);
    if (lease == 0) {
        return false;
    }

    if (lease->owner_uid != owner_uid) {
        return false;
    }

    lease->active = false;
    return true;
}

bool cl_lease_extend(
    struct cl_context *context,
    uint64_t lease_id,
    uint32_t owner_uid,
    uint64_t seconds,
    uint64_t now_seconds)
{
    if (context == 0 || seconds == 0) {
        return false;
    }

    struct cl_lease *lease = cl_lease_find(context, lease_id);
    if (lease == 0) {
        return false;
    }

    if (lease->owner_uid != owner_uid) {
        return false;
    }

    if (now_seconds >= lease->expiry_seconds) {
        lease->active = false;
        return false;
    }

    if (seconds > CL_MAX_LEASE_SECONDS) {
        seconds = CL_MAX_LEASE_SECONDS;
    }

    lease->expiry_seconds = now_seconds + seconds;
    return true;
}

uint32_t cl_lease_revoke_owner(struct cl_context *context, uint32_t owner_uid)
{
    if (context == 0) {
        return 0;
    }

    uint32_t revoked = 0;

    for (uint32_t index = 0; index < CL_MAX_LEASES; ++index) {
        struct cl_lease *lease = &context->leases[index];
        if (!lease->active) {
            continue;
        }

        if (owner_uid == 0xFFFFFFFFu || lease->owner_uid == owner_uid) {
            lease->active = false;
            revoked++;
        }
    }

    return revoked;
}

uint32_t cl_lease_snapshot(struct cl_context *context, struct cl_lease_record *out, uint32_t limit)
{
    if (context == 0 || out == 0 || limit == 0) {
        return 0;
    }

    uint32_t written = 0;

    for (uint32_t index = 0; index < CL_MAX_LEASES && written < limit; ++index) {
        struct cl_lease *lease = &context->leases[index];
        if (!lease->active) {
            continue;
        }

        out[written].lease_id = lease->id;
        out[written].capabilities = lease->capabilities;
        out[written].expiry_seconds = lease->expiry_seconds;
        out[written].cookie = lease->cookie;
        out[written].owner_pid = lease->owner_pid;
        out[written].owner_uid = lease->owner_uid;
        out[written].flags = lease->flags;
        out[written].reserved = 0;

        written++;
    }

    return written;
}

uint32_t cl_lease_retain_for_task(struct cl_context *context, uint32_t owner_uid)
{
    if (context == 0) {
        return 0;
    }

    uint32_t retained = 0;

    for (uint32_t index = 0; index < CL_MAX_LEASES; ++index) {
        struct cl_lease *lease = &context->leases[index];
        if (!lease->active) {
            continue;
        }

        if (lease->owner_uid != owner_uid) {
            continue;
        }

        if ((lease->flags & CL_LEASE_ONE_SHOT) != 0) {
            lease->active = false;
            continue;
        }

        retained++;
    }

    return retained;
}

uint32_t cl_audit_snapshot(struct cl_context *context, struct cl_audit_record *out, uint32_t limit)
{
    if (context == 0 || out == 0 || limit == 0) {
        return 0;
    }

    uint32_t available = context->audit_count;
    if (available > limit) {
        available = limit;
    }

    for (uint32_t index = 0; index < available; ++index) {
        uint32_t slot = (context->audit_head + CL_AUDIT_CAPACITY - available + index) % CL_AUDIT_CAPACITY;
        out[index] = context->audit[slot];
    }

    return available;
}

void cl_audit_clear(struct cl_context *context)
{
    if (context == 0) {
        return;
    }

    context->audit_head = 0;
    context->audit_count = 0;
    cl_wipe(context->audit, (uint32_t)sizeof(context->audit));
}