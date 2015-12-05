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

#define ASHM_NAME_SIZE  32

int ashmem_create_region(const char *name, u32 size)
{
    int fd, fdWithOption;
    char buf[ASHM_NAME_SIZE];

    while(name && size)
    {
        fd = open(ASHMEM_NAME_DEF, O_RDWR);

        if(fd < 0)
        {
            break;
        }

        LOGI("ashmem open success %d", fd);

        strlcpy(buf, name, sizeof(buf));
        fdWithOption = ioctl(fd, ASHMEM_SET_NAME, buf);

        if(fdWithOption < 0)
        {
            close(fd);
            break;
        }

        fdWithOption = ioctl(fd, ASHMEM_SET_SIZE, size);

        if(fdWithOption < 0)
        {
            close(fd);
            break;
        }

        break;
    }

    return fd;
}


#define ASHM_EXIST  0x12345678
#pragma pack(1)
typedef struct
{
    u32 ashmStat;
    u8 data[4];
} t_ashmBlock;

typedef struct
{
    char name[ASHM_NAME_SIZE];
    int size;
    u32 ashmStat;
} t_ashmParaStore;

#pragma pack()

typedef struct t_ashm
{
    char name[ASHM_NAME_SIZE];
    int size;
    int fd;
    t_ashmBlock *ashmem;
    struct t_ashm *next;
} t_ashmNode;


t_ashmNode *ashmemChainHead = NULL;
t_ashmNode *ashmemChainTail = NULL;


t_ashmNode *findAshmemByName(const char *name)
{
    t_ashmNode *ashmNode = ashmemChainHead;

    while(ashmNode != NULL)
    {
        if(strcmp(ashmNode->name, name) == 0)
        {
            return ashmNode;
        }

        ashmNode = ashmNode->next;
    }

    return NULL;
}

int createNewAshmem(const char *name, int size, u8 **addr)
{
    int fd = ashmem_create_region(name, size + sizeof(u32));

    if(fd >= 0)
    {
        LOGI("create ashmem name:%s size:%d fd:%d success!", name, size, fd);

        t_ashmBlock *ashm = mmap(NULL, size, PROT_READ | PROT_WRITE,
                                 MAP_SHARED, fd, 0);

        if(ashm != NULL)
        {
            t_ashmNode *newAshmNode = malloc(sizeof(t_ashmNode));

            if(newAshmNode != NULL)
            {
                ashm->ashmStat = 0;

                if(addr != NULL)
                {
                    *addr = ashm->data;
                }

                newAshmNode->ashmem = ashm;
                newAshmNode->fd = fd;
                strncpy(newAshmNode->name, name, ASHM_NAME_SIZE);
                newAshmNode->next = NULL;
                newAshmNode->size = size;

                if(ashmemChainHead == NULL)
                {
                    ashmemChainHead = newAshmNode;
                }

                if(ashmemChainTail != NULL)
                {
                    ashmemChainTail->next = newAshmNode;
                }

                ashmemChainTail = newAshmNode;


                LOGI("ashmem mmap %d to watchdog process success!", (u32)ashm);

                if(strcmp(name, "ashmTest") == 0)
                {
                    int i;

                    for(i = 0; i < 8; i++)
                    {
                        ashm->data[i] = i;
                    }
                }
            }
        }
    }

    return fd;
}

void Java_com_gk969_UltimateImgSpider_WatchdogService_jniRestoreProjectData(JNIEnv *env,
        jobject thiz, jstring jDataFileFullPath)
{
    const u8 *dataFileFullPath = (*env)->GetStringUTFChars(env, jDataFileFullPath, NULL);

    LOGI("jniRestoreProjectData path:%s", dataFileFullPath);

    FILE *dataFile = fopen(dataFileFullPath, "r");

    if(dataFile != NULL)
    {
        t_ashmParaStore ashmParaStore;

        while(true)
        {
            if(fread(&ashmParaStore, sizeof(t_ashmParaStore), 1, dataFile) != 1)
            {
                LOGI("fread ashmParaStore error");
                break;
            }

            u8 *data;
            int fd = createNewAshmem(ashmParaStore.name, ashmParaStore.size, &data);

            if(fd < 0)
            {
                LOGI("createNewAshmem error");
                break;
            }

            if(fread(data, ashmParaStore.size, 1, dataFile) != 1)
            {
                LOGI("fread data error");
                break;
            }

            LOGI("Restore AshmNode Name:%s Size:%d Success", ashmParaStore.name, ashmParaStore.size);
        }
    }

    (*env)->ReleaseStringUTFChars(env, jDataFileFullPath, dataFileFullPath);
}

