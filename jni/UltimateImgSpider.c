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

int Java_com_gk969_UltimateImgSpider_WatchdogService_jniGetAshmem(JNIEnv* env,
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
			"com/gk969/UltimateImgSpider/SpiderService");
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






jstring Java_com_gk969_UltimateImgSpider_SpiderService_stringFromJNI(JNIEnv* env,
		jobject thiz, jstring jSrcStr)
{
	int i;
	const u8 *srcStr = (*env)->GetStringUTFChars(env, jSrcStr, NULL);
	LOGI("stringFromJNI %s", srcStr);

	SpiderServiceInstance=thiz;

	//ashmemTest(env);

	if((*env)->ExceptionOccurred(env)) {
	   return NULL;
	}

	(*env)->ReleaseStringUTFChars(env, jSrcStr, srcStr);
	return (*env)->NewStringUTF(env, "test jni !");
}


#define MAX_RAM_TOTAL_POOL	1024*1024*1024

#define MAX_SIZE_PER_URL	4095
#define SIZE_PER_URLPOOL	(1024*1024-12)

#define URL_TYPE_PAGE	0
#define URL_TYPE_IMG	1

#define RBT_RED     0
#define RBT_BLACK   1

#define POOL_PTR_INVALID	0xFFFFFFFF

#pragma pack(1)
typedef struct
{
	u32 poolPtr;
	u32 offset;
} urlNodeRelativeAddr;

typedef struct
{
	u64 md5_64;
	u8 pad;
	u8 color;
	u16 len;
	
	
	
	urlNodeRelativeAddr containerPage;
	
	urlNodeRelativeAddr nextNodeAddr;
	urlNodeRelativeAddr prevNodeAddr;

	urlNodeRelativeAddr left;
	urlNodeRelativeAddr right;
	urlNodeRelativeAddr parent;
} nodePara;

typedef struct
{
	nodePara para;
	char url[MAX_SIZE_PER_URL+1];
} urlNode;

typedef struct
{
	urlNodeRelativeAddr head;
	urlNodeRelativeAddr tail;

	urlNodeRelativeAddr root;

	urlNodeRelativeAddr curNode;
	u32 processed;
	u32 len;
	u32 height;
} urlTree;


typedef struct
{
	urlTree pageUrlTree;
	urlTree imgUrlTree;
	u32 urlPoolNum;
} t_spiderPara;


typedef struct memPool
{
	u8 mem[SIZE_PER_URLPOOL];
	u32 idleMemPtr;
} t_urlPool;

#pragma pack()

t_spiderPara *spiderPara=NULL;

#define TOTAL_POOL (MAX_RAM_TOTAL_POOL/sizeof(t_urlPool))
t_urlPool *urlPools[TOTAL_POOL];

u32 downloadingImgNum=0;

char nextImgUrlWithContainerBuf[MAX_SIZE_PER_URL*2+2];


void nodeAddrAbsToRelative(urlNode *node, urlNodeRelativeAddr *RelativeAddr)
{
	if(node!=NULL)
	{
		u32 i;
		for(i=0; i<spiderPara->urlPoolNum; i++)
		{
			s64 ofs=(u32)node-(u32)(urlPools[i]);
			if(ofs>=0&&ofs<sizeof(t_urlPool))
			{
				RelativeAddr->poolPtr=i;
				RelativeAddr->offset=ofs;
				return;
			}
		}
	}
	else
	{
		RelativeAddr->poolPtr=POOL_PTR_INVALID;
	}
}

urlNode *nodeAddrRelativeToAbs(urlNodeRelativeAddr *relativeAddr)
{
	if(relativeAddr->poolPtr < spiderPara->urlPoolNum)
	{
		t_urlPool *pool=urlPools[relativeAddr->poolPtr];

		if(relativeAddr->offset < (pool->idleMemPtr-sizeof(nodePara)))
		{
			return (urlNode*)(pool->mem+relativeAddr->offset);
		}
	}

	return NULL;
}


