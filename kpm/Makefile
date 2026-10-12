ifndef TARGET_COMPILE
    $(error TARGET_COMPILE not set)
endif

ifndef KP_DIR
    $(error KP_DIR not set)
endif

CC = $(TARGET_COMPILE)gcc
LD = $(TARGET_COMPILE)ld

INCLUDE_DIRS := . \
    $(KP_DIR)/kernel/include \
    $(KP_DIR)/kernel/patch/include \
    $(KP_DIR)/kernel/linux/include \
    $(KP_DIR)/kernel/linux/arch/arm64/include \
    $(KP_DIR)/kernel/linux/tools/arch/arm64/include

INCLUDE_FLAGS := $(foreach dir,$(INCLUDE_DIRS),-I$(dir))

CFLAGS := -O2 -fno-stack-protector -fno-pic -fno-asynchronous-unwind-tables \
    -fno-unwind-tables -nostdinc -ffreestanding -Wall -Wextra \
    -Wno-unused-parameter -Wno-sign-compare

objs := main.o state.o

all: caplease.kpm

caplease.kpm: $(objs)
	$(LD) -r -o $@ $(objs)

%.o: %.c protocol.h state.h
	$(CC) $(CFLAGS) $(INCLUDE_FLAGS) -c -o $@ $<

.PHONY: clean
clean:
	rm -f *.o *.kpm