void Java_com_gk969_UltimateImgSpider_WatchdogService_jniStoreProjectData(JNIEnv *env,
        jobject thiz, jstring jDataFileFullPath)
{
    const u8 *dataFileFullPath = (*env)->GetStringUTFChars(env, jDataFileFullPath, NULL);

    LOGI("jniStoreProjectData path:%s", dataFileFullPath);

    FILE *dataFile = fopen(dataFileFullPath, "wb");

    if(dataFile != NULL)
    {
        t_ashmNode *ashmNode;

        for(ashmNode = ashmemChainHead; ashmNode != NULL; ashmNode = ashmNode->next)
        {
            t_ashmParaStore ashmParaStore;
            strncpy(ashmParaStore.name, ashmNode->name, ASHM_NAME_SIZE);
            ashmParaStore.size = ashmNode->size;
            ashmParaStore.ashmStat = ashmNode->ashmem->ashmStat;

            fwrite(&ashmParaStore, sizeof(t_ashmParaStore), 1, dataFile);
            fwrite(ashmNode->ashmem->data, ashmNode->size, 1, dataFile);
        }

        fclose(dataFile);
    }

    (*env)->ReleaseStringUTFChars(env, jDataFileFullPath, dataFileFullPath);
}

int Java_com_gk969_UltimateImgSpider_WatchdogService_jniGetAshmem(JNIEnv *env,
        jobject thiz, jstring jname, jint size)
{
    int i, fd = -1;
    s64 fdWithOption;
    const char *name = (*env)->GetStringUTFChars(env, jname, NULL);

    LOGI("WatchdogService_jniGetAshmem %s", name);
    t_ashmNode *ashmNode = findAshmemByName(name);

    if(ashmNode != NULL)
    {
        LOGI("ashmNode %s Exist", name);
        ashmNode->ashmem->ashmStat = ASHM_EXIST;
        fd = ashmNode->fd;
    }
    else
    {
        fd = createNewAshmem(name, size, NULL);
    }

    (*env)->ReleaseStringUTFChars(env, jname, name);

    return fd;
}

jobject AshmAllocObjectInstance = NULL;

jclass SpiderServiceClass = NULL;

jmethodID getAshmemFromWatchdogMID = NULL;

void *spiderGetAshmemFromWatchdog(JNIEnv *env, const char *name, int size)
{
    void *ashmem = NULL;

    LOGI("spiderGetAshmemFromWatchdog name:%s size:%d", name, size);

    if(getAshmemFromWatchdogMID == NULL)
    {
        SpiderServiceClass = (*env)->FindClass(env, "com/gk969/UltimateImgSpider/SpiderService");

        if(SpiderServiceClass != NULL)
        {
            getAshmemFromWatchdogMID = (*env)->GetMethodID(env, SpiderServiceClass,
                                        "getAshmemFromWatchdog", "(Ljava/lang/String;I)I");
        }
    }

    if(getAshmemFromWatchdogMID != NULL)
    {
        jstring jname = (*env)->NewStringUTF(env, name);


        LOGI("spiderGetAshmemFromWatchdog call method");
        LOGI("AshmAllocObjectInstance %08X %08X", AshmAllocObjectInstance, getAshmemFromWatchdogMID);
        int fd = (*env)->CallIntMethod(env, AshmAllocObjectInstance, getAshmemFromWatchdogMID, jname, size);

        if(fd >= 0)
        {
            LOGI("spiderGetAshmemFromWatchdog mmap");
            ashmem = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        }

    }

    return ashmem;
}




void ashmemTest(JNIEnv *env)
{
    t_ashmBlock *ashm = spiderGetAshmemFromWatchdog(env, "ashmTest", 32);

    if(ashm != NULL)
    {
        u8 i;

        for(i = 0; i < 8; i++)
        {
            LOGI("%d", ashm->data[i]);
        }

        if(ashm->ashmStat == ASHM_EXIST)
        {
            LOGI("ashmem already exist");
        }
    }
}


void fileTest()
{
    FILE *testFile;

    testFile = fopen("/mnt/sdcard/UltimateImgSpider/download/test.txt", "a");
    fprintf(testFile, "test ");
    fclose(testFile);

    char buf[100];
    testFile = fopen("/mnt/sdcard/UltimateImgSpider/download/test.txt", "r");
    fread(buf, 99, 1, testFile);
    fclose(testFile);

    LOGI("test file:%s", buf);
}



jstring Java_com_gk969_UltimateImgSpider_SpiderService_stringFromJNI(JNIEnv *env,
        jobject thiz, jstring jSrcStr)
{
    int i;
    const u8 *srcStr = (*env)->GetStringUTFChars(env, jSrcStr, NULL);
    LOGI("stringFromJNI %s", srcStr);

    //ashmemTest(env);

    //fileTest();

    if((*env)->ExceptionOccurred(env))
    {
        return NULL;
    }

    (*env)->ReleaseStringUTFChars(env, jSrcStr, srcStr);
    return (*env)->NewStringUTF(env, "test jni !");
}

#define MAX_SIZE_PER_URL    4095
#define URL_TYPE_PAGE   0
#define URL_TYPE_IMG    1

#define RBT_RED     0
#define RBT_BLACK   1

//最大可用内存池总大小
#define MAX_RAM_TOTAL_POOL  1024*1024*1024
//每个内存池大小
#define SIZE_PER_URLPOOL    (1024*1024-12)