void urlTreeLeftRotate(urlTree *tree, urlNode *upNode)
{
	urlNodeRelativeAddr downNodeAddr=upNode->para.right;
	urlNode *downNode=nodeAddrRelativeToAbs(&downNodeAddr);

	urlNodeRelativeAddr upNodeAddr;
	nodeAddrAbsToRelative(upNode, &upNodeAddr);

	upNode->para.right=downNode->para.left;
	if(downNode->para.left.poolPtr!=POOL_PTR_INVALID)
	{
		nodeAddrRelativeToAbs(&(downNode->para.left))->para.parent=upNodeAddr;
	}

	downNode->para.parent=upNode->para.parent;
	if(upNode->para.parent.poolPtr==POOL_PTR_INVALID)
	{
		tree->root=downNodeAddr;
	}
	else if(upNode==nodeAddrRelativeToAbs(&(nodeAddrRelativeToAbs(&(upNode->para.parent))->para.left)))
	{
		nodeAddrRelativeToAbs(&(upNode->para.parent))->para.left=downNodeAddr;
	}
	else
	{
		nodeAddrRelativeToAbs(&(upNode->para.parent))->para.right=downNodeAddr;
	}

	downNode->para.left=upNodeAddr;
	upNode->para.parent=downNodeAddr;
}


void urlTreeRightRotate(urlTree *tree, urlNode *upNode)
{
	urlNodeRelativeAddr downNodeAddr=upNode->para.left;
	urlNode *downNode=nodeAddrRelativeToAbs(&downNodeAddr);

	urlNodeRelativeAddr upNodeAddr;
	nodeAddrAbsToRelative(upNode, &upNodeAddr);

	upNode->para.left=downNode->para.right;
	if(downNode->para.right.poolPtr!=POOL_PTR_INVALID)
	{
		nodeAddrRelativeToAbs(&(downNode->para.right))->para.parent=upNodeAddr;
	}

	downNode->para.parent=upNode->para.parent;

	if(upNode->para.parent.poolPtr==POOL_PTR_INVALID)
	{
		tree->root=downNodeAddr;
	}
	else if(upNode==nodeAddrRelativeToAbs(&(nodeAddrRelativeToAbs(&(upNode->para.parent))->para.left)))
	{
		nodeAddrRelativeToAbs(&(upNode->para.parent))->para.left=downNodeAddr;
	}
	else
	{
		nodeAddrRelativeToAbs(&(upNode->para.parent))->para.right=downNodeAddr;
	}

	downNode->para.right=upNodeAddr;
	upNode->para.parent=downNodeAddr;
}


#define URL_POOL_NAME_SIZE	20
char urlPoolName[URL_POOL_NAME_SIZE];
char *urlPoolIndexToName(u32 index)
{
	snprintf(urlPoolName, URL_POOL_NAME_SIZE, "urlPool_%d", index);
	LOGI("url pool name:%s", urlPoolName);
	return urlPoolName;
}

urlNode *urlNodeAllocFromPool(JNIEnv* env, u32 urlSize, urlNodeRelativeAddr *direction)
{
	urlNode *node=NULL;
	t_urlPool *urlPool;
	u32 poolIndex;
	u32 offset=0;
	u32 size=((urlSize+sizeof(nodePara))+3)&0xFFFFFFFC;


	if ((size > MAX_SIZE_PER_URL) || (spiderPara->urlPoolNum == 0 ))
	{
		return NULL;
	}

	for(poolIndex=0; poolIndex<spiderPara->urlPoolNum; poolIndex++)
	{
		urlPool=urlPools[poolIndex];
		if ((urlPool->idleMemPtr + size) <= SIZE_PER_URLPOOL)
		{
			break;
		}
	}

	if(poolIndex==spiderPara->urlPoolNum)
	{
		urlPool=NULL;
		if(spiderPara->urlPoolNum<TOTAL_POOL)
		{
			t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(spiderPara->urlPoolNum), sizeof(t_urlPool));
			if(ashm!=NULL)
			{
				LOGI("init new urlPool Success");

				urlPool = (t_urlPool*)(ashm->data);
				urlPool->idleMemPtr = 0;
				urlPools[spiderPara->urlPoolNum]=urlPool;

				spiderPara->urlPoolNum++;
			}
		}
	}


	if(urlPool!=NULL)
	{
		offset = urlPool->idleMemPtr;

		//LOGI("alloc at %d", offset);

		urlPool->idleMemPtr += size;

		node=(urlNode*)(urlPool->mem + offset);

		if(direction!=NULL)
		{
			direction->poolPtr=poolIndex;
			direction->offset=offset;
		}
	}

	return node;
}


