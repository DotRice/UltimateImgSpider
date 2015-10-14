LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := UltimateImgSpider
LOCAL_LDLIBS := \
	-llog \

LOCAL_SRC_FILES := \
	F:\android\UltimateImgSpider\app\src\main\jni\Android.mk \
	F:\android\UltimateImgSpider\app\src\main\jni\Application.mk \
	F:\android\UltimateImgSpider\app\src\main\jni\UltimateImgSpider.c \

LOCAL_C_INCLUDES += F:\android\UltimateImgSpider\app\src\main\jni
LOCAL_C_INCLUDES += F:\android\UltimateImgSpider\app\src\debug\jni

include $(BUILD_SHARED_LIBRARY)
