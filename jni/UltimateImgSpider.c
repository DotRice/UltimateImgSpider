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

#define MAX_SIZE_PER_URL	4096

enum URL_STATE
{
	URL_PENDING, URL_DOWNLOADED
};

#define URL_TYPE_PAGE	0
#define URL_TYPE_IMG	1

#define POOL_PTR_INVALID	0xFFFFFFFF

typedef struct
{
	u32 poolPtr;
	u32 offset;
} urlNodeRelativeAddr;

typedef struct
{
	int hashCode;
	u32 state;
	urlNodeRelativeAddr nextNodeAddr;
} nodePara;

typedef struct
{
	nodePara para;
	char url[MAX_SIZE_PER_URL];
} urlNode;

typedef struct
{
	urlNode *head;
	urlNode *tail;
	urlNode *curNode;
	u32 processed;
	u32 len;
} urlChain;

urlChain pageUrlChain;
urlChain imgUrlChain;


#define SIZE_PER_URLPOOL	(1024*1024-16)
typedef struct memPool
{
	char mem[SIZE_PER_URLPOOL];
	u32 idleMemPtr;
	struct memPool* next;
} t_urlPool;

t_urlPool *firstUrlPool = NULL;

void nodeAddrAbsToRelative(urlNode *node, urlNodeRelativeAddr *RelativeAddr)
{
	t_urlPool *pool=firstUrlPool;
	u32 i=0;

	if(pool!=NULL)
	{
		do
		{
			s64 ofs=(u32)node-(u32)pool;
			if(ofs>=0&&ofs<sizeof(t_urlPool))
			{
				RelativeAddr->poolPtr=i;
				RelativeAddr->offset=ofs;
				return;
			}
			pool=pool->next;
			i++;
		}while(pool->next!=NULL);
	}
}

void nodeAddrRelativeToAbs(urlNodeRelativeAddr *RelativeAddr, urlNode **node)
{
	t_urlPool *pool=firstUrlPool;
	u32 i=0;

	if(pool!=NULL)
	{
		while(pool->next!=NULL&&i<RelativeAddr->poolPtr)
		{
			pool=pool->next;
			i++;
		}

		if(i==RelativeAddr->poolPtr&&RelativeAddr->offset<sizeof(t_urlPool))
		{
			(*node)=(urlNode *)(((u32)pool)+RelativeAddr->offset);
		}
	}
}


t_urlPool *findUrlPoolByIndex(u32 index)
{
	t_urlPool *pool=firstUrlPool;
	u32 i=0;

	if(pool!=NULL)
	{
		while(i<index&&pool->next!=NULL)
		{
			pool=pool->next;
			i++;
		}
	}

	return (i<index)?NULL:pool;
}

urlNode *gotoNextNode(urlNode *curNode)
{
	t_urlPool *pool=findUrlPoolByIndex(curNode->para.nextNodeAddr.poolPtr);

	if(pool!=NULL)
	{
		return (urlNode*)(pool->mem+curNode->para.nextNodeAddr.offset);
	}

	return NULL;
}

urlNode *urlNodeAllocFromPool(u32 urlSize, urlNode *prevNode)
{
	urlNode *node=NULL;
	t_urlPool *urlPool = firstUrlPool;
	u32 poolIndex=0;
	u32 offset=0;
	u32 size=((urlSize+sizeof(nodePara))+3)&0xFFFFFFFC;


	if ((size > MAX_SIZE_PER_URL) || (urlPool == NULL ))
	{
		return NULL;
	}

	while (true)
	{
		if ((urlPool->idleMemPtr + size) <= SIZE_PER_URLPOOL)
		{
			offset = urlPool->idleMemPtr;
			urlPool->idleMemPtr += size;

			node=(urlNode*)(urlPool->mem + offset);
			break;
		}

		poolIndex++;
		if (urlPool->next != NULL)
		{
			urlPool = urlPool->next;
		}
		else
		{
			urlPool->next = malloc(sizeof(t_urlPool));
			if (urlPool->next != NULL)
			{
				urlPool = urlPool->next;

				urlPool->idleMemPtr = 0;
				urlPool->next = NULL;

				LOGI("init new urlPool Success");
			}
			else
			{
				break;
			}
		}
	}

	if(node!=NULL)
	{
		if(prevNode!=NULL)
		{
			prevNode->para.nextNodeAddr.poolPtr=poolIndex;
			prevNode->para.nextNodeAddr.offset=offset;
		}
		node->para.nextNodeAddr.poolPtr=POOL_PTR_INVALID;

		//LOGI("urlNodeAlloc %d %d", (u32)node-(u32)firstUrlPool, firstUrlPool->idleMemPtr);
	}

	return node;
}

