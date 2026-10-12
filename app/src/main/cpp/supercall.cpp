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

long Supercall::exchange(Request* request, Response* response) noexcept
{
    if (request == nullptr || response == nullptr) {
        return -EINVAL;
    }

    return ::syscall(
        static_cast<long>(kSupercallNumber),
        request,
        response,
        encodeOp(request->op));
}

long Supercall::hello() noexcept
{
    return ::syscall(
        static_cast<long>(kSupercallNumber),
        static_cast<void*>(nullptr),
        static_cast<void*>(nullptr),
        encodeOp(static_cast<std::uint32_t>(Op::Hello)));
}

bool Supercall::available() noexcept
{
    errno = 0;
    const long result = hello();
    return result >= 0 && errno != ENOSYS;
}

}