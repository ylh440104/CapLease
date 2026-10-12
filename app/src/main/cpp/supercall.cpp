#include "supercall.h"

#include <cerrno>
#include <sys/syscall.h>
#include <unistd.h>

namespace caplease {

namespace {

constexpr long encodeOp(std::uint32_t op) noexcept
{
    return static_cast<long>(kProtocolMagic) | static_cast<long>(op);
}

}

long Supercall::exchange(Exchange* exchange) noexcept
{
    if (exchange == nullptr) {
        return -EINVAL;
    }

    exchange->request.magic = kProtocolMagic;
    exchange->request.version = kProtocolVersion;
    exchange->response.status = 0;
    exchange->response.payloadLength = 0;
    exchange->response.value = 0;

    return ::syscall(
        static_cast<long>(kSupercallNumber),
        static_cast<void*>(exchange),
        encodeOp(exchange->request.op));
}

bool Supercall::available() noexcept
{
    Exchange probe{};
    probe.request.op = static_cast<std::uint32_t>(Op::Hello);

    errno = 0;
    const long result = exchange(&probe);

    if (result < 0) {
        return false;
    }

    return probe.response.status == 0
        && probe.response.value == static_cast<std::uint64_t>(kProtocolMagic);
}

}