/*
相对地址位映射
PTR:内存池序号
OFFSET:内存池内偏移量
31 30 29 28 27 ... 4 3 2 1 0
PTR            |      OFFSET
OFFSET低位，PTR高位。
*/
//内存池内偏移量在相对地址中占用的位数，必须能够包含SIZE_PER_URLPOOL
#define POOL_OFFSET_BIT     20
#define POOL_OFFSET_BITS    (((u32)1<<POOL_OFFSET_BIT)-1)
//内存池序号在相对地址中占用的位数
#define POOL_PTR_BIT        (sizeof(u32)-POOL_OFFSET_BITS)
#define POOL_PTR_BITS       ((((u32)1<<POOL_PTR_BIT)-1)<<POOL_OFFSET_BIT)

#define RelativeAddr u32
#define GET_POOL_OFFSET(addr)           ((addr)&POOL_OFFSET_BITS)
#define SET_POOL_OFFSET(addr,offset)    addr&=~POOL_OFFSET_BITS;\
                                        addr|=offset&POOL_OFFSET_BITS;

#define GET_POOL_PTR(addr)              (((addr)&POOL_PTR_BITS)>>POOL_OFFSET_BIT)
#define SET_POOL_PTR(addr,ptr)          addr&=~POOL_PTR_BITS;\
                                        addr|=(ptr<<POOL_OFFSET_BIT)&POOL_PTR_BITS;

#define NEW_RELATIVE_ADDR(ptr,offset)   ((((u32)ptr)<<POOL_OFFSET_BIT)|offset)


#define POOL_PTR_INVALID    (((u32)1<<POOL_PTR_BIT)-1)


enum
{
    PARAM_TOTAL = 0,
    PARAM_PROCESSED,
    PARAM_HEIGHT,
    PARAM_PAYLOAD,
    PARAM_DOWNLOAD
};



#pragma pack(1)
typedef struct
{
    u64 md5_64;
    u8 pad;
    u8 color;
    u16 len;

    RelativeAddr containerPage;
    RelativeAddr title;

    RelativeAddr nextToLoad;
    RelativeAddr prevToLoad;

    RelativeAddr left;
    RelativeAddr right;
    RelativeAddr parent;
} nodePara;

typedef struct
{
    nodePara para;
    char url[MAX_SIZE_PER_URL];
} urlNode;

typedef struct
{
    RelativeAddr head;
    RelativeAddr tail;

    RelativeAddr root;

    RelativeAddr curNode;
    u32 processed;
    u32 len;
    u32 height;
} urlTree;

typedef struct
{
    RelativeAddr imgUrl;
    RelativeAddr pageUrl;

    RelativeAddr prev;
    RelativeAddr next;
} t_storageImgInfo;

typedef struct
{
    RelativeAddr head;
    RelativeAddr tail;

    u32 num;
} t_storageImgInfoList;

typedef struct
{
    urlTree pageUrlTree;
    urlTree imgUrlTree;
    t_storageImgInfoList storageImgList;
    u32 urlPoolNum;
} t_spiderPara;


typedef struct memPool
{
    u8 mem[SIZE_PER_URLPOOL];
    u32 idleMemPtr;
} t_urlPool;


#pragma pack()

t_spiderPara *spiderPara = NULL;

#define TOTAL_POOL (MAX_RAM_TOTAL_POOL/sizeof(t_urlPool))
t_urlPool *urlPools[TOTAL_POOL];

u32 downloadingImgNum = 0;

char nextImgUrlWithContainerBuf[MAX_SIZE_PER_URL * 2 + 2 + sizeof(u32) * 2 + 2];

RelativeAddr nodeAddrAbsToRelative(urlNode *node)
{
    RelativeAddr relativeAddr;

    if(node != NULL)
    {
        u32 i;

        for(i = 0; i < spiderPara->urlPoolNum; i++)
        {
            s64 ofs = (u32)node - (u32)(urlPools[i]);

            if(ofs >= 0 && ofs < sizeof(t_urlPool))
            {
                relativeAddr = NEW_RELATIVE_ADDR(i, ofs);
                break;
            }
        }
    }
    else
    {
        relativeAddr = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
    }

    return relativeAddr;
}

void *addrRelativeToAbs(RelativeAddr relativeAddr)
{
    u32 poolPtr = GET_POOL_PTR(relativeAddr);

    if(poolPtr < spiderPara->urlPoolNum)
    {
        t_urlPool *pool = urlPools[poolPtr];

        u32 offset = GET_POOL_OFFSET(relativeAddr);

        //LOGI("addrRelativeToAbs %08X offset %08X pool->idleMemPtr %08X", relativeAddr, offset, pool->idleMemPtr);

        if(offset < pool->idleMemPtr)
        {
            return (void *)(pool->mem + offset);
        }
    }

    return NULL;
}

#define nodeAddrRelativeToAbs(relativeAddr) ((urlNode *)(addrRelativeToAbs(relativeAddr)))
#define infoAddrRelativeToAbs(relativeAddr) ((t_storageImgInfo *)(addrRelativeToAbs(relativeAddr)))


