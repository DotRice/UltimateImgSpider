#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <malloc.h>
#include <android/log.h>

#include "typeDef.h"

#define  LOG_TAG    "jni"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

jstring Java_com_UltimateImgSpider_SpiderCrawlActivity_stringFromJNI(
		JNIEnv* env, jobject thiz, jstring jSrcStr)
{
	const u8 *srcStr = (*env)->GetStringUTFChars(env, jSrcStr, NULL);
	LOGI("stringFromJNI %s", srcStr);
	(*env)->ReleaseStringUTFChars(env, jSrcStr, srcStr);
	return (*env)->NewStringUTF(env, "test jni !  Compiled with ABI " ABI ".");
}

