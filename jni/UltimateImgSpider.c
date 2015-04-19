#include <stdio.h>
#include <string.h>
#include <stdlib.h>
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


#define ASHM_EXIST	0x12345678
#pragma pack(1)
typedef struct
{
	u32 ashmStat;
	u8 data[4];
} t_ashmBlock;
#pragma pack()

typedef struct t_ashm
{
	char name[ASHM_NAME_SIZE+1];
	int size;
	int fd;
	t_ashmBlock *ashmem;
	struct t_ashm *next;
} t_ashmNode;


t_ashmNode *ashmemChainHead=NULL;
t_ashmNode *ashmemChainTail=NULL;


t_ashmNode *findAshmemByName(const char *name)
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
	const char *name = (*env)->GetStringUTFChars(env, jname, NULL);

	t_ashmNode *ashmNode=findAshmemByName(name);
	if(ashmNode!=NULL)
	{
		ashmNode->ashmem->ashmStat=ASHM_EXIST;
		fd=ashmNode->fd;
	}
	else
	{
		fd = ashmem_create_region(name, size+sizeof(u32));
		if (fd >= 0)
		{
			LOGI("create ashmem name:%s size:%d fd:%d success!", name, size, fd);

			t_ashmBlock *ashm = mmap(NULL, size, PROT_READ | PROT_WRITE,
			MAP_SHARED, fd, 0);
			if (ashm != NULL)
			{
				t_ashmNode *newAshmNode=malloc(sizeof(t_ashmNode));
				if(newAshmNode!=NULL)
				{
					ashm->ashmStat=0;
					newAshmNode->ashmem=ashm;
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


					LOGI("ashmem mmap %d to watchdog process success!", (u32 )ashm);

					if(strcmp(name, "ashmTest")==0)
					{
						for(i=0; i<8; i++)
						{
							ashm->data[i]=i;
						}
					}
				}
			}
		}
	}

	(*env)->ReleaseStringUTFChars(env, jname, name);

	return fd;
}

jobject SpiderServiceInstance=NULL;

jclass SpiderServiceClass=NULL;

jmethodID getAshmemFromWatchdogMID=NULL;

void* spiderGetAshmemFromWatchdog(JNIEnv* env, const char *name, int size)
{
	void *ashmem=NULL;

	LOGI("spiderGetAshmemFromWatchdog name:%s size:%d", name, size);

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
	t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, "ashmTest", 32);

	if(ashm!=NULL)
	{
		u8 i;
		for(i=0; i<8; i++)
		{
			LOGI("%d", ashm->data[i]);
		}
		if(ashm->ashmStat==ASHM_EXIST)
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
#define SIZE_PER_URLPOOL	(1024*1024-12)

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
	u16 state;
	u16 len;
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
	u32 urlPoolNum;
} t_spiderPara;


typedef struct memPool
{
	char mem[SIZE_PER_URLPOOL];
	u32 idleMemPtr;
	struct memPool* next;
} t_urlPool;
#pragma pack()

t_spiderPara *spiderPara=NULL;

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

#define URL_POOL_NAME_SIZE	20
char urlPoolName[URL_POOL_NAME_SIZE];
char *urlPoolIndexToName(u32 index)
{
	snprintf(urlPoolName, URL_POOL_NAME_SIZE, "urlPool_%d", index);
	LOGI("url pool name:%s", urlPoolName);
	return urlPoolName;
}

urlNode *urlNodeAllocFromPool(JNIEnv* env, u32 urlSize, urlNode *prevNode)
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
			t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(spiderPara->urlPoolNum), sizeof(t_urlPool));
			if(ashm!=NULL)
			{
				LOGI("init new urlPool Success");

				spiderPara->urlPoolNum++;

				urlPool->next=(t_urlPool*)(ashm->data);
				urlPool = (t_urlPool*)(ashm->data);

				urlPool->idleMemPtr = 0;
				urlPool->next = NULL;

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


jmethodID spiderReportProcessMID=NULL;
void spiderReportProcess(JNIEnv* env)
{

	if(spiderReportProcessMID==NULL)
	{
		SpiderServiceClass = (*env)->FindClass(env,
			"com/UltimateImgSpider/SpiderService");
		if (SpiderServiceClass != NULL)
		{
			spiderReportProcessMID = (*env)->GetMethodID(env, SpiderServiceClass, "recvProcess",
					"(IIII)V");
		}
	}

	if (spiderReportProcessMID != NULL)
	{
		(*env)->CallVoidMethod(env, SpiderServiceInstance, spiderReportProcessMID, spiderPara->imgUrlChain.len, spiderPara->imgUrlChain.processed, spiderPara->pageUrlChain.len, spiderPara->pageUrlChain.processed);
	}
}

jboolean spiderParaInit(JNIEnv* env)
{

	t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, "spiderPara", sizeof(t_spiderPara));

	if(ashm!=NULL)
	{
		spiderPara=(t_spiderPara*)(ashm->data);
		if(ashm->ashmStat!=ASHM_EXIST)
		{
			spiderPara->pageUrlChain.head.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlChain.tail.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlChain.curNode.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlChain.processed=0;
			spiderPara->pageUrlChain.len=0;

			spiderPara->imgUrlChain.head.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlChain.tail.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlChain.curNode.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlChain.processed=0;
			spiderPara->imgUrlChain.len=0;

			spiderPara->urlPoolNum=0;
		}
		else
		{
			spiderReportProcess(env);
		}
		return true;
	}

	return false;
}