void urlTreeLeftRotate(urlTree *tree, urlNode *upNode)
{
    RelativeAddr downNodeAddr = upNode->para.right;
    urlNode *downNode = nodeAddrRelativeToAbs(downNodeAddr);
    urlNode *left;
    urlNode *parent;

    RelativeAddr upNodeAddr = nodeAddrAbsToRelative(upNode);

    upNode->para.right = downNode->para.left;
    left = nodeAddrRelativeToAbs(downNode->para.left);

    if(left != NULL)
    {
        left->para.parent = upNodeAddr;
    }

    downNode->para.parent = upNode->para.parent;
    parent = nodeAddrRelativeToAbs(upNode->para.parent);

    if(parent == NULL)
    {
        tree->root = downNodeAddr;
    }
    else if(upNode == nodeAddrRelativeToAbs(parent->para.left))
    {
        parent->para.left = downNodeAddr;
    }
    else
    {
        parent->para.right = downNodeAddr;
    }

    downNode->para.left = upNodeAddr;
    upNode->para.parent = downNodeAddr;
}


void urlTreeRightRotate(urlTree *tree, urlNode *upNode)
{
    RelativeAddr downNodeAddr = upNode->para.left;
    urlNode *downNode = nodeAddrRelativeToAbs(downNodeAddr);
    urlNode *right;
    urlNode *parent;

    RelativeAddr upNodeAddr = nodeAddrAbsToRelative(upNode);

    upNode->para.left = downNode->para.right;
    right = nodeAddrRelativeToAbs(downNode->para.right);

    if(right != NULL)
    {
        right->para.parent = upNodeAddr;
    }

    downNode->para.parent = upNode->para.parent;
    parent = nodeAddrRelativeToAbs(upNode->para.parent);

    if(parent == NULL)
    {
        tree->root = downNodeAddr;
    }
    else if(upNode == nodeAddrRelativeToAbs(parent->para.left))
    {
        parent->para.left = downNodeAddr;
    }
    else
    {
        parent->para.right = downNodeAddr;
    }

    downNode->para.right = upNodeAddr;
    upNode->para.parent = downNodeAddr;
}


#define URL_POOL_NAME_SIZE  20
char urlPoolName[URL_POOL_NAME_SIZE];
char *urlPoolIndexToName(u32 index)
{
    snprintf(urlPoolName, URL_POOL_NAME_SIZE, "urlPool_%d", index);
    LOGI("url pool name:%s", urlPoolName);
    return urlPoolName;
}

void *mallocFromPool(JNIEnv *env, u32 size, RelativeAddr *direction)
{
    void *node = NULL;
    t_urlPool *urlPool;
    u32 poolIndex;
    u32 offset = 0;

    if((size > MAX_SIZE_PER_URL) || (spiderPara->urlPoolNum == 0))
    {
        return NULL;
    }

    for(poolIndex = 0; poolIndex < spiderPara->urlPoolNum; poolIndex++)
    {
        urlPool = urlPools[poolIndex];

        if((urlPool->idleMemPtr + size) <= SIZE_PER_URLPOOL)
        {
            break;
        }
    }

    if(poolIndex == spiderPara->urlPoolNum)
    {
        urlPool = NULL;

        if(spiderPara->urlPoolNum < TOTAL_POOL)
        {
            t_ashmBlock *ashm = spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(spiderPara->urlPoolNum), sizeof(t_urlPool));

            if(ashm != NULL)
            {
                LOGI("init new urlPool Success");

                urlPool = (t_urlPool *)(ashm->data);
                urlPool->idleMemPtr = 0;
                urlPools[spiderPara->urlPoolNum] = urlPool;

                spiderPara->urlPoolNum++;
            }
        }
    }


    if(urlPool != NULL)
    {
        offset = urlPool->idleMemPtr;

        //LOGI("alloc at %d", offset);

        urlPool->idleMemPtr += size;

        node = (void *)(urlPool->mem + offset);

        if(direction != NULL)
        {
            *direction = NEW_RELATIVE_ADDR(poolIndex, offset);
        }
    }

    return node;
}


jboolean spiderParaInit(JNIEnv *env, int *imgParam, int *pageParam)
{

    t_ashmBlock *ashm = spiderGetAshmemFromWatchdog(env, "spiderPara", sizeof(t_spiderPara));

    if(ashm != NULL)
    {
        spiderPara = (t_spiderPara *)(ashm->data);

        if(ashm->ashmStat != ASHM_EXIST)
        {
            spiderPara->pageUrlTree.head = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->pageUrlTree.tail = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->pageUrlTree.root = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->pageUrlTree.curNode = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->pageUrlTree.processed = 0;
            spiderPara->pageUrlTree.len = 0;
            spiderPara->pageUrlTree.height = 0;

            spiderPara->imgUrlTree.head = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->imgUrlTree.tail = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->imgUrlTree.root = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->imgUrlTree.curNode = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->imgUrlTree.processed = 0;
            spiderPara->imgUrlTree.len = 0;
            spiderPara->imgUrlTree.height = 0;

            spiderPara->urlPoolNum = 0;

            spiderPara->storageImgList.head = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->storageImgList.tail = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
            spiderPara->storageImgList.num = 0;
        }

        imgParam[PARAM_TOTAL] = spiderPara->imgUrlTree.len;
        imgParam[PARAM_PROCESSED] = spiderPara->imgUrlTree.processed;
        imgParam[PARAM_HEIGHT] = spiderPara->imgUrlTree.height;
        imgParam[PARAM_DOWNLOAD] = spiderPara->storageImgList.num;

        pageParam[PARAM_TOTAL] = spiderPara->pageUrlTree.len;
        pageParam[PARAM_PROCESSED] = spiderPara->pageUrlTree.processed;
        pageParam[PARAM_HEIGHT] = spiderPara->pageUrlTree.height;

        return true;
    }

    return false;
}

