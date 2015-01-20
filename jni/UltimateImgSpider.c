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

#define URL_TYPE_PAGE	0
#define URL_TYPE_IMG	1

typedef struct
{
	char *url;
	int hashCode;
	u8 state;
}urlNode;


typedef struct
{
	urlNode *list;
	int len;
	int max;
}urlList;

urlList *pageUrlList;
urlList *imgUrlList;

#define MAX_PAGE_ONE_SITE	100000
#define MAX_IMG_ONE_SITE	100000

jboolean Java_com_UltimateImgSpider_SpiderCrawlActivity_jniUrlListInit(JNIEnv* env, jobject thiz)
{
	pageUrlList->list=malloc(MAX_PAGE_ONE_SITE*sizeof(urlNode));
	if(pageUrlList->list==NULL)
	{
		LOGI("malloc Fail!");
		return false;
	}

	imgUrlList->list=malloc(MAX_IMG_ONE_SITE*sizeof(urlNode));
	if(imgUrlList->list==NULL)
	{
		LOGI("malloc Fail!");
		return false;
	}
	
	pageUrlList->len=0;
	pageUrlList->max=MAX_PAGE_ONE_SITE;
	imgUrlList->len=0;
	imgUrlList->max=MAX_IMG_ONE_SITE;

	return true;
}

//添加URL 返回添加完成后的列表大小，返回0表示内存分配失败
jint Java_com_UltimateImgSpider_SpiderCrawlActivity_jniRecvAddPageUrl(JNIEnv* env, jobject thiz, jstring jUrl, jint jHashCode, jint jType)
{
	int i;
	int ret;
	urlList *curList=(jType==URL_TYPE_PAGE)?pageUrlList:imgUrlList;
	urlNode *curNode;
	
	const u8 *url = (*env)->GetStringUTFChars(env, jUrl, NULL);
	LOGI("url:%s hashCode:%d", url, jHashCode);
	
	for(i=0; i<curList.len; i++)
	{
		curNode=&(curList->list[i]);
		if(curNode->hashCode==jHashCode)
		{
			if(strcmp(curNode->url, url)==0)
			{
				break;
			}
		}
	}
	
	if((i==curList->len)&&(curList->len<curList->max))
	{
		char *newUrl=malloc(strlen(url)+1);
		if(newUrl==NULL)
		{
			ret=0;
		}
		else
		{
			stpcpy(newUrl, url);
			
			curNode->url=newUrl;
			curNode->hashCode=jHashCode;
			curNode->state=URL_PENDING;
			curList->len++;
			
			ret=curList->len;
		}
	}
	
	(*env)->ReleaseStringUTFChars(env, jUrl, NULL);
	
	return ret;
}

u32 urlSimilarity(char *url1, char *url2)
{
	u32 len1=strlen(url1);
	u32 len2=strlen(url2);
	u32 len=(len2<len1)?len2:len1;
	u32 i;
	
	for(i=0; i<len; i++)
	{
		if(url1[i]!=url2[i])
		{
			break;
		}
	}
	
	return i;
}

jstring Java_com_UltimateImgSpider_SpiderCrawlActivity_jniFindNextUrlToLoad(JNIEnv* env, jobject thiz, jstring jUrl, jint jType)
{
	int i;
	char *nextUrl;
	
	urlList *curList=(jType==URL_TYPE_PAGE)?pageUrlList:imgUrlList;
	urlNode *curNode;
	
	if(jUrl==NULL)
	{
		for(i=0; i<curList->len; i++)
		{
			curNode=&(curList->list[i]);
			if(curNode->state==URL_PENDING)
			{
				nextUrl=curNode->url;
				curNode->state=URL_DOWNLOADED;
			}
		}
	}
	else
	{
		for(i=0; i<curList->len; i++)
		{
			curNode=&(curList->list[i]);
			
}

void Java_com_UltimateImgSpider_SpiderCrawlActivity_jniOnDestroy(JNIEnv* env, jobject thiz)
{
	int i;
	
	for(i=0; i<pageUrlList->len; i++)
	{
		free(pageUrlList.list[i].url);
	}
	free(pageUrlList.list);
	
	for(i=0; i<imgUrlList->len; i++)
	{
		free(imgUrlList.list[i].url);
	}
	free(imgUrlList.list);
}

