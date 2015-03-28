#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <malloc.h>
#include <android/log.h>
#include <linux/ashmem.h>
#include <asm-generic/fcntl.h>
#include <sys/mman.h>

#include "typeDef.h"


/*
 * 使用共享内存存储urlList，实现重启下载服务进程后能恢复工作现场。
 * 需要新申请一段内存时，新建一段共享内存，并把文件描述符发送给看门狗服务进程，
 * 看门狗服务进程用这个文件描述符映射此段共享内存到自己虚拟内存空间。
 */



int ashmem_create_region(const char *name, u32 size)
{
	int fd, ret;
	char buf[ASHMEM_NAME_LEN];

	while (name && size)
	{
		fd = open(ASHMEM_NAME_DEF, O_RDWR);
		if (fd < 0)
		{
			break;
		}

		LOGI("ashmem open success %d", fd);

		strlcpy(buf, name, sizeof(buf));
		ret = ioctl(fd, ASHMEM_SET_NAME, buf);
		if (ret < 0)
		{
			close(fd);
			break;
		}
		ret = ioctl(fd, ASHMEM_SET_SIZE, size);
		if (ret < 0)
		{
			close(fd);
			break;
		}

		break;
	}

	return fd;
}


jmethodID registerAshmemPoolMID=NULL;
jclass SpiderServiceClass=NULL;
jobject SpiderServiceInstance=NULL;

void registerAshmemPool(JNIEnv* env, int fd)
{
	if(registerAshmemPoolMID==NULL)
	{
		SpiderServiceClass = (*env)->FindClass(env,
			"com/UltimateImgSpider/SpiderService");
		if (SpiderServiceClass != NULL)
		{
			LOGI("find class");

			registerAshmemPoolMID = (*env)->GetMethodID(env, SpiderServiceClass, "registerAshmemPool",
					"(I)Z");
		}
	}

	if (registerAshmemPoolMID != NULL)
	{
		LOGI("find Method");
		(*env)->CallBooleanMethod(env, SpiderServiceInstance, registerAshmemPoolMID, fd);
	}

}


#define ASHMEM_FILESIZE	32
void* ashmemTest(JNIEnv* env, char* ashmName)
{
	int i, fd;
	u8* mAddr = NULL;

	fd = ashmem_create_region(ashmName, ASHMEM_FILESIZE);

	if (fd >= 0)
	{
		mAddr = (u8*) mmap(NULL, ASHMEM_FILESIZE, PROT_READ | PROT_WRITE,
		MAP_SHARED, fd, 0);
		if (mAddr != NULL)
		{
			LOGI("mmap %d success!", (u32 )mAddr);

			for(i=0; i<ASHMEM_FILESIZE; i++)
			{
				mAddr[i]=i;
			}

			registerAshmemPool(env, fd);
		}
	}

	return mAddr;
}

void Java_com_UltimateImgSpider_WatchdogService_jniRegisterAshmem(JNIEnv* env,
		jobject thiz, jint fd)
{
	LOGI("handleAshmem %d", fd);
	u8 *addr=(u8*) mmap(NULL, ASHMEM_FILESIZE, PROT_READ | PROT_WRITE,
			MAP_SHARED, fd, 0);

	if(addr!=NULL)
	{
		int i;

		LOGI("data:");
		for(i=0; i<ASHMEM_FILESIZE; i++)
		{
			LOGI("%d", addr[i]);
		}
	}

}

jstring Java_com_UltimateImgSpider_SpiderService_stringFromJNI(JNIEnv* env,
		jobject thiz, jstring jSrcStr)
{
	int i;
	const u8 *srcStr = (*env)->GetStringUTFChars(env, jSrcStr, NULL);
	LOGI("stringFromJNI %s", srcStr);

	SpiderServiceInstance=thiz;

	u8 *mWrite = ashmemTest(env, "write");

	if((*env)->ExceptionOccurred(env)) {
	   return NULL;
	}

	(*env)->ReleaseStringUTFChars(env, jSrcStr, srcStr);
	return (*env)->NewStringUTF(env, "test jni !");
}






