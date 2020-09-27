LOCAL_PATH := $(call my-dir)

LOCAL_PATH_SRC_GIF := $(LOCAL_PATH)
include $(LOCAL_PATH_SRC_GIF)/dgif/Android.mk
LOCAL_PATH := $(LOCAL_PATH_SRC_GIF)

include $(CLEAR_VARS)
LOCAL_MODULE := gif
LOCAL_SRC_FILES := native.c gif.c
LOCAL_CFLAGS += -std=c99
LOCAL_LDFLAGS += -Wl,--build-id=none
LOCAL_LDLIBS += -llog -ljnigraphics
LOCAL_STATIC_LIBRARIES := dgif
include $(BUILD_SHARED_LIBRARY)
