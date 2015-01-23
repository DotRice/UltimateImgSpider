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

urlList pageUrlList;
urlList imgUrlList;

#define MAX_PAGE_ONE_SITE	100000
#define MAX_IMG_ONE_SITE	100000

jboolean Java_com_UltimateImgSpider_SpiderCrawlActivity_jniUrlListInit(JNIEnv* env, jobject thiz)
{
	pageUrlList.list=malloc(MAX_PAGE_ONE_SITE*sizeof(urlNode));
	if(pageUrlList.list==NULL)
	{
		LOGI("malloc Fail!");
		return false;
	}

	imgUrlList.list=malloc(MAX_IMG_ONE_SITE*sizeof(urlNode));
	if(imgUrlList.list==NULL)
	{
		LOGI("malloc Fail!");
		return false;
	}
	
	pageUrlList.len=0;
	pageUrlList.max=MAX_PAGE_ONE_SITE;
	imgUrlList.len=0;
	imgUrlList.max=MAX_IMG_ONE_SITE;

	return true;
}

//添加URL 返回添加完成后的列表大小，返回0表示内存分配失败
jint Java_com_UltimateImgSpider_SpiderCrawlActivity_jniAddUrl(JNIEnv* env, jobject thiz, jstring jUrl, jint jHashCode, jint jType)
{
	int i;
	int ret=0;
	urlList *curList=(jType==URL_TYPE_PAGE)?(&pageUrlList):(&imgUrlList);
	urlNode *curNode=&(curList->list[0]);
	
	const u8 *url = (*env)->GetStringUTFChars(env, jUrl, NULL);
	//LOGI("url:%s hashCode:%d type:%d", url, jHashCode, jType);

	for(i=0; i<curList->len; i++)
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

	while(true)
	{
		if((i==curList->len)&&(curList->len<curList->max))
		{
			char *newUrl=malloc(strlen(url)+1);
			if(newUrl==NULL)
			{
				break;
			}
			else
			{
				strcpy(newUrl, url);

				curNode=&(curList->list[i]);
				curNode->url=newUrl;
				curNode->hashCode=jHashCode;
				curNode->state=URL_PENDING;
				curList->len++;
			}
		}

		ret=curList->len;
		break;
	}

	(*env)->ReleaseStringUTFChars(env, jUrl, url);
	
	return ret;
}

u32 urlSimilarity(const char *url1, const char *url2)
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

jstring Java_com_UltimateImgSpider_SpiderCrawlActivity_jniFindNextUrlToLoad(JNIEnv* env, jobject thiz, jstring jPrevUrl, jint jType)
{
	int i;
	urlList *curList=(jType==URL_TYPE_PAGE)?(&pageUrlList):(&imgUrlList);
	
	urlNode *nextNode=NULL;
	urlNode *curNode;
	
	LOGI("jniFindNextUrlToLoad jPrevUrl:%X", (int)jPrevUrl);
	
	if(jPrevUrl==NULL)
	{
		LOGI("jPrevUrl==NULL");
		
		for(i=0; i<curList->len; i++)
		{
			curNode=&(curList->list[i]);
			if(curNode->state==URL_PENDING)
			{
				nextNode=curNode;
			}
		}
	}
	else
	{
		bool scanComplete = true;
		int urlSim = 0;
		
		const u8 *prevUrl = (*env)->GetStringUTFChars(env, jPrevUrl, NULL);

		LOGI("prevUrl:%s curList->len:%d", prevUrl, curList->len);
		/**/
		for(i=0; i<(curList->len); i++)
		{
			curNode=&(curList->list[i]);
			/**/
			//LOGI("url %d:%s", i, curNode->url);
			if(curNode->state==URL_PENDING)
			{

				if (scanComplete)
				{
					scanComplete = false;
					urlSim=urlSimilarity(prevUrl, curNode->url);
					nextNode=curNode;
				}
				else
				{
					int curSim=urlSimilarity(prevUrl, curNode->url);
					if(curSim > urlSim)
                    {
                        urlSim = curSim;
                        nextNode = curNode;
                    }
				}

			}
		}

		(*env)->ReleaseStringUTFChars(env, jPrevUrl, prevUrl);
	}
	
	if(nextNode!=NULL)
	{
		LOGI("nextNode->url:%s", nextNode->url);
		nextNode->state=URL_DOWNLOADED;
		return (*env)->NewStringUTF(env, nextNode->url);
	}
	
	return (*env)->NewStringUTF(env, "");
}

void Java_com_UltimateImgSpider_SpiderCrawlActivity_jniOnDestroy(JNIEnv* env, jobject thiz)
{
	int i;
	
	for(i=0; i<pageUrlList.len; i++)
	{
		free(pageUrlList.list[i].url);
	}
	free(pageUrlList.list);
	
	for(i=0; i<imgUrlList.len; i++)
	{
		free(imgUrlList.list[i].url);
	}
	free(imgUrlList.list);
}

