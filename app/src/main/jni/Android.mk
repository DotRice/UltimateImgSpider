LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := UltimateImgSpider
LOCAL_SRC_FILES := UltimateImgSpider.c ashmem.c funcName.c
LOCAL_LDLIBS    := -lm -llog

include $(BUILD_SHARED_LIBRARY)