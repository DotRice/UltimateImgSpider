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

urlNode *pageUrlList;
urlNode *imgUrlList;

#define MAX_PAGE_ONE_SITE	100000
#define MAX_IMG_ONE_SITE	100000

jboolean Java_com_UltimateImgSpider_SpiderCrawlActivity_jniUrlListInit(JNIEnv* env, jobject thiz)
{
	pageUrlList=malloc(MAX_PAGE_ONE_SITE*sizeof(urlNode));
	if(pageUrlList==NULL)
	{
		LOGI("malloc Fail!");
		return false;
	}

	imgUrlList=malloc(MAX_PAGE_ONE_SITE*sizeof(urlNode));
	if(imgUrlList==NULL)
	{
		LOGI("malloc Fail!");
		return false;
	}

	return true;
}

jboolean Java_com_UltimateImgSpider_SpiderCrawlActivity_jniRecvPageUrl(JNIEnv* env, jobject thiz, jstring jPageUrl, jint jHashCode)
{
	jboolean ret;
	const u8 *pageUrl = (*env)->GetStringUTFChars(env, jPageUrl, NULL);
	LOGI("pageUrl:%s hashCode:%d", pageUrl, jHashCode);
	(*env)->ReleaseStringUTFChars(env, jPageUrl, pageUrl);
	
	return true;
}

void Java_com_UltimateImgSpider_SpiderCrawlActivity_jniOnDestroy(JNIEnv* env, jobject thiz)
{
	free(pageUrlList);
	free(imgUrlList);
}

