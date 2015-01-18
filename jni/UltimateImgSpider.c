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

enum URL_STATE
{
	URL_PENDING,
	URL_DOWNLOADED
};

typedef struct
{
	char *str;
	int hashCode;
	u8 state;
}urlNode;
/*
urlNode *imgUrlList;
urlNode *pageUrlList;
u8 *allocTest;
*/
jboolean Java_com_UltimateImgSpider_SpiderCrawlActivity_jniUrlListInit(JNIEnv* env, jobject thiz)
{
	/*
	//allocTest=malloc(100);//*1024*1024);
	if(allocTest==NULL)
	{
		LOGI("malloc Fail!");
		return false;
	}

	allocTest[0]=123;
	*/
	return false;
}

void Java_com_UltimateImgSpider_SpiderCrawlActivity_jniOnDestroy(JNIEnv* env, jobject thiz)
{
	//free(allocTest);
}

