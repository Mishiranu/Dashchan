LOCAL_PATH := $(call my-dir)

LOCAL_PATH_SRC_GIF := $(LOCAL_PATH)
include $(LOCAL_PATH_SRC_GIF)/dgif/Android.mk
LOCAL_PATH := $(LOCAL_PATH_SRC_GIF)

include $(CLEAR_VARS)
LOCAL_MODULE := gif
LOCAL_SRC_FILES := native.c gif.c
LOCAL_CFLAGS += -std=c99 -Wall -Wextra -Wpedantic
LOCAL_LDFLAGS += -Wl,--build-id=none
LOCAL_LDLIBS += -ljnigraphics
ifeq ($(notdir $(realpath $(dir $(NDK_OUT)))),ndebug)
LOCAL_CFLAGS += -DDEBUG_VERBOSE
LOCAL_LDLIBS += -llog
else
LOCAL_CFLAGS += -Werror
endif
LOCAL_STATIC_LIBRARIES := dgif
include $(BUILD_SHARED_LIBRARY)