enum URL_STATE
{
	URL_PENDING, URL_DOWNLOADED
};

#define URL_TYPE_PAGE	0
#define URL_TYPE_IMG	1

typedef struct
{
	char *url;
	int hashCode;
	u8 state;
} urlNode;

typedef struct
{
	urlNode *list;
	urlNode *curNode;
	int len;
	int max;
} urlList;

urlList pageUrlList;
urlList imgUrlList;

#define MAX_PAGE_ONE_SITE	200000
#define MAX_IMG_ONE_SITE	200000

#define MAX_SIZE_PER_URL	4096

#define SIZE_PER_URLPOOL	(1024*1024-16)
typedef struct memPool
{
	char mem[SIZE_PER_URLPOOL];
	u32 idleMemPtr;
	struct memPool* next;
} t_urlPool;

t_urlPool *firstUrlPool = NULL;
char *urlMalloc(u32 size)
{
	t_urlPool *urlPool = firstUrlPool;

	if ((size > MAX_SIZE_PER_URL) || (urlPool == NULL ))
	{
		return NULL ;
	}

	while (true)
	{
		if ((urlPool->idleMemPtr + size) <= SIZE_PER_URLPOOL)
		{
			u32 retPtr = urlPool->idleMemPtr;
			urlPool->idleMemPtr += size;
			return urlPool->mem + retPtr;
		}
		else
		{
			if (urlPool->next != NULL)
			{
				urlPool = urlPool->next;
			}
			else
			{
				break;
			}
		}
	}

	urlPool->next = malloc(sizeof(t_urlPool));
	if (urlPool->next != NULL)
	{
		urlPool = urlPool->next;

		urlPool->idleMemPtr = size;
		urlPool->next = NULL;

		LOGI("init new urlPool Success");

		return urlPool->mem;
	}

	return NULL ;
}

jboolean urlListInit()
{
	pageUrlList.list = malloc(MAX_PAGE_ONE_SITE * sizeof(urlNode));
	if (pageUrlList.list == NULL)
	{
		LOGI("malloc Fail!");
		return false;
	}

	imgUrlList.list = malloc(MAX_IMG_ONE_SITE * sizeof(urlNode));
	if (imgUrlList.list == NULL)
	{
		LOGI("malloc Fail!");
		return false;
	}

	pageUrlList.curNode=NULL;
	pageUrlList.len = 0;
	pageUrlList.max = MAX_PAGE_ONE_SITE;

	imgUrlList.curNode=NULL;
	imgUrlList.len = 0;
	imgUrlList.max = MAX_IMG_ONE_SITE;

	return true;
}

jboolean urlRemPoolInit()
{
	firstUrlPool = malloc(sizeof(t_urlPool));
	if (firstUrlPool == NULL)
	{
		return false;
	}
	else
	{
		firstUrlPool->idleMemPtr = 0;
		firstUrlPool->next = NULL;

		LOGI("init firstUrlPool Success");
	}

	return true;
}

jboolean Java_com_UltimateImgSpider_SpiderService_jniUrlListInit(JNIEnv* env,
		jobject thiz)
{
	if(!urlListInit())
	{
		return false;
	}

	if(!urlRemPoolInit())
	{
		return false;
	}

	return true;
}