jboolean urlPoolInit(JNIEnv *env)
{
    if(spiderPara->urlPoolNum == 0)
    {
        t_ashmBlock *ashm = spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(0), sizeof(t_urlPool));

        if(ashm != NULL)
        {
            urlPools[0] = (t_urlPool *)(ashm->data);
            urlPools[0]->idleMemPtr = 0;
            spiderPara->urlPoolNum = 1;
            return true;
        }
    }
    else
    {
        u32 i;
        t_urlPool *urlPool;

        for(i = 0; i < spiderPara->urlPoolNum; i++)
        {
            t_ashmBlock *ashm = spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(i), sizeof(t_urlPool));

            if(ashm == NULL)
            {
                break;
            }

            if(ashm->ashmStat != ASHM_EXIST)
            {
                break;
            }

            urlPools[i] = (t_urlPool *)(ashm->data);
        }

        if(i == spiderPara->urlPoolNum)
        {
            return true;
        }
    }

    return false;
}

jboolean Java_com_gk969_UltimateImgSpider_SpiderService_jniSpiderInit(JNIEnv *env,
        jobject thiz, jintArray jImgParam, jintArray jPageParam)
{
    AshmAllocObjectInstance = thiz;

    int *imgParam = (*env)->GetIntArrayElements(env, jImgParam, NULL);
    int *pageParam = (*env)->GetIntArrayElements(env, jPageParam, NULL);

    if(!spiderParaInit(env, imgParam, pageParam))
    {
        return false;
    }

    (*env)->ReleaseIntArrayElements(env, jImgParam, imgParam, 0);
    (*env)->ReleaseIntArrayElements(env, jPageParam, pageParam, 0);

    if(!urlPoolInit(env))
    {
        return false;
    }

    return true;
}



void urlTreeTraversal(urlTree *tree)
{
    u32 i;
    urlNode *node = nodeAddrRelativeToAbs(tree->head);

    for(i = 0; i < tree->len; i++)
    {
        urlNode *parent = nodeAddrRelativeToAbs(node->para.parent);
        u32 parentMd5 = (parent == NULL) ? 0 : (parent->para.md5_64 >> 32);
        urlNode *left = nodeAddrRelativeToAbs(node->para.left);
        u32 leftMd5 = (left == NULL) ? 0 : (left->para.md5_64 >> 32);
        urlNode *right = nodeAddrRelativeToAbs(node->para.right);
        u32 rightMd5 = (right == NULL) ? 0 : (right->para.md5_64 >> 32);

        LOGI("Chain %08X %c p:%08X l:%08X r:%08X", (u32)(node->para.md5_64 >> 32), (node->para.color == RBT_BLACK) ? 'B' : 'R', parentMd5, leftMd5, rightMd5);
        //LOGI("Chain %08X %c p:%08X l:%08X r:%08X", (u32)(node->para.md5_64>>32), (node->para.color==RBT_BLACK)?'B':'R', node->para.parent, node->para.left, node->para.right);

        node = nodeAddrRelativeToAbs(node->para.nextToLoad);
    }
}


