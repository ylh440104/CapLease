#ifndef CAPLEASE_KPM_H
#define CAPLEASE_KPM_H

#include <stdint.h>

#define CL_PROTOCOL_MAGIC 0x434C5031u
#define CL_PROTOCOL_VERSION 2u

#define CL_TOKEN_SIZE 32
#define CL_NONCE_SIZE 32
#define CL_PAYLOAD_SIZE 512
#define CL_PACKAGE_MAX 128

#define CL_SUPERCALL_NR 45

#define CL_MAX_LEASES 64
#define CL_MAX_SESSIONS 16
#define CL_AUDIT_CAPACITY 256
#define CL_CHALLENGE_WINDOW 30
#define CL_SESSION_TTL 600
#define CL_MAX_LEASE_SECONDS 86400

#define CL_OP_HELLO 1
#define CL_OP_INFO 2
#define CL_OP_CHALLENGE 3
#define CL_OP_AUTHENTICATE 4
#define CL_OP_SESSION_DROP 5
#define CL_OP_PAIR 6
#define CL_OP_UNPAIR 7
#define CL_OP_LEASE_ACQUIRE 16
#define CL_OP_LEASE_RELEASE 17
#define CL_OP_LEASE_QUERY 18
#define CL_OP_LEASE_EXTEND 19
#define CL_OP_LEASE_REVOKE_ALL 20
#define CL_OP_EXEC 32
#define CL_OP_AUDIT_READ 48
#define CL_OP_AUDIT_CLEAR 49

#define CL_OK 0
#define CL_ERR_NOT_READY -1
#define CL_ERR_BAD_MAGIC -2
#define CL_ERR_BAD_VERSION -3
#define CL_ERR_UNKNOWN_OP -4
#define CL_ERR_INVALID_ARGUMENT -5
#define CL_ERR_UNAUTHENTICATED -6
#define CL_ERR_BAD_CREDENTIAL -7
#define CL_ERR_CHALLENGE_MISSING -8
#define CL_ERR_CHALLENGE_EXPIRED -9
#define CL_ERR_ALREADY_PAIRED -10
#define CL_ERR_NOT_PAIRED -11
#define CL_ERR_CAPABILITY_DENIED -12
#define CL_ERR_LEASE_MISSING -13
#define CL_ERR_LEASE_EXPIRED -14
#define CL_ERR_LEASE_LIMIT_REACHED -15
#define CL_ERR_TARGET_DENIED -16
#define CL_ERR_INTERNAL -17
#define CL_ERR_NOT_SUPPORTED -18
#define CL_ERR_BUFFER_TOO_SMALL -19

#define CL_CAP_FS_READ_APP (1ull << 0)
#define CL_CAP_FS_WRITE_APP (1ull << 1)
#define CL_CAP_NET_RAW (1ull << 2)
#define CL_CAP_NET_ADMIN (1ull << 3)
#define CL_CAP_MOUNT (1ull << 4)
#define CL_CAP_MODULE (1ull << 5)
#define CL_CAP_KERNEL_LOG (1ull << 6)
#define CL_CAP_PTRACE (1ull << 7)
#define CL_CAP_SET_IDENTITY (1ull << 8)
#define CL_CAP_RELAX_LABEL (1ull << 9)
#define CL_CAP_AUDIT_READ (1ull << 10)
#define CL_CAP_DEVICE_NODE (1ull << 11)

#define CL_CAP_ALL 0xFFFFFFFFull

#define CL_REVOKE_ALL 0xFFFFFFFFu

#define CL_LEASE_ONE_SHOT (1u << 0)
#define CL_LEASE_INHERIT (1u << 1)
#define CL_LEASE_NO_AUDIT (1u << 2)

#define CL_AUDIT_SESSION_OPENED 1
#define CL_AUDIT_SESSION_CLOSED 2
#define CL_AUDIT_LEASE_GRANTED 3
#define CL_AUDIT_LEASE_RELEASED 4
#define CL_AUDIT_LEASE_EXPIRED 5
#define CL_AUDIT_CAPABILITY_USED 6
#define CL_AUDIT_CAPABILITY_REFUSED 7
#define CL_AUDIT_EXEC_COMPLETED 8
#define CL_AUDIT_PAIRING_CHANGED 9

struct cl_request {
    uint32_t magic;
    uint32_t version;
    uint32_t op;
    uint32_t payload_length;
    uint64_t arg0;
    uint64_t arg1;
    uint64_t arg2;
    uint64_t arg3;
    uint8_t token[CL_TOKEN_SIZE];
    uint8_t payload[CL_PAYLOAD_SIZE];
};

struct cl_response {
    int32_t status;
    uint32_t payload_length;
    uint64_t value;
    uint8_t payload[CL_PAYLOAD_SIZE];
};

struct cl_exchange {
    struct cl_request request;
    struct cl_response response;
};

struct cl_lease_record {
    uint64_t lease_id;
    uint64_t capabilities;
    uint64_t expiry_seconds;
    uint64_t cookie;
    uint32_t owner_pid;
    uint32_t owner_uid;
    uint32_t flags;
    uint32_t reserved;
};

struct cl_audit_record {
    uint64_t timestamp_seconds;
    uint64_t value;
    uint32_t kind;
    uint32_t actor_uid;
    uint32_t actor_pid;
    uint32_t detail;
};

struct cl_info_payload {
    uint32_t kernel_patch_version;
    uint32_t kernel_version;
    uint32_t protocol_version;
    uint32_t lease_slots;
    uint64_t capability_mask;
    uint64_t boot_seconds;
};

struct cl_challenge_payload {
    uint8_t nonce[CL_NONCE_SIZE];
    uint64_t issued_seconds;
    uint32_t window_seconds;
    uint32_t reserved;
};

struct cl_authenticate_payload {
    uint8_t proof[CL_NONCE_SIZE];
    uint8_t client_nonce[CL_NONCE_SIZE];
};

struct cl_pair_payload {
    uint8_t verifier[32];
    uint8_t salt[16];
};

#endif