//添加URL 返回添加完成后的列表大小，返回0表示内存分配失败
jint Java_com_UltimateImgSpider_SpiderService_jniAddUrl(JNIEnv* env,
		jobject thiz, jstring jUrl, jint jHashCode, jint jType)
{
	int i;
	int ret = 0;
	urlList *curList =
			(jType == URL_TYPE_PAGE) ? (&pageUrlList) : (&imgUrlList);
	urlNode *node = &(curList->list[0]);

	const u8 *url = (*env)->GetStringUTFChars(env, jUrl, NULL);
	//LOGI("url:%s hashCode:%d type:%d", url, jHashCode, jType);

	for (i = 0; i < curList->len; i++)
	{
		node = &(curList->list[i]);
		if (node->hashCode == jHashCode)
		{
			if (strcmp(node->url, url) == 0)
			{
				break;
			}
		}
	}

	while (true)
	{
		if ((i == curList->len) && (curList->len < curList->max))
		{
			char *newUrl = urlMalloc(strlen(url) + 1);
			if (newUrl == NULL)
			{
				break;
			}
			else
			{
				strcpy(newUrl, url);

				node = &(curList->list[i]);
				node->url = newUrl;
				node->hashCode = jHashCode;
				node->state = URL_PENDING;
				curList->len++;
			}
		}

		ret = curList->len;
		break;
	}

	(*env)->ReleaseStringUTFChars(env, jUrl, url);

	return ret;
}

u32 urlSimilarity(const char *url1, const char *url2)
{
	u32 len1 = strlen(url1);
	u32 len2 = strlen(url2);
	u32 len = (len2 < len1) ? len2 : len1;
	u32 i;

	for (i = 0; i < len; i++)
	{
		if (url1[i] != url2[i])
		{
			break;
		}
	}

	return i;
}


jstring Java_com_UltimateImgSpider_SpiderService_jniFindNextUrlToLoad(
		JNIEnv* env, jobject thiz, jstring jPrevUrl, jint jType)
{
	int i;
	urlList *curList =
			(jType == URL_TYPE_PAGE) ? (&pageUrlList) : (&imgUrlList);

	urlNode **curNode = &(curList->curNode);
	bool findNewNode=false;
	urlNode *node;


	//LOGI("jniFindNextUrlToLoad jPrevUrl:%X", (int)jPrevUrl);

	if (jPrevUrl == NULL)
	{
		for (i = 0; i < curList->len; i++)
		{
			node = &(curList->list[i]);
			if (node->state == URL_PENDING)
			{
				*curNode = node;
				findNewNode=true;
				break;
			}
		}
	}
	else
	{
		bool scanComplete = true;
		int urlSim = 0;
		const u8 *prevUrl = (*env)->GetStringUTFChars(env, jPrevUrl, NULL);

		if(strcmp(prevUrl, (*curNode)->url)==0)
		{
			(*curNode)->state=URL_DOWNLOADED;
		}

		//LOGI("prevUrl:%s curList->len:%d", prevUrl, curList->len);

		for (i = 0; i < (curList->len); i++)
		{
			node = &(curList->list[i]);

			//LOGI("url %d:%s", i, node->url);
			if (node->state == URL_PENDING)
			{
				if (scanComplete)
				{
					scanComplete = false;
					urlSim = urlSimilarity(prevUrl, node->url);
					*curNode = node;
				}
				else
				{
					int curSim = urlSimilarity(prevUrl, node->url);

					if (curSim > urlSim)
					{
						urlSim = curSim;
						*curNode = node;
					}
				}

				findNewNode=true;
			}
		}

		(*env)->ReleaseStringUTFChars(env, jPrevUrl, prevUrl);
	}

	if (findNewNode)
	{
		//LOGI("curNode->url:%s", curNode->url);
		return (*env)->NewStringUTF(env, (*curNode)->url);
	}

	return (*env)->NewStringUTF(env, "");
}

void Java_com_UltimateImgSpider_SpiderService_jniClearAll(JNIEnv* env,
		jobject thiz)
{
	int i;

	pageUrlList.len = 0;
	imgUrlList.len = 0;
	free(pageUrlList.list);
	free(imgUrlList.list);

	if (firstUrlPool != NULL)
	{
		t_urlPool *urlPool = firstUrlPool;

		do
		{
			t_urlPool *next = urlPool->next;
			free(urlPool);
			urlPool = next;
		} while (urlPool != NULL );

		firstUrlPool = NULL;
	}
	LOGI("jniOnDestroy free all url");
}

