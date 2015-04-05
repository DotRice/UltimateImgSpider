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
 * Spider需要新申请一段内存时，向看门狗进程发出命令，并提供共享内存段名称和大小。
 * 看门狗进程创建共享内存，并将其映射到自己的内存空间，然后向Spider进程返回共享内存的文件描述符。
 * Spider进程用这个文件描述符映射此段共享内存到自己的内存空间。
 */

#define ASHM_NAME_SIZE	32
#define ASHM_EXIST	0x12345678

int ashmem_create_region(const char *name, u32 size)
{
	int fd, fdWithOption;
	char buf[ASHM_NAME_SIZE];

	while (name && size)
	{
		fd = open(ASHMEM_NAME_DEF, O_RDWR);
		if (fd < 0)
		{
			break;
		}

		LOGI("ashmem open success %d", fd);

		strlcpy(buf, name, sizeof(buf));
		fdWithOption = ioctl(fd, ASHMEM_SET_NAME, buf);
		if (fdWithOption < 0)
		{
			close(fd);
			break;
		}
		fdWithOption = ioctl(fd, ASHMEM_SET_SIZE, size);
		if (fdWithOption < 0)
		{
			close(fd);
			break;
		}

		break;
	}

	return fd;
}


typedef struct t_ashm
{
	char name[ASHM_NAME_SIZE+1];
	int size;
	int fd;
	u32 *addr;
	struct t_ashm *next;
} t_ashmNode;


t_ashmNode *ashmemChainHead=NULL;
t_ashmNode *ashmemChainTail=NULL;


t_ashmNode *findAshmemByNameAndSize(const char *name, int size)
{
	t_ashmNode *ashmNode=ashmemChainHead;

	while(ashmNode!=NULL)
	{
		if(strcmp(ashmNode->name, name)==0)
		{
			return ashmNode;
		}

		ashmNode=ashmNode->next;
	}

	return NULL;
}

int Java_com_UltimateImgSpider_WatchdogService_jniGetAshmem(JNIEnv* env,
		jobject thiz, jstring jname, jint size)
{
	int i, fd=-1;
	s64 fdWithOption;
	u8* mAddr = NULL;
	const char *name = (*env)->GetStringUTFChars(env, jname, NULL);

	t_ashmNode *ashmNode=findAshmemByNameAndSize(name, size);
	if(ashmNode!=NULL)
	{
		*ashmNode->addr=ASHM_EXIST;
		fd=ashmNode->fd;
	}
	else
	{
		fd = ashmem_create_region(name, size+4);
		if (fd >= 0)
		{
			LOGI("create ashmem name:%s size:%d fd:%d success!", name, size, fd);

			mAddr = (u8*) mmap(NULL, size, PROT_READ | PROT_WRITE,
			MAP_SHARED, fd, 0);
			if (mAddr != NULL)
			{
				t_ashmNode *newAshmNode=malloc(sizeof(t_ashmNode));
				if(newAshmNode!=NULL)
				{
					*(u32*)mAddr=0;

					newAshmNode->addr=(u32*)mAddr;
					newAshmNode->fd=fd;
					strncpy(newAshmNode->name, name, ASHM_NAME_SIZE);
					newAshmNode->next=NULL;
					newAshmNode->size=size;

					if(ashmemChainHead==NULL)
					{
						ashmemChainHead=newAshmNode;
					}

					if(ashmemChainTail!=NULL)
					{
						ashmemChainTail->next=newAshmNode;
					}
					ashmemChainTail=newAshmNode;


					LOGI("ashmem mmap %d to watchdog process success!", (u32 )mAddr);

					if(strcmp(name, "ashmTest")==0)
					{
						for(i=0; i<8; i++)
						{
							mAddr[i+4]=i;
						}
					}
				}
			}
		}
	}

	(*env)->ReleaseStringUTFChars(env, jname, name);

	return fd;
}


jmethodID getAshmemFromWatchdogMID=NULL;
jclass SpiderServiceClass=NULL;
jobject SpiderServiceInstance=NULL;

void* spiderGetAshmemFromWatchdog(JNIEnv* env, const char *name, int size)
{
	void *ashmem=NULL;

	if(getAshmemFromWatchdogMID==NULL)
	{
		SpiderServiceClass = (*env)->FindClass(env,
			"com/UltimateImgSpider/SpiderService");
		if (SpiderServiceClass != NULL)
		{
			getAshmemFromWatchdogMID = (*env)->GetMethodID(env, SpiderServiceClass, "getAshmemFromWatchdog",
					"(Ljava/lang/String;I)I");
		}
	}

	if (getAshmemFromWatchdogMID != NULL)
	{
		jstring jname=(*env)->NewStringUTF(env, name);
		int fd=(*env)->CallIntMethod(env, SpiderServiceInstance, getAshmemFromWatchdogMID, jname, size);
		if(fd>=0)
		{
			ashmem=mmap(NULL, size, PROT_READ | PROT_WRITE,
						MAP_SHARED, fd, 0);
		}

	}

	return ashmem;
}




