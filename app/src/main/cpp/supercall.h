#pragma once

#include <cstdint>

#include "protocol.h"

namespace caplease {

class Supercall {
public:
    static long hello() noexcept;
    static long exchange(Request* request, Response* response) noexcept;
    static bool available() noexcept;
};

}