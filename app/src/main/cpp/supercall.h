#pragma once

#include <cstdint>

#include "protocol.h"

namespace caplease {

class Supercall {
public:
    static long exchange(Exchange* exchange) noexcept;
    static bool available() noexcept;
};

}