jboolean spiderParaInit(JNIEnv* env)
{

	t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, "spiderPara", sizeof(t_spiderPara));

	if(ashm!=NULL)
	{
		spiderPara=(t_spiderPara*)(ashm->data);
		if(ashm->ashmStat!=ASHM_EXIST)
		{
			spiderPara->pageUrlTree.head.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlTree.tail.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlTree.root.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlTree.curNode.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlTree.processed=0;
			spiderPara->pageUrlTree.len=0;
			spiderPara->pageUrlTree.height=0;

			spiderPara->imgUrlTree.head.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlTree.tail.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlTree.root.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlTree.curNode.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlTree.processed=0;
			spiderPara->imgUrlTree.len=0;
			spiderPara->imgUrlTree.height=0;

			spiderPara->urlPoolNum=0;
		}
		
		return true;
	}

	return false;
}

jboolean urlPoolInit(JNIEnv* env)
{
	if(spiderPara->urlPoolNum==0)
	{
		t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(0), sizeof(t_urlPool));
		if(ashm!=NULL)
		{
			urlPools[0]=(t_urlPool*)(ashm->data);
			urlPools[0]->idleMemPtr = 0;
			spiderPara->urlPoolNum=1;
			return true;
		}
	}
	else
	{
		u32 i;
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

			urlPools[i]=(t_urlPool*)(ashm->data);
		}

		if(i==spiderPara->urlPoolNum)
		{
			return true;
		}
	}

	return false;
}