void rbUrlTreeFixup(urlTree *tree, urlNode *node)
{
    urlNode *parent = nodeAddrRelativeToAbs(node->para.parent);
    urlNode *grandParent;
    urlNode *uncle;

    if(parent != NULL)
    {
        grandParent = nodeAddrRelativeToAbs(parent->para.parent);
    }

    while(parent != NULL)
    {
        if(parent->para.color != RBT_RED)
        {
            break;
        }

        if(parent == nodeAddrRelativeToAbs(grandParent->para.left))
        {
            uncle = nodeAddrRelativeToAbs(grandParent->para.right);

            while(true)
            {
                if(uncle != NULL)
                {
                    if(uncle->para.color == RBT_RED)
                    {
                        parent->para.color = RBT_BLACK;
                        uncle->para.color = RBT_BLACK;
                        grandParent->para.color = RBT_RED;
                        node = grandParent;

                        parent = nodeAddrRelativeToAbs(node->para.parent);

                        if(parent != NULL)
                        {
                            grandParent = nodeAddrRelativeToAbs(parent->para.parent);
                        }

                        break;
                    }
                }

                if(node == nodeAddrRelativeToAbs(parent->para.right))
                {
                    node = parent;
                    urlTreeLeftRotate(tree, node);
                }

                parent = nodeAddrRelativeToAbs(node->para.parent);
                parent->para.color = RBT_BLACK;

                grandParent = nodeAddrRelativeToAbs(parent->para.parent);
                grandParent->para.color = RBT_RED;

                urlTreeRightRotate(tree, grandParent);
                break;
            }
        }
        else
        {
            uncle = nodeAddrRelativeToAbs(grandParent->para.left);

            while(true)
            {
                if(uncle != NULL)
                {
                    if(uncle->para.color == RBT_RED)
                    {
                        parent->para.color = RBT_BLACK;
                        uncle->para.color = RBT_BLACK;
                        grandParent->para.color = RBT_RED;
                        node = grandParent;

                        parent = nodeAddrRelativeToAbs(node->para.parent);

                        if(parent != NULL)
                        {
                            grandParent = nodeAddrRelativeToAbs(parent->para.parent);
                        }

                        break;
                    }
                }

                if(node == nodeAddrRelativeToAbs(parent->para.left))
                {
                    node = parent;
                    urlTreeRightRotate(tree, node);

                }

                parent = nodeAddrRelativeToAbs(node->para.parent);
                parent->para.color = RBT_BLACK;

                grandParent = nodeAddrRelativeToAbs(parent->para.parent);
                grandParent->para.color = RBT_RED;

                urlTreeLeftRotate(tree, grandParent);
                break;
            }

        }

    }

    nodeAddrRelativeToAbs(tree->root)->para.color = RBT_BLACK;
}

urlNode *findUrlNodeByMd5(urlTree *tree, u64 md5)
{
    RelativeAddr nextNodeAddr = tree->root;
    urlNode *node = nodeAddrRelativeToAbs(nextNodeAddr);

    while(node != NULL)
    {
        if(md5 > node->para.md5_64)
        {
            nextNodeAddr = node->para.right;
        }
        else if(md5 < node->para.md5_64)
        {
            nextNodeAddr = node->para.left;
        }
        else
        {
            return node;
        }

        node = nodeAddrRelativeToAbs(nextNodeAddr);
    }

    return NULL;
}

void urlTreeInsert(JNIEnv *env, urlTree *tree, const u8 *newUrl, u64 newMd5_64)
{
    RelativeAddr *nextNodeAddr = &(tree->root);
    urlNode *node = nodeAddrRelativeToAbs(*nextNodeAddr);
    urlNode *parent = NULL;

    u16 urlLen = strlen(newUrl);
    u32 height = 0;

    /*
    LOGI("urlTreeInsert %08X", (u32)(newMd5_64>>32));

    if(node!=NULL)
        LOGI("root %08X", (u32)(node->para.md5_64>>32));
    */
    while(node != NULL)
    {
        //LOGI("path node %08X", (u32)(node->para.md5_64>>32));

        if(newMd5_64 > node->para.md5_64)
        {
            nextNodeAddr = &(node->para.right);
        }
        else if(newMd5_64 < node->para.md5_64)
        {
            nextNodeAddr = &(node->para.left);
        }
        else
        {
            return;
        }

        parent = node;
        node = nodeAddrRelativeToAbs(*nextNodeAddr);

        height++;
    }

    if(height > tree->height)
    {
        tree->height = height;
    }

    u32 urlNodeSize = ((urlLen + 1 + sizeof(nodePara)) + 3) & 0xFFFFFFFC;
    node = mallocFromPool(env, urlNodeSize, nextNodeAddr);

    if(node != NULL)
    {
        RelativeAddr newNodeAddr = *nextNodeAddr;
        urlNode *tail;

        strcpy(node->url, newUrl);
        node->para.md5_64 = newMd5_64;
        node->para.len = urlLen;

        node->para.left = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
        node->para.right = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);

        node->para.parent = nodeAddrAbsToRelative(parent);

        node->para.containerPage = spiderPara->pageUrlTree.curNode;
        node->para.title = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);

        node->para.nextToLoad = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);
        node->para.prevToLoad = tree->tail;
        tail = nodeAddrRelativeToAbs(tree->tail);

        if(tail != NULL)
        {
            tail->para.nextToLoad = newNodeAddr;
        }

        tree->tail = newNodeAddr;

        if(nodeAddrRelativeToAbs(tree->head) == NULL)
        {
            tree->head = newNodeAddr;
        }

        tree->len++;

        if(parent == NULL)
        {
            node->para.color = RBT_BLACK;
        }
        else
        {
            node->para.color = RBT_RED;

            if(nodeAddrRelativeToAbs(parent->para.parent) != NULL)
            {
                //LOGI("Before Fixup");
                //urlTreeTraversal(tree);
                rbUrlTreeFixup(tree, node);
            }
        }

        //LOGI("new %d %d", GET_POOL_PTR(newNodeAddr), GET_POOL_OFFSET(newNodeAddr));
        //urlTreeTraversal(tree);

    }
}


