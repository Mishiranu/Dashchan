LOCAL_PATH := $(call my-dir)

LOCAL_PATH_SRC_PLAYER := $(LOCAL_PATH)
include $(LOCAL_PATH_SRC_PLAYER)/ffmpeg/Android.mk
include $(LOCAL_PATH_SRC_PLAYER)/yuv/Android.mk
LOCAL_PATH := $(LOCAL_PATH_SRC_PLAYER)

include $(CLEAR_VARS)
LOCAL_MODULE := player
LOCAL_SRC_FILES := native.c player.c util.c
LOCAL_CFLAGS += -std=c99
LOCAL_LDFLAGS += -Wl,--build-id=none
LOCAL_LDLIBS += -llog -landroid -lOpenSLES
LOCAL_SHARED_LIBRARIES := avcodec avformat avutil swresample swscale yuv
include $(BUILD_SHARED_LIBRARY)
