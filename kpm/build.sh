#!/system/bin/sh
set -eu

KP_DIR="${KP_DIR:-/data/local/tmp/KernelPatch}"
OUT="${1:-/data/local/tmp/caplease.kpm}"
SRC="$(dirname "$0")"

if [ ! -d "$KP_DIR/kernel/include" ]; then
    echo "kernel headers not found under $KP_DIR" >&2
    exit 1
fi

CC_BIN="$(command -v aarch64-linux-gnu-gcc || command -v clang || echo clang)"

"$CC_BIN" \
    -O2 \
    -fno-stack-protector \
    -fno-pic \
    -fno-asynchronous-unwind-tables \
    -fno-unwind-tables \
    -nostdinc \
    -ffreestanding \
    -Wall -Wextra \
    -Wno-unused-parameter \
    -Wno-sign-compare \
    -I"$SRC" \
    -I"$KP_DIR/kernel/include" \
    -I"$KP_DIR/kernel/patch/include" \
    -I"$KP_DIR/kernel/linux/include" \
    -I"$KP_DIR/kernel/linux/arch/arm64/include" \
    -I"$KP_DIR/kernel/linux/tools/arch/arm64/include" \
    -c -o /tmp/caplease_main.o "$SRC/main.c"

"$CC_BIN" \
    -O2 \
    -fno-stack-protector \
    -fno-pic \
    -fno-asynchronous-unwind-tables \
    -fno-unwind-tables \
    -nostdinc \
    -ffreestanding \
    -Wall -Wextra \
    -Wno-unused-parameter \
    -Wno-sign-compare \
    -I"$SRC" \
    -I"$KP_DIR/kernel/include" \
    -I"$KP_DIR/kernel/patch/include" \
    -I"$KP_DIR/kernel/linux/include" \
    -I"$KP_DIR/kernel/linux/arch/arm64/include" \
    -I"$KP_DIR/kernel/linux/tools/arch/arm64/include" \
    -c -o /tmp/caplease_state.o "$SRC/state.c"

ld -r -o "$OUT" /tmp/caplease_main.o /tmp/caplease_state.o

rm -f /tmp/caplease_main.o /tmp/caplease_state.o

echo "built $OUT"