void Java_com_gk969_UltimateImgSpider_SpiderService_jniAddUrl(JNIEnv *env,
        jobject thiz, jstring jUrl, jbyteArray jMd5, jint jType, jintArray jParam)
{
    urlTree *curTree =
        (jType == URL_TYPE_PAGE) ? (&(spiderPara->pageUrlTree)) : (&(spiderPara->imgUrlTree));
    const u8 *url = (*env)->GetStringUTFChars(env, jUrl, NULL);

    AshmAllocObjectInstance=thiz;

    u8 *md5 = (*env)->GetByteArrayElements(env, jMd5, NULL);
    u64 md5_64;
    memcpy((u8 *)&md5_64, md5 + 4, 8);

    //LOGI("jniAddUrl %s", url);

    //if(curTree->len<20)
    {
        //LOGI("len:%d %d md5_64:%08X type:%d url:%s",curTree->len, strlen(url), (u32)(md5_64>>32), jType, url);

        urlTreeInsert(env, curTree, url, md5_64);

        /*
        urlNode *tail=nodeAddrRelativeToAbs(curTree->tail);
        urlNode *head=nodeAddrRelativeToAbs(curTree->head);
        LOGI("tail:%08X head:%08X", tail, head);
        */
    }

    int *param = (*env)->GetIntArrayElements(env, jParam, NULL);
    param[PARAM_TOTAL] = curTree->len;
    param[PARAM_HEIGHT] = curTree->height;
    (*env)->ReleaseIntArrayElements(env, jParam, param, 0);


    (*env)->ReleaseStringUTFChars(env, jUrl, url);
    (*env)->ReleaseByteArrayElements(env, jMd5, md5, 0);
}


u16 urlSimilarity(const char *url1, u16 len1, const char *url2, u16 len2)
{
    u16 i;
    u16 len = (len1 > len2) ? len2 : len1;
    u16 lenInWord = len /= sizeof(u32);
    const u32 *urlInWord1 = (u32 *)url1;
    const u32 *urlInWord2 = (u32 *)url2;

    for(i = 0; i < lenInWord; i++)
    {
        if(urlInWord1[i] != urlInWord2[i])
        {
            break;
        }
    }

    for(i *= sizeof(u32); i < len; i++)
    {
        if(url1[i] != url2[i])
        {
            break;
        }
    }

    return i;
}

void deleteUrlNodeFromList(urlTree *curTree, urlNode *curNode)
{
    curTree->processed++;

    urlNode *prev = nodeAddrRelativeToAbs(curNode->para.prevToLoad);

    if(prev == NULL)
    {
        curTree->head = curNode->para.nextToLoad;
    }
    else
    {
        prev->para.nextToLoad = curNode->para.nextToLoad;
    }

    urlNode *next = nodeAddrRelativeToAbs(curNode->para.nextToLoad);

    if(next == NULL)
    {
        curTree->tail = curNode->para.prevToLoad;
    }
    else
    {
        next->para.prevToLoad = curNode->para.prevToLoad;
    }
}


void Java_com_gk969_UltimateImgSpider_SpiderService_jniRecvPageTitle(
        JNIEnv *env, jobject thiz, jstring jCurPageTitle)
{
    urlNode *curNode = nodeAddrRelativeToAbs(spiderPara->pageUrlTree.curNode);

    //LOGI("jniRecvPageTitle");

    AshmAllocObjectInstance=thiz;

    //保存页面标题
    if(jCurPageTitle!=NULL)
    {
        const char *title = (*env)->GetStringUTFChars(env, jCurPageTitle, NULL);
        u32 titleStorageSize=(strlen(title)+1+3) & 0xFFFFFFFC;
        RelativeAddr titleAddr;
        char *titleBuf=mallocFromPool(env, titleStorageSize, &titleAddr);

        if(titleBuf!=NULL)
        {
            curNode->para.title=titleAddr;
            stpcpy(titleBuf, title);
        }

        (*env)->ReleaseStringUTFChars(env, jCurPageTitle, title);
    }
}


#define SEARCH_STEP_MAX 5000

jstring Java_com_gk969_UltimateImgSpider_SpiderService_jniFindNextPageUrl(
        JNIEnv *env, jobject thiz, jintArray jPageParam)
{
    urlTree *curTree = &(spiderPara->pageUrlTree);

    urlNode *curNode = nodeAddrRelativeToAbs(curTree->curNode);
    urlNode *nextNode = NULL;

    if(curNode == NULL)
    {
        nextNode = nodeAddrRelativeToAbs(curTree->head);
    }
    else
    {
        int urlSim = -1;
        const char *curUrl = curNode->url;
        u16 prevUrlLen = strlen(curUrl);

        LOGI("urlDownloaded:%s len:%d", curUrl, curTree->len);
        LOGI("title:%s", (char*)addrRelativeToAbs(curNode->para.title));
        
        //当前url已经被下载，从未下载url链表中删除
        deleteUrlNodeFromList(curTree, curNode);

        urlNode *node = curNode;
        int i;

        for(i = 0; i < SEARCH_STEP_MAX; i++)
        {
            //LOGI("next %d:%s", i, node->url);
            node = nodeAddrRelativeToAbs(node->para.nextToLoad);

            if(node == NULL)
            {
                break;
            }
            else
            {
                int curSim = urlSimilarity(curUrl, prevUrlLen, node->url, node->para.len);

                if(curSim > urlSim)
                {
                    urlSim = curSim;
                    nextNode = node;
                }
            }
        }


        node = curNode;

        for(i = 0; i < SEARCH_STEP_MAX; i++)
        {
            //LOGI("prev %d:%s", i, node->url);
            node = nodeAddrRelativeToAbs(node->para.prevToLoad);

            if(node == NULL)
            {
                break;
            }
            else
            {
                int curSim = urlSimilarity(curUrl, prevUrlLen, node->url, node->para.len);

                if(curSim > urlSim)
                {
                    urlSim = curSim;
                    nextNode = node;
                }
            }
        }
    }

    char *nextUrl=NULL;
    if(nextNode != NULL)
    {
        nextUrl = nextNode->url;
        curTree->curNode = nodeAddrAbsToRelative(nextNode);
    }

    int *param = (*env)->GetIntArrayElements(env, jPageParam, NULL);
    param[PARAM_PROCESSED] = curTree->processed;
    (*env)->ReleaseIntArrayElements(env, jPageParam, param, 0);


    LOGI("nextUrl:%s", nextUrl);
    return (*env)->NewStringUTF(env, nextUrl);
}