jboolean Java_com_gk969_UltimateImgSpider_SpiderService_jniSpiderInit(JNIEnv* env,
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



void urlTreeTraversal(urlTree *tree)
{
	u32 i;
	urlNode *node = nodeAddrRelativeToAbs(&(tree->head));
	for (i = 0; i < tree->len; i++)
	{
		urlNode *parent=nodeAddrRelativeToAbs(&(node->para.parent));
		u32 parentMd5=(parent==NULL)?0:(parent->para.md5_64>>32);
		urlNode *left=nodeAddrRelativeToAbs(&(node->para.left));
		u32 leftMd5=(left==NULL)?0:(left->para.md5_64>>32);
		urlNode *right=nodeAddrRelativeToAbs(&(node->para.right));
		u32 rightMd5=(right==NULL)?0:(right->para.md5_64>>32);
		LOGI("Chain %08X %c p:%08X l:%08X r:%08X", (u32)(node->para.md5_64>>32), (node->para.color==RBT_BLACK)?'B':'R', parentMd5, leftMd5, rightMd5);

		node=nodeAddrRelativeToAbs(&(node->para.nextNodeAddr));
	}
}


void rbUrlTreeFixup(urlTree *tree, urlNode *node)
{
    urlNode *parent=nodeAddrRelativeToAbs(&(node->para.parent));
	urlNode *grandParent;
	urlNode *uncle;
	if(parent!=NULL)
	{
        grandParent=nodeAddrRelativeToAbs(&(parent->para.parent));
	}
	
	while(parent!=NULL)
    {
		if(parent->para.color!=RBT_RED)
		{
			break;
		}
		
        if(parent==nodeAddrRelativeToAbs(&(grandParent->para.left)))
        {
            uncle=nodeAddrRelativeToAbs(&(grandParent->para.right));
			
			while(true)
			{
				if(uncle!=NULL)
				{
					if(uncle->para.color==RBT_RED)
					{
						parent->para.color=RBT_BLACK;
						uncle->para.color=RBT_BLACK;
						grandParent->para.color=RBT_RED;
						node=grandParent;
						
						parent=nodeAddrRelativeToAbs(&(node->para.parent));
						if(parent!=NULL)
						{
							grandParent=nodeAddrRelativeToAbs(&(parent->para.parent));
						}
						
						break;
					}
				}
				
				if(node==nodeAddrRelativeToAbs(&(parent->para.right)))
				{
					node=parent;
					urlTreeLeftRotate(tree, node);
				}
				
				parent=nodeAddrRelativeToAbs(&(node->para.parent));
				parent->para.color=RBT_BLACK;
				
				grandParent=nodeAddrRelativeToAbs(&(parent->para.parent));
				grandParent->para.color=RBT_RED;
				
				urlTreeRightRotate(tree, grandParent);
				break;
			}
        }
        else
        {
            uncle=nodeAddrRelativeToAbs(&(grandParent->para.left));
			
			while(true)
			{
				if(uncle!=NULL)
				{
					if(uncle->para.color==RBT_RED)
					{
						parent->para.color=RBT_BLACK;
						uncle->para.color=RBT_BLACK;
						grandParent->para.color=RBT_RED;
						node=grandParent;
						
						parent=nodeAddrRelativeToAbs(&(node->para.parent));
						if(parent!=NULL)
						{
							grandParent=nodeAddrRelativeToAbs(&(parent->para.parent));
						}
						
						break;
					}
				}
				
				if(node==nodeAddrRelativeToAbs(&(parent->para.left)))
				{
					node=parent;
					urlTreeRightRotate(tree, node);
					
				}
				
				parent=nodeAddrRelativeToAbs(&(node->para.parent));
				parent->para.color=RBT_BLACK;

				grandParent=nodeAddrRelativeToAbs(&(parent->para.parent));
				grandParent->para.color=RBT_RED;

				urlTreeLeftRotate(tree, grandParent);
				break;
			}
			
        }
        
    }
    
    nodeAddrRelativeToAbs(&(tree->root))->para.color=RBT_BLACK;
}

urlNode *findUrlNodeByMd5(urlTree *tree, u64 md5)
{
	urlNodeRelativeAddr *nextNodeAddr=&(tree->root);
	urlNode *node=nodeAddrRelativeToAbs(nextNodeAddr);

	while(node!=NULL)
	{
		if(md5>node->para.md5_64)
		{
			nextNodeAddr=&(node->para.right);
		}
		else if(md5<node->para.md5_64)
		{
			nextNodeAddr=&(node->para.left);
		}
		else
		{
			return node;
		}

		node=nodeAddrRelativeToAbs(nextNodeAddr);
	}

	return NULL;
}

void urlTreeInsert(JNIEnv* env, urlTree *tree, const u8 *newUrl, u64 newMd5_64)
{
	urlNodeRelativeAddr *nextNodeAddr=&(tree->root);
	urlNode *node=nodeAddrRelativeToAbs(nextNodeAddr);
	urlNode *parent=NULL;
	
	u16 urlLen=strlen(newUrl);
	u32 height=0;

	while(node!=NULL)
	{
		if(newMd5_64>node->para.md5_64)
		{
			nextNodeAddr=&(node->para.right);
		}
		else if(newMd5_64<node->para.md5_64)
		{
			nextNodeAddr=&(node->para.left);
		}
		else
		{
			return;
		}
		
		//LOGI("go %d %d", nextNodeAddr->poolPtr, nextNodeAddr->offset);

		parent=node;
		node=nodeAddrRelativeToAbs(nextNodeAddr);
		
		height++;
	}
	
	if(height>tree->height)
	{
		tree->height=height;
	}
	
	node=urlNodeAllocFromPool(env, urlLen+1 , nextNodeAddr);
	if(node!=NULL)
	{
		urlNodeRelativeAddr newNodeAddr=*nextNodeAddr;

		strcpy(node->url, newUrl);
		node->para.md5_64 = newMd5_64;
		node->para.len=urlLen;

		node->para.left.poolPtr=POOL_PTR_INVALID;
		node->para.right.poolPtr=POOL_PTR_INVALID;

		nodeAddrAbsToRelative(parent, &(node->para.parent));

		node->para.containerPage=spiderPara->pageUrlTree.curNode;
		
		node->para.nextNodeAddr.poolPtr=POOL_PTR_INVALID;
		node->para.prevNodeAddr=tree->tail;
		if(tree->tail.poolPtr!=POOL_PTR_INVALID)
		{
			nodeAddrRelativeToAbs(&(tree->tail))->para.nextNodeAddr=newNodeAddr;
		}
		tree->tail=newNodeAddr;
		if(tree->head.poolPtr==POOL_PTR_INVALID)
		{
			tree->head=newNodeAddr;
		}
		tree->len++;
		
		if(parent==NULL)
		{
			node->para.color=RBT_BLACK;
		}
		else
		{
			node->para.color=RBT_RED;

			if(nodeAddrRelativeToAbs(&(parent->para.parent))!=NULL)
			{
				rbUrlTreeFixup(tree, node);
			}
		}

		

		/*
		LOGI("new %d %d", newNodeAddr.poolPtr, newNodeAddr.offset);

		//if(tree->len==10)
		{
			urlTreeTraversal(tree);
		}
		*/

	}
}

enum
{
	TOTAL=0,
    PROCESSED,
    HEIGHT,
    PAYLOAD
};


void Java_com_gk969_UltimateImgSpider_SpiderService_jniAddUrl(JNIEnv* env,
		jobject thiz, jstring jUrl, jbyteArray jMd5, jint jType, jintArray jParam)
{
	u8 UrlAlreadyInTree=false;
	urlTree *curTree =
			(jType == URL_TYPE_PAGE) ? (&(spiderPara->pageUrlTree)) : (&(spiderPara->imgUrlTree));
	const u8 *url = (*env)->GetStringUTFChars(env, jUrl, NULL);

	u8 *md5=(*env)->GetByteArrayElements(env, jMd5, NULL);
	u64 md5_64;
	memcpy((u8*)&md5_64, md5+4, 8);

	SpiderServiceInstance=thiz;

	//if(curTree->len<20)
	{
		//LOGI("len:%d %d md5_64:%08X type:%d url:%s",curTree->len, strlen(url), (u32)(md5_64>>32), jType, url);

		urlTreeInsert(env, curTree, url, md5_64);

		/*
		urlNode *tail=nodeAddrRelativeToAbs(&(curTree->tail));
		urlNode *head=nodeAddrRelativeToAbs(&(curTree->head));
		LOGI("tail:%08X head:%08X", tail, head);
		*/
	}
	
	int *param=(*env)->GetIntArrayElements(env, jParam, NULL);
	param[TOTAL]=curTree->len;
	param[HEIGHT]=curTree->height;
	(*env)->ReleaseByteArrayElements(env, jParam, param, 0);
	

	(*env)->ReleaseStringUTFChars(env, jUrl, url);
	(*env)->ReleaseByteArrayElements(env, jMd5, md5, 0);
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

void deleteUrlNodeFromList(urlTree *curTree, urlNode *curNode)
{
	curTree->processed++;

	urlNode *prev=nodeAddrRelativeToAbs(&(curNode->para.prevNodeAddr));
	if(prev==NULL)
	{
		curTree->head=curNode->para.nextNodeAddr;
	}
	else
	{
		prev->para.nextNodeAddr=curNode->para.nextNodeAddr;
	}

	urlNode *next=nodeAddrRelativeToAbs(&(curNode->para.nextNodeAddr));
	if(next==NULL)
	{
		curTree->tail=curNode->para.prevNodeAddr;
	}
	else
	{
		next->para.prevNodeAddr=curNode->para.prevNodeAddr;
	}
}

#define SEARCH_STEP_MAX	5000

jstring Java_com_gk969_UltimateImgSpider_SpiderService_jniFindNextUrlToLoad(
		JNIEnv* env, jobject thiz, jstring jCurUrl, jbyteArray jMd5, jint jType, jintArray jParam)
{
	urlTree *curTree =
			(jType == URL_TYPE_PAGE) ? (&(spiderPara->pageUrlTree)) : (&(spiderPara->imgUrlTree));

	char *nextUrl=NULL;
	
	int i;

	int *param=(*env)->GetIntArrayElements(env, jParam, NULL);
	//LOGI("jniFindNextUrlToLoad type:%d", jType);
	
	urlNode *curNode=NULL;
	urlNode *nextNode=NULL;
	if(jType == URL_TYPE_PAGE)
	{
		curNode=nodeAddrRelativeToAbs(&(curTree->curNode));
		if(curNode==NULL || jCurUrl==NULL)
		{
			nextNode=nodeAddrRelativeToAbs(&(curTree->head));
		}
		else
		{
			int urlSim = -1;
			const char *curUrl = (*env)->GetStringUTFChars(env, jCurUrl, NULL);
			u16 prevUrlLen=strlen(curUrl);


			//LOGI("curUrl:%s curTree->len:%d", curUrl, curTree->len);
			//当前url已经被下载，从未下载url链表中删除
			if(strcmp(curUrl, curNode->url)==0)
			{
				deleteUrlNodeFromList(curTree, curNode);
			}
			
			urlNode *node=curNode;
			for (i = 0; i < SEARCH_STEP_MAX; i++)
			{
				//LOGI("next %d:%s", i, node->url);
				node=nodeAddrRelativeToAbs(&(node->para.nextNodeAddr));
				if(node==NULL)
				{
					break;
				}
				else
				{
					int curSim = urlSimilarity(curUrl, prevUrlLen, node->url, node->para.len);
					if (curSim > urlSim)
					{
						urlSim = curSim;
						nextNode = node;
					}
				}
			}


			node=curNode;
			for (i = 0; i < SEARCH_STEP_MAX; i++)
			{
				//LOGI("prev %d:%s", i, node->url);
				node=nodeAddrRelativeToAbs(&(node->para.prevNodeAddr));
				if(node==NULL)
				{
					break;
				}
				else
				{
					int curSim = urlSimilarity(curUrl, prevUrlLen, node->url, node->para.len);
					if (curSim > urlSim)
					{
						urlSim = curSim;
						nextNode = node;
					}
				}
			}
			
			(*env)->ReleaseStringUTFChars(env, jCurUrl, curUrl);
		}
		
		if(nextNode!=NULL)
		{
			nextUrl=nextNode->url;
			nodeAddrAbsToRelative(nextNode, &(curTree->curNode));
		}
	}
	else
	{
		if(jMd5!=NULL)
		{
			u8 *md5=(*env)->GetByteArrayElements(env, jMd5, NULL);
			u64 md5_64;
			memcpy((u8*)&md5_64, md5+4, 8);
			(*env)->ReleaseByteArrayElements(env, jMd5, md5, 0);
			
			//LOGI("md5:%08X", (u32)(md5_64>>32));

			curNode=findUrlNodeByMd5(curTree, md5_64);

			//LOGI("prev url:%s", curNode->url);
			deleteUrlNodeFromList(curTree, curNode);
			
			downloadingImgNum--;
		}
		
		//LOGI("downloadingImgNum:%d", downloadingImgNum);

		nextNode=nodeAddrRelativeToAbs(&(curTree->head));
		for(i=0; i<10; i++)
		{
			if(nextNode==NULL)
			{
				break;
			}
			//LOGI("img%d:%s", i, nextNode->url);

			nextNode=nodeAddrRelativeToAbs(&(nextNode->para.nextNodeAddr));
		}


		nextNode=nodeAddrRelativeToAbs(&(curTree->head));
		for(i=0; i<downloadingImgNum; i++)
		{
			if(nextNode==NULL)
			{
				break;
			}

			nextNode=nodeAddrRelativeToAbs(&(nextNode->para.nextNodeAddr));
		}

		if(nextNode!=NULL)
		{
			downloadingImgNum++;
			
			nextUrl=nextImgUrlWithContainerBuf;

			sprintf(nextUrl, "%s %s", nextNode->url, nodeAddrRelativeToAbs(&(nextNode->para.containerPage))->url);
		}
		param[PAYLOAD]=downloadingImgNum;
	}


	param[PROCESSED]=curTree->processed;

	(*env)->ReleaseByteArrayElements(env, jParam, param, NULL);
	

	//LOGI("nextUrl:%s", nextUrl);
	return (*env)->NewStringUTF(env, nextUrl);
}