void ashmemTest(JNIEnv* env)
{
	u8 *ashm=spiderGetAshmemFromWatchdog(env, "ashmTest", 32);

	if(ashm!=NULL)
	{
		u8 i;
		for(i=0; i<8; i++)
		{
			LOGI("%d", ashm[i+4]);
		}
		if(*(u32*)ashm==ASHM_EXIST)
		{
			LOGI("ashmem already exist");
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

	ashmemTest(env);

	if((*env)->ExceptionOccurred(env)) {
	   return NULL;
	}

	(*env)->ReleaseStringUTFChars(env, jSrcStr, srcStr);
	return (*env)->NewStringUTF(env, "test jni !");
}

#define MAX_SIZE_PER_URL	4096
#define SIZE_PER_URLPOOL	(1024*1024-8)

enum URL_STATE
{
	URL_PENDING, URL_DOWNLOADED
};

#define URL_TYPE_PAGE	0
#define URL_TYPE_IMG	1

#define POOL_PTR_INVALID	0xFFFFFFFF

#pragma pack(1)
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
	urlNodeRelativeAddr head;
	urlNodeRelativeAddr tail;
	urlNodeRelativeAddr curNode;
	u32 processed;
	u32 len;
} urlChain;


typedef struct
{
	urlChain pageUrlChain;
	urlChain imgUrlChain;
} t_urlChains;


typedef struct memPool
{
	char mem[SIZE_PER_URLPOOL];
	u32 idleMemPtr;
	struct memPool* next;
} t_urlPool;
#pragma pack()

t_urlChains *urlChains=NULL;

t_urlPool *firstUrlPool = NULL;

void nodeAddrAbsToRelative(urlNode *node, urlNodeRelativeAddr *RelativeAddr)
{
	t_urlPool *pool=firstUrlPool;
	u32 i=0;

	while(pool!=NULL)
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
	}
}

urlNode *nodeAddrRelativeToAbs(urlNodeRelativeAddr *RelativeAddr)
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
			return (urlNode *)(((u32)pool)+RelativeAddr->offset);
		}
	}

	return NULL;
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
	}

	return node;
}

jboolean urlListInit()
{
	urlChains=malloc(sizeof(t_urlChains));
	if(urlChains!=NULL)
	{
		urlChains->pageUrlChain.head.poolPtr=POOL_PTR_INVALID;
		urlChains->pageUrlChain.tail.poolPtr=POOL_PTR_INVALID;
		urlChains->pageUrlChain.curNode.poolPtr=POOL_PTR_INVALID;
		urlChains->pageUrlChain.processed=0;
		urlChains->pageUrlChain.len=0;

		urlChains->imgUrlChain.head.poolPtr=POOL_PTR_INVALID;
		urlChains->imgUrlChain.tail.poolPtr=POOL_PTR_INVALID;
		urlChains->imgUrlChain.curNode.poolPtr=POOL_PTR_INVALID;
		urlChains->imgUrlChain.processed=0;
		urlChains->imgUrlChain.len=0;

		return true;
	}

	return false;
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

	if(!urlListInit())
	{
		return false;
	}

	return true;
}



//添加URL 返回列表大小
jint Java_com_UltimateImgSpider_SpiderService_jniAddUrl(JNIEnv* env,
		jobject thiz, jstring jUrl, jint jHashCode, jint jType)
{
	u8 UrlAlreadyInChain=false;
	urlChain *curChain =
			(jType == URL_TYPE_PAGE) ? (&(urlChains->pageUrlChain)) : (&(urlChains->imgUrlChain));
	const u8 *url = (*env)->GetStringUTFChars(env, jUrl, NULL);

	urlNode *node=nodeAddrRelativeToAbs(&(curChain->head));

	//LOGI("head:%X len:%d url:%s %d hashCode:%d type:%d",(u32)node, curChain->len, url, strlen(url), jHashCode, jType);

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
		urlNode *newNode = urlNodeAllocFromPool(strlen(url) + 1, nodeAddrRelativeToAbs(&(curChain->tail)));
		if (newNode != NULL)
		{

			strcpy(newNode->url, url);
			newNode->para.hashCode = jHashCode;
			newNode->para.state = URL_PENDING;

			if(curChain->head.poolPtr==POOL_PTR_INVALID)
			{
				nodeAddrAbsToRelative(newNode, &(curChain->head));
			}

			nodeAddrAbsToRelative(newNode, &(curChain->tail));
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
			(jType == URL_TYPE_PAGE) ? (&(urlChains->pageUrlChain)) : (&(urlChains->imgUrlChain));

	urlNode *curNode = nodeAddrRelativeToAbs(&(curChain->curNode));
	urlNode *node = nodeAddrRelativeToAbs(&(curChain->head));

	char *nextUrl="";

	//LOGI("jPrevUrl:%X head:%X", (u32)jPrevUrl, (u32)node);

	if(node!=NULL)
	{
		if (jPrevUrl == NULL)
		{
			for (i = 0; i < curChain->len; i++)
			{
				if (node->para.state == URL_PENDING)
				{
					curNode = node;
					nextUrl=node->url;
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

			if(curNode!=NULL)
			{
				if(strcmp(prevUrl, curNode->url)==0)
				{
					curNode->para.state=URL_DOWNLOADED;
					curChain->processed++;
				}
			}

			//LOGI("prevUrl:%s curChain->len:%d", prevUrl, curChain->len);
			for (i = 0; i < curChain->len; i++)
			{
				if (node->para.state == URL_PENDING)
				{
					//LOGI("url %d:%s", i, node->url);
					if (scanComplete)
					{
						scanComplete = false;
						urlSim = urlSimilarity(prevUrl, node->url);
						curNode = node;
						nextUrl=node->url;
					}
					else
					{
						u32 curSim = urlSimilarity(prevUrl, node->url);

						if (curSim > urlSim)
						{
							urlSim = curSim;
							curNode = node;
							nextUrl=node->url;
						}
					}
				}

				node=gotoNextNode(node);
			}

			(*env)->ReleaseStringUTFChars(env, jPrevUrl, prevUrl);
		}
	}

	//LOGI("nextUrl:%s", nextUrl);
	nodeAddrAbsToRelative(curNode, &(curChain->curNode));
	return (*env)->NewStringUTF(env, nextUrl);
}

void Java_com_UltimateImgSpider_SpiderService_jniClearAll(JNIEnv* env,
		jobject thiz)
{
	if(urlChains!=NULL)
	{
		free(urlChains);
	}

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