jstring Java_com_gk969_UltimateImgSpider_SpiderService_jniFindNextImgUrl(
    JNIEnv *env, jobject thiz, jbyteArray jMd5, jintArray jImgParam)
{
    urlTree *curTree = &(spiderPara->imgUrlTree);
    
    //LOGI("URL_TYPE_IMG");
    if(jMd5 != NULL)
    {
        u8 *md5 = (*env)->GetByteArrayElements(env, jMd5, NULL);
        u64 md5_64;
        memcpy((u8 *)&md5_64, md5 + 4, 8);
        (*env)->ReleaseByteArrayElements(env, jMd5, md5, 0);

        //LOGI("md5:%08X", (u32)(md5_64>>32));

        urlNode *curNode = findUrlNodeByMd5(curTree, md5_64);

        //LOGI("cur url:%s", curNode->url);
        deleteUrlNodeFromList(curTree, curNode);

        downloadingImgNum--;
    }

    //LOGI("downloadingImgNum:%d", downloadingImgNum);

    urlNode *nextNode = nodeAddrRelativeToAbs(curTree->head);

    int i;
    for(i = 0; i < downloadingImgNum; i++)
    {
        if(nextNode == NULL)
        {
            break;
        }

        nextNode = nodeAddrRelativeToAbs(nextNode->para.nextToLoad);
    }

    char *nextUrl = NULL;
    if(nextNode != NULL)
    {
        downloadingImgNum++;

        nextUrl = nextImgUrlWithContainerBuf;

        sprintf(nextUrl, "%s %08X %s %08X", nextNode->url, (u32)nodeAddrAbsToRelative(nextNode), nodeAddrRelativeToAbs(nextNode->para.containerPage)->url, (u32)(nextNode->para.containerPage));
    }

    int *param = (*env)->GetIntArrayElements(env, jImgParam, NULL);
    param[PARAM_PAYLOAD] = downloadingImgNum;
    param[PARAM_PROCESSED] = curTree->processed;
    (*env)->ReleaseIntArrayElements(env, jImgParam, param, 0);


    //LOGI("nextUrl:%s", nextUrl);
    return (*env)->NewStringUTF(env, nextUrl);
}

void Java_com_gk969_UltimateImgSpider_SpiderService_jniSaveImgStorageInfo(
    JNIEnv *env, jobject thiz, jint jImgUrlAddr, jint jPageUrlAddr, jintArray jImgParam)
{
    u32 imgUrlAddr = (u32)jImgUrlAddr;
    u32 pageUrlAddr = (u32)jPageUrlAddr;
    //LOGI("jniSaveImgStorageInfo img:%08X:%s page:%08X:%s", (u32)imgUrlAddr, nodeAddrRelativeToAbs(imgUrlAddr)->url, (u32)pageUrlAddr, nodeAddrRelativeToAbs(pageUrlAddr)->url);


    AshmAllocObjectInstance=thiz;

    RelativeAddr infoAddr;
    t_storageImgInfo *imgInfo = mallocFromPool(env, sizeof(t_storageImgInfo), &infoAddr);

    if(imgInfo != NULL)
    {
        t_storageImgInfoList *infoList = &(spiderPara->storageImgList);

        imgInfo->imgUrl = imgUrlAddr;
        imgInfo->pageUrl = pageUrlAddr;

        imgInfo->prev = infoList->tail;
        imgInfo->next = NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0);

        if(infoAddrRelativeToAbs(infoList->head) == NULL)
        {
            infoList->head = infoAddr;
        }
        else
        {
            infoAddrRelativeToAbs(infoList->tail)->next = infoAddr;
        }

        infoList->tail = infoAddr;

        infoList->num++;

        int *param = (*env)->GetIntArrayElements(env, jImgParam, NULL);
        param[PARAM_DOWNLOAD] = infoList->num;
        (*env)->ReleaseIntArrayElements(env, jImgParam, param, 0);
    }
}
