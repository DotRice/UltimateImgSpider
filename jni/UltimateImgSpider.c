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

#define MAX_PAGE_ONE_SITE	200000
#define MAX_IMG_ONE_SITE	200000

#define MAX_SIZE_PER_URL	4096

#define SIZE_PER_URLPOOL	(1024*1024-16)
typedef struct memPool
{
	char 			mem[SIZE_PER_URLPOOL];
	u32  			idleMemPtr;
	struct memPool* next;
}t_urlPool;

t_urlPool *firstUrlPool=NULL;
char *urlMalloc(u32 size)
{
	t_urlPool *urlPool;

	if(size>MAX_SIZE_PER_URL)
	{
		return NULL;
	}

	if(firstUrlPool==NULL)
	{
		firstUrlPool=malloc(sizeof(t_urlPool));
		if(firstUrlPool==NULL)
		{
			return NULL;
		}
		else
		{
			firstUrlPool->idleMemPtr=0;
			firstUrlPool->next=NULL;

			LOGI("init firstUrlPool Success");
		}
	}

	urlPool=firstUrlPool;
	while(true)
	{
		if((urlPool->idleMemPtr+size)<=SIZE_PER_URLPOOL)
		{
			u32 retPtr=urlPool->idleMemPtr;
			urlPool->idleMemPtr+=size;
			return urlPool->mem+retPtr;
		}
		else
		{
			if(urlPool->next!=NULL)
			{
				urlPool=urlPool->next;
			}
			else
			{
				break;
			}
		}
	}

	urlPool->next=malloc(sizeof(t_urlPool));
	if(urlPool->next!=NULL)
	{
		urlPool=urlPool->next;

		urlPool->idleMemPtr=size;
		urlPool->next=NULL;

		LOGI("init new urlPool Success");

		return urlPool->mem;
	}

	return NULL;
}

void urlListTest()
{
	int i;
	char url[200];

	for(i=0; i<180000; i++)
	{
		urlList *curList=(i&0x01)?(&pageUrlList):(&imgUrlList);
		urlNode *curNode;

		sprintf(url, "http://www.umei.cc/p/gaoqing/rihan/indexp/gaoqing/rihan/indexp/gaoqing/rihan/indexp/gaoqing/rihan/indexp/gaoqing/rihan/index-%d.htm", i);

		char *newUrl=urlMalloc(strlen(url)+1);
		if(newUrl==NULL)
		{
			break;
		}
		else
		{
			strcpy(newUrl, url);

			curNode=&(curList->list[curList->len]);
			curNode->url=newUrl;
			curNode->hashCode=0x233445;
			curNode->state=URL_DOWNLOADED;
			curList->len++;
		}
	}
}

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
	imgUrlList.len=0;
	pageUrlList.max=MAX_PAGE_ONE_SITE;
	imgUrlList.max=MAX_IMG_ONE_SITE;

	urlListTest();

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
			char *newUrl=urlMalloc(strlen(url)+1);
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
	
	//LOGI("jniFindNextUrlToLoad jPrevUrl:%X", (int)jPrevUrl);

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

		//LOGI("prevUrl:%s curList->len:%d", prevUrl, curList->len);

		for(i=0; i<(curList->len); i++)
		{
			curNode=&(curList->list[i]);

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
		//LOGI("nextNode->url:%s", nextNode->url);
		nextNode->state=URL_DOWNLOADED;
		return (*env)->NewStringUTF(env, nextNode->url);
	}

	return (*env)->NewStringUTF(env, "");
}

void Java_com_UltimateImgSpider_SpiderCrawlActivity_jniOnDestroy(JNIEnv* env, jobject thiz)
{
	int i;
	

	pageUrlList.len=0;
	imgUrlList.len=0;
	free(pageUrlList.list);
	free(imgUrlList.list);
	
	if(firstUrlPool!=NULL)
	{
		t_urlPool *urlPool=firstUrlPool;

		do
		{
			t_urlPool *next=urlPool->next;
			free(urlPool);
			urlPool=next;
		}
		while(urlPool!=NULL);

		firstUrlPool=NULL;
	}
	LOGI("jniOnDestroy free all url");
}

