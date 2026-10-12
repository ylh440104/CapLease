#ifndef CAPLEASE_STATE_H
#define CAPLEASE_STATE_H

#include <stdint.h>
#include <stdbool.h>

#include "protocol.h"

struct cl_session {
    bool active;
    uint32_t id;
    uint64_t issued_seconds;
    uint64_t expires_seconds;
    uint8_t token[CL_TOKEN_SIZE];
    uint32_t owner_uid;
};

struct cl_lease {
    bool active;
    uint64_t id;
    uint64_t capabilities;
    uint64_t expiry_seconds;
    uint64_t cookie;
    uint32_t owner_uid;
    uint32_t owner_pid;
    uint32_t flags;
};

struct cl_pending_challenge {
    bool active;
    uint8_t nonce[CL_NONCE_SIZE];
    uint64_t issued_seconds;
    uint32_t owner_uid;
};

struct cl_context {
    bool paired;
    uint8_t verifier[32];
    uint8_t salt[16];
    uint32_t pairing_generation;
    uint64_t next_lease_id;
    uint64_t next_session_id;
    uint64_t sequence;
    struct cl_pending_challenge challenge;
    struct cl_session sessions[CL_MAX_SESSIONS];
    struct cl_lease leases[CL_MAX_LEASES];
    struct cl_audit_record audit[CL_AUDIT_CAPACITY];
    uint32_t audit_head;
    uint32_t audit_count;
};

void cl_state_reset(struct cl_context *context);
void cl_state_seed(struct cl_context *context, uint64_t seed_a, uint64_t seed_b);

void cl_audit_append(
    struct cl_context *context,
    uint32_t kind,
    uint32_t actor_uid,
    uint32_t actor_pid,
    uint32_t detail,
    uint64_t value,
    uint64_t now_seconds);

void cl_challenge_issue(
    struct cl_context *context,
    uint32_t owner_uid,
    uint64_t now_seconds,
    const uint8_t *nonce);

bool cl_challenge_consume(
    struct cl_context *context,
    uint32_t owner_uid,
    uint64_t now_seconds);

struct cl_session *cl_session_open(
    struct cl_context *context,
    uint32_t owner_uid,
    uint64_t now_seconds,
    const uint8_t *token);

struct cl_session *cl_session_find(
    struct cl_context *context,
    const uint8_t *token,
    uint64_t now_seconds);

bool cl_session_close(struct cl_context *context, const uint8_t *token);

void cl_session_sweep(struct cl_context *context, uint64_t now_seconds);

struct cl_lease *cl_lease_create(
    struct cl_context *context,
    uint32_t owner_uid,
    uint32_t owner_pid,
    uint64_t capabilities,
    uint64_t seconds,
    uint32_t flags,
    uint64_t now_seconds);

struct cl_lease *cl_lease_find(struct cl_context *context, uint64_t lease_id);

struct cl_lease *cl_lease_find_by_owner(struct cl_context *context, uint32_t owner_uid);

bool cl_lease_release(struct cl_context *context, uint64_t lease_id, uint32_t owner_uid);

bool cl_lease_extend(
    struct cl_context *context,
    uint64_t lease_id,
    uint32_t owner_uid,
    uint64_t seconds,
    uint64_t now_seconds);

uint32_t cl_lease_revoke_owner(struct cl_context *context, uint32_t owner_uid);

void cl_lease_sweep(struct cl_context *context, uint64_t now_seconds);

uint32_t cl_lease_snapshot(struct cl_context *context, struct cl_lease_record *out, uint32_t limit);

uint32_t cl_lease_retain_for_task(struct cl_context *context, uint32_t owner_uid);

uint32_t cl_audit_snapshot(struct cl_context *context, struct cl_audit_record *out, uint32_t limit);

void cl_audit_clear(struct cl_context *context);

#endif