jboolean urlPoolInit(JNIEnv* env)
{
	u32 i;

	if(spiderPara->urlPoolNum==0)
	{
		t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(0), sizeof(t_urlPool));
		if(ashm!=NULL)
		{
			firstUrlPool=(t_urlPool*)(ashm->data);
			firstUrlPool->idleMemPtr = 0;
			firstUrlPool->next = NULL;
			spiderPara->urlPoolNum=1;
			return true;
		}
	}
	else
	{
		t_urlPool *urlPool;
		for(i=0; i<spiderPara->urlPoolNum; i++)
		{
			t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(i), sizeof(t_urlPool));
			if(ashm==NULL)
			{
				break;
			}
			if(ashm->ashmStat!=ASHM_EXIST)
			{
				break;
			}

			if(i==0)
			{
				firstUrlPool=(t_urlPool*)(ashm->data);
				urlPool=firstUrlPool;
			}
			else
			{
				urlPool->next=(t_urlPool*)(ashm->data);
				urlPool=(t_urlPool*)(ashm->data);
			}
		}

		if(i==spiderPara->urlPoolNum)
		{
			return true;
		}
	}

	return false;
}

jboolean Java_com_UltimateImgSpider_SpiderService_jniSpiderInit(JNIEnv* env,
		jobject thiz)
{
	SpiderServiceInstance=thiz;

	if(!spiderParaInit(env))
	{
		return false;
	}

	if(!urlPoolInit(env))
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
			(jType == URL_TYPE_PAGE) ? (&(spiderPara->pageUrlChain)) : (&(spiderPara->imgUrlChain));
	const u8 *url = (*env)->GetStringUTFChars(env, jUrl, NULL);

	urlNode *node=nodeAddrRelativeToAbs(&(curChain->head));

	SpiderServiceInstance=thiz;

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
		u16 urlLen=strlen(url);
		urlNode *newNode = urlNodeAllocFromPool(env, urlLen+1 , nodeAddrRelativeToAbs(&(curChain->tail)));
		if (newNode != NULL)
		{
			//LOGI("newNode:%X", (u32)newNode);
			strcpy(newNode->url, url);
			newNode->para.hashCode = jHashCode;
			newNode->para.state = URL_PENDING;
			newNode->para.len=urlLen;

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


u16 urlSimilarity(const char *url1, u16 len1, const char *url2, u16 len2)
{
	u16 i;
	u16 len=(len1>len2)?len2:len1;
	u16 lenInWord=len/=sizeof(u32);
	const u32 *urlInWord1=(u32*)url1;
	const u32 *urlInWord2=(u32*)url2;

	for (i = 0; i<lenInWord; i++)
	{
		if (urlInWord1[i] != urlInWord2[i])
		{
			break;
		}
	}

	for(i*=sizeof(u32); i<len; i++)
	{
		if(url1[i]!=url2[i])
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
			(jType == URL_TYPE_PAGE) ? (&(spiderPara->pageUrlChain)) : (&(spiderPara->imgUrlChain));

	char *nextUrl="";

	urlNode *curNode = NULL;
	urlNode *node = nodeAddrRelativeToAbs(&(curChain->head));
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
					break;
				}

				node=gotoNextNode(node);
			}
		}
		else
		{
			bool scanComplete = true;
			u32 urlSim = 0;
			const char *prevUrl = (*env)->GetStringUTFChars(env, jPrevUrl, NULL);
			u16 prevUrlLen=strlen(prevUrl);

			if(((u32)prevUrl)&0x03!=0)
			{
				LOGI("URL NOT ALIGN!");
			}

			curNode = nodeAddrRelativeToAbs(&(curChain->curNode));
			if(curNode!=NULL)
			{
				if(strcmp(prevUrl, curNode->url)==0)
				{
					curNode->para.state=URL_DOWNLOADED;
					curChain->processed++;
				}

				curNode=NULL;
			}

			//LOGI("prevUrl:%s curChain->len:%d", prevUrl, curChain->len);
			for (i = 0; i < curChain->len; i++)
			{
				//LOGI("url %d:%s", i, node->url);
				if (node->para.state == URL_PENDING)
				{
					if (scanComplete)
					{
						scanComplete = false;
						urlSim = urlSimilarity(prevUrl, prevUrlLen, node->url, node->para.len);
						curNode = node;
					}
					else
					{
						u32 curSim = urlSimilarity(prevUrl, prevUrlLen, node->url, node->para.len);

						if (curSim > urlSim)
						{
							urlSim = curSim;
							curNode = node;
						}
					}
				}

				node=gotoNextNode(node);
			}

			(*env)->ReleaseStringUTFChars(env, jPrevUrl, prevUrl);
		}
	}


	if(curNode!=NULL)
	{
		nextUrl=curNode->url;
		nodeAddrAbsToRelative(curNode, &(curChain->curNode));
	}

	//LOGI("nextUrl:%s", nextUrl);
	return (*env)->NewStringUTF(env, nextUrl);
}


