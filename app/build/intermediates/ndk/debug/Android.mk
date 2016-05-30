LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := UltimateImgSpider
LOCAL_LDFLAGS := -Wl,--build-id
LOCAL_LDLIBS := \
	-llog \

LOCAL_SRC_FILES := \
	D:\android\UltimateImgSpider\app\src\main\jni\Android.mk \
	D:\android\UltimateImgSpider\app\src\main\jni\Application.mk \
	D:\android\UltimateImgSpider\app\src\main\jni\ashmem.c \
	D:\android\UltimateImgSpider\app\src\main\jni\UltimateImgSpider.c \

LOCAL_C_INCLUDES += D:\android\UltimateImgSpider\app\src\main\jni
LOCAL_C_INCLUDES += D:\android\UltimateImgSpider\app\src\debug\jni

include $(BUILD_SHARED_LIBRARY)