void urlListInit()
{
	pageUrlChain.head=NULL;
	pageUrlChain.tail=NULL;
	pageUrlChain.curNode=NULL;
	pageUrlChain.processed=NULL;
	pageUrlChain.len=0;

	imgUrlChain.head=NULL;
	imgUrlChain.tail=NULL;
	imgUrlChain.curNode=NULL;
	imgUrlChain.processed=NULL;
	imgUrlChain.len=0;
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
	if(!urlRemPoolInit())
	{
		return false;
	}

	urlListInit();

	return true;
}



//添加URL 返回列表大小
jint Java_com_UltimateImgSpider_SpiderService_jniAddUrl(JNIEnv* env,
		jobject thiz, jstring jUrl, jint jHashCode, jint jType)
{
	u8 UrlAlreadyInChain=false;
	urlChain *curChain =
			(jType == URL_TYPE_PAGE) ? (&pageUrlChain) : (&imgUrlChain);
	const u8 *url = (*env)->GetStringUTFChars(env, jUrl, NULL);
	//LOGI("url:%s %d hashCode:%d type:%d", url, strlen(url), jHashCode, jType);

	urlNode *node=curChain->head;
	if(node!=NULL)
	{
		u32 i;
		for(i=0; i<curChain->len; i++)
		{
			if (node->para.hashCode == jHashCode)
			{
				if (strcmp(node->url, url) == 0)
				{
					UrlAlreadyInChain=true;
					break;
				}
			}

			node=gotoNextNode(node);
		}
	}

	if (!UrlAlreadyInChain)
	{
		urlNode *newNode = urlNodeAllocFromPool(strlen(url) + 1, curChain->tail);
		if (newNode != NULL)
		{
			//LOGI("newNode %d %d", (u32)newNode-(u32)firstUrlPool, firstUrlPool->idleMemPtr);

			strcpy(newNode->url, url);
			newNode->para.hashCode = jHashCode;
			newNode->para.state = URL_PENDING;

			if(curChain->head==NULL)
			{
				curChain->head=newNode;
			}
			curChain->tail=newNode;
			curChain->len++;

		}
	}

	(*env)->ReleaseStringUTFChars(env, jUrl, url);

	return curChain->len;
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
	urlChain *curChain =
			(jType == URL_TYPE_PAGE) ? (&pageUrlChain) : (&imgUrlChain);

	urlNode **curNode = &(curChain->curNode);
	bool findNewNode=false;
	urlNode *node;


	//LOGI("jniFindNextUrlToLoad jPrevUrl:%X", (int)jPrevUrl);

	if (jPrevUrl == NULL)
	{
		node = curChain->head;
		for (i = 0; i < curChain->len; i++)
		{
			if (node->para.state == URL_PENDING)
			{
				*curNode = node;
				findNewNode=true;
				break;
			}

			node=gotoNextNode(node);
		}
	}
	else
	{
		bool scanComplete = true;
		u32 urlSim = 0;
		const u8 *prevUrl = (*env)->GetStringUTFChars(env, jPrevUrl, NULL);

		if(*curNode!=NULL)
		{
			if(strcmp(prevUrl, (*curNode)->url)==0)
			{
				(*curNode)->para.state=URL_DOWNLOADED;
				curChain->processed++;
			}
		}

		//LOGI("prevUrl:%s curChain->len:%d", prevUrl, curChain->len);

		node = curChain->head;
		for (i = 0; i < curChain->len; i++)
		{
			//LOGI("url %d:%s", i, node->url);
			if (node->para.state == URL_PENDING)
			{
				if (scanComplete)
				{
					scanComplete = false;
					urlSim = urlSimilarity(prevUrl, node->url);
					*curNode = node;
				}
				else
				{
					u32 curSim = urlSimilarity(prevUrl, node->url);

					if (curSim > urlSim)
					{
						urlSim = curSim;
						*curNode = node;
					}
				}

				findNewNode=true;
			}

			node=gotoNextNode(node);
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

	urlListInit();

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

