LOCAL_PATH := $(call my-dir)

LOCAL_PATH_SRC_PLAYER := $(LOCAL_PATH)
include $(LOCAL_PATH_SRC_PLAYER)/ffmpeg/Android.mk
include $(LOCAL_PATH_SRC_PLAYER)/yuv/Android.mk
LOCAL_PATH := $(LOCAL_PATH_SRC_PLAYER)

include $(CLEAR_VARS)
LOCAL_MODULE := player
LOCAL_SRC_FILES := native.c player.c util.c
LOCAL_CFLAGS += -std=c99 -Wall -Wextra -Wpedantic
LOCAL_LDFLAGS += -Wl,--build-id=none
LOCAL_LDLIBS += -landroid -lOpenSLES
ifeq ($(notdir $(realpath $(dir $(NDK_OUT)))),ndebug)
LOCAL_CFLAGS += -DDEBUG_VERBOSE
LOCAL_LDLIBS += -llog
else
LOCAL_CFLAGS += -Werror
endif
LOCAL_SHARED_LIBRARIES := avcodec avformat avutil swresample swscale yuv
include $(BUILD_SHARED_LIBRARY)
