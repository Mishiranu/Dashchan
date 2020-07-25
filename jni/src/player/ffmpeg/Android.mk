# Copyright 2016, 2020 Fukurou Mishiranu
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := avcodec
LOCAL_SRC_FILES := shared/$(TARGET_ARCH_ABI)/libavcodec.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include/$(TARGET_ARCH_ABI)
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avformat
LOCAL_SRC_FILES := shared/$(TARGET_ARCH_ABI)/libavformat.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include/$(TARGET_ARCH_ABI)
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avutil
LOCAL_SRC_FILES := shared/$(TARGET_ARCH_ABI)/libavutil.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include/$(TARGET_ARCH_ABI)
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := swresample
LOCAL_SRC_FILES := shared/$(TARGET_ARCH_ABI)/libswresample.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include/$(TARGET_ARCH_ABI)
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := swscale
LOCAL_SRC_FILES := shared/$(TARGET_ARCH_ABI)/libswscale.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include/$(TARGET_ARCH_ABI)
include $(PREBUILT_SHARED_LIBRARY)
