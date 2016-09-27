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
#include "funcName.h"

#include "ashmem.h"


void fileTest()
{
    FILE *testFile;

    testFile = fopen("/mnt/sdcard/UltimateImgSpider/test.txt", "wb+");
    fprintf(testFile, "testd");
    fclose(testFile);


    testFile = fopen("/mnt/sdcard/UltimateImgSpider/test.txt", "rb+");
    fseek(testFile, 8, SEEK_SET);
    fwrite(" fseek ", 6, 1, testFile);
    fclose(testFile);


    char buf[20] = "00000000000";
    testFile = fopen("/mnt/sdcard/UltimateImgSpider/test.txt", "r");
    fread(buf, 19, 1, testFile);
    char str[100];
    int i;
    int ofs = 0;
    for(i = 0; i < 20; i++)
    {
        ofs += sprintf(str + ofs, "%02X ", buf[i]);
    }
    LOGI("test file:%s", buf);
    LOGI("test file:%s", str);
    fclose(testFile);
}

jstring stringFromJNI(JNIEnv *env,
                      jobject thiz, jstring jSrcStr)
{
    const char *srcStr = (*env)->GetStringUTFChars(env, jSrcStr, NULL);
    LOGI("build @ %s %s", __DATE__, __TIME__);
    LOGI("stringFromJNI %s ", srcStr);

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
#define SIZE_PER_URLPOOL    (1024*1024-sizeof(u32)-sizeof(u32))


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
#define SET_POOL_OFFSET(addr, offset)    addr&=~POOL_OFFSET_BITS;\
                                        addr|=offset&POOL_OFFSET_BITS;

#define GET_POOL_PTR(addr)              (((addr)&POOL_PTR_BITS)>>POOL_OFFSET_BIT)
#define SET_POOL_PTR(addr, ptr)          addr&=~POOL_PTR_BITS;\
                                        addr|=(ptr<<POOL_OFFSET_BIT)&POOL_PTR_BITS;

#define NEW_RELATIVE_ADDR(ptr, offset)   ((((u32)ptr)<<POOL_OFFSET_BIT)|offset)
#define POOL_PTR_INVALID    (((u32)1<<POOL_PTR_BIT)-1)

#define RELATIVE_ADDR_NULL  NEW_RELATIVE_ADDR(POOL_PTR_INVALID, 0)


#define SPIDER_PARA_NAME    "spiderPara"

enum
{
    PARA_TOTAL = 0,
    PARA_PROCESSED,
    PARA_HEIGHT,
    PARA_PAYLOAD,
    PARA_DOWNLOAD,
    PARA_TOTAL_SIZE
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
    u64 imgTotalSize;
    RelativeAddr srcPageNode;
    RelativeAddr curPageNode;
} t_spiderPara;


typedef struct memPool
{
    u32 idleMemPtr;
    u8 mem[SIZE_PER_URLPOOL];
} t_urlPool;


#pragma pack()

t_spiderPara *spiderPara = NULL;

#define TOTAL_POOL (MAX_RAM_TOTAL_POOL/sizeof(t_urlPool))
t_urlPool *urlPools[TOTAL_POOL];

u32 downloadingImgNum = 0;

#define SEARCH_STEPS_FOR_NEXT_PAGE 5000
#define SEARCH_STEPS_ON_SEL_DIFF_PAGE  (SEARCH_STEPS_FOR_NEXT_PAGE*2)

#define SELECT_DIFF_PAGE_CNT    3
u8 pageWithoutNewImgCnt = 0;

char urlBuf[MAX_SIZE_PER_URL * 2 + 2 + sizeof(u32) * 2 + 2];

RelativeAddr nodeAddrAbsToRelative(urlNode *node)
{
    RelativeAddr relativeAddr = RELATIVE_ADDR_NULL;

    if(node != NULL)
    {
        u32 i;

        for(i = 0; i < spiderPara->urlPoolNum; i++)
        {
            s64 ofs = (u32) node - (u32) (urlPools[i]->mem);

            if(ofs >= 0 && ofs < sizeof(t_urlPool))
            {
                relativeAddr = NEW_RELATIVE_ADDR(i, (u32) ofs);
                break;
            }
        }
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
            return (void *) (pool->mem + offset);
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
    t_urlPool *urlPool = NULL;
    u32 poolIndex;
    u32 offset = 0;

    size = (size + 3) & 0xFFFFFFFC;

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
            AshmBlock *ashm = spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(
                    spiderPara->urlPoolNum), sizeof(t_urlPool));

            if(ashm != NULL)
            {
                LOGI("init new urlPool Success");

                urlPool = (t_urlPool *) (ashm->data);
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

        node = (void *) (urlPool->mem + offset);

        if(direction != NULL)
        {
            *direction = NEW_RELATIVE_ADDR(poolIndex, offset);
        }
    }

    return node;
}


jboolean spiderParaInit(JNIEnv *env, u64 *imgParam, u64 *pageParam)
{
    AshmBlock *ashm = spiderGetAshmemFromWatchdog(env, SPIDER_PARA_NAME, sizeof(t_spiderPara));
    if(ashm != NULL)
    {
        spiderPara = (t_spiderPara *) (ashm->data);

        if(ashm->ashmStat != ASHM_EXIST)
        {
            spiderPara->pageUrlTree.head = RELATIVE_ADDR_NULL;
            spiderPara->pageUrlTree.tail = RELATIVE_ADDR_NULL;
            spiderPara->pageUrlTree.root = RELATIVE_ADDR_NULL;
            spiderPara->pageUrlTree.processed = 0;
            spiderPara->pageUrlTree.len = 0;
            spiderPara->pageUrlTree.height = 0;

            spiderPara->imgUrlTree.head = RELATIVE_ADDR_NULL;
            spiderPara->imgUrlTree.tail = RELATIVE_ADDR_NULL;
            spiderPara->imgUrlTree.root = RELATIVE_ADDR_NULL;
            spiderPara->imgUrlTree.processed = 0;
            spiderPara->imgUrlTree.len = 0;
            spiderPara->imgUrlTree.height = 0;

            spiderPara->urlPoolNum = 0;

            spiderPara->storageImgList.head = RELATIVE_ADDR_NULL;
            spiderPara->storageImgList.tail = RELATIVE_ADDR_NULL;
            spiderPara->storageImgList.num = 0;

            spiderPara->imgTotalSize = 0;
            spiderPara->curPageNode = RELATIVE_ADDR_NULL;
            spiderPara->srcPageNode = RELATIVE_ADDR_NULL;
        }

        imgParam[PARA_TOTAL] = spiderPara->imgUrlTree.len;
        imgParam[PARA_PROCESSED] = spiderPara->imgUrlTree.processed;
        imgParam[PARA_HEIGHT] = spiderPara->imgUrlTree.height;
        imgParam[PARA_DOWNLOAD] = spiderPara->storageImgList.num;
        imgParam[PARA_TOTAL_SIZE] = spiderPara->imgTotalSize;

        pageParam[PARA_TOTAL] = spiderPara->pageUrlTree.len;
        pageParam[PARA_PROCESSED] = spiderPara->pageUrlTree.processed;
        pageParam[PARA_HEIGHT] = spiderPara->pageUrlTree.height;

        return true;
    }

    return false;
}

jboolean urlPoolInit(JNIEnv *env)
{
    if(spiderPara->urlPoolNum == 0)
    {
        AshmBlock *ashm = spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(0),
                                                      sizeof(t_urlPool));

        if(ashm != NULL)
        {
            urlPools[0] = (t_urlPool *) (ashm->data);
            urlPools[0]->idleMemPtr = 0;
            spiderPara->urlPoolNum = 1;
            return true;
        }
    }
    else
    {
        u32 i;
        for(i = 0; i < spiderPara->urlPoolNum; i++)
        {
            AshmBlock *ashm = spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(i),
                                                          sizeof(t_urlPool));

            if(ashm == NULL)
            {
                break;
            }

            if(ashm->ashmStat != ASHM_EXIST)
            {
                break;
            }

            urlPools[i] = (t_urlPool *) (ashm->data);
        }

        if(i == spiderPara->urlPoolNum)
        {
            return true;
        }
    }

    return false;
}

jstring jniSpiderInit(JNIEnv *env, jobject thiz, jlongArray jImgParam, jlongArray jPageParam)
{
    AshmAllocObjectInstance = thiz;
    char *srcUrl = NULL;

    jlong *imgParam = (*env)->GetLongArrayElements(env, jImgParam, NULL);
    jlong *pageParam = (*env)->GetLongArrayElements(env, jPageParam, NULL);

    if(spiderParaInit(env, (u64*)imgParam, (u64*)pageParam))
    {
        if(urlPoolInit(env))
        {
            urlNode *srcNode = nodeAddrRelativeToAbs(spiderPara->srcPageNode);
            srcUrl = (srcNode == NULL) ? "" : srcNode->url;
        }
    }

    (*env)->ReleaseLongArrayElements(env, jImgParam, imgParam, 0);
    (*env)->ReleaseLongArrayElements(env, jPageParam, pageParam, 0);

    return (*env)->NewStringUTF(env, srcUrl);
}


jstring jniGetProjectInfo(JNIEnv *env, jobject thiz, jstring jDataFileFullPath,
                                 jlongArray jImgParam, jlongArray jPageParam)
{
    const char *dataFileFullPath = (*env)->GetStringUTFChars(env, jDataFileFullPath, NULL);

    jlong *imgParam = (*env)->GetLongArrayElements(env, jImgParam, NULL);
    jlong *pageParam = (*env)->GetLongArrayElements(env, jPageParam, NULL);

    LOGI("jniGetProjectInfo path:%s", dataFileFullPath);

    char *srcUrl = NULL;

    FILE *dataFile = fopen(dataFileFullPath, "r");
    if(dataFile != NULL)
    {
        AshmParaStore ashmParaStore;

        if(fread(&ashmParaStore, sizeof(AshmParaStore), 1, dataFile) == 1)
        {
            LOGI("AshmNode Name:%s Size:%d", ashmParaStore.name, ashmParaStore.size);

            if(strcmp(ashmParaStore.name, SPIDER_PARA_NAME) == 0)
            {
                t_spiderPara para;
                if(fread(&para, sizeof(t_spiderPara), 1, dataFile) == 1)
                {
                    imgParam[PARA_TOTAL] = para.imgUrlTree.len;
                    imgParam[PARA_PROCESSED] = para.imgUrlTree.processed;
                    imgParam[PARA_HEIGHT] = para.imgUrlTree.height;
                    imgParam[PARA_DOWNLOAD] = para.storageImgList.num;
                    imgParam[PARA_TOTAL_SIZE] = para.imgTotalSize;

                    pageParam[PARA_TOTAL] = para.pageUrlTree.len;
                    pageParam[PARA_PROCESSED] = para.pageUrlTree.processed;
                    pageParam[PARA_HEIGHT] = para.pageUrlTree.height;

                    LOGI("para.srcPageNode %08X", para.srcPageNode);

                    u32 srcNodeOffsetInFile = (u32)(GET_POOL_PTR(para.srcPageNode)
                                              * (sizeof(t_urlPool) + sizeof(AshmParaStore))
                                              + sizeof(AshmParaStore)
                                              + OFFSET_IN_STRUCT(t_urlPool, mem)
                                              + GET_POOL_OFFSET(para.srcPageNode));

                    LOGI("srcNodeOffsetInFile %08X", srcNodeOffsetInFile);

                    fseek(dataFile, srcNodeOffsetInFile, SEEK_CUR);
                    nodePara srcNodePara;
                    if(fread(&srcNodePara, sizeof(nodePara), 1, dataFile) == 1)
                    {
                        LOGI("src url size %d", srcNodePara.len);
                        if(fread(urlBuf, srcNodePara.len + 1, 1, dataFile) == 1)
                        {
                            srcUrl = urlBuf;
                            LOGI("srcUrl:%s", srcUrl);
                        }
                    }
                }
            }
        }

        fclose(dataFile);
    }

    (*env)->ReleaseLongArrayElements(env, jImgParam, imgParam, 0);
    (*env)->ReleaseLongArrayElements(env, jPageParam, pageParam, 0);
    (*env)->ReleaseStringUTFChars(env, jDataFileFullPath, dataFileFullPath);


    return (*env)->NewStringUTF(env, srcUrl);
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

        LOGI("Chain %08X %c p:%08X l:%08X r:%08X", (u32) (node->para.md5_64 >> 32),
             (node->para.color == RBT_BLACK) ? 'B' : 'R', parentMd5, leftMd5, rightMd5);
        //LOGI("Chain %08X %c p:%08X l:%08X r:%08X", (u32)(node->para.md5_64>>32), (node->para.color==RBT_BLACK)?'B':'R', node->para.parent, node->para.left, node->para.right);

        node = nodeAddrRelativeToAbs(node->para.nextToLoad);
    }
}


void rbUrlTreeFixup(urlTree *tree, urlNode *node)
{
    urlNode *parent = nodeAddrRelativeToAbs(node->para.parent);
    urlNode *grandParent=NULL;
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

bool urlTreeInsert(JNIEnv *env, urlTree *tree, const char *newUrl,
                   u64 newMd5_64, RelativeAddr *nodeAddr)
{
    RelativeAddr *nextNodeAddr = &(tree->root);
    urlNode *node = nodeAddrRelativeToAbs(*nextNodeAddr);
    urlNode *parent = NULL;

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
            *nodeAddr = *nextNodeAddr;
            return false;
        }

        parent = node;
        node = nodeAddrRelativeToAbs(*nextNodeAddr);

        height++;
    }

    LOGI("urlTreeInsert %s", newUrl);

    if(height > tree->height)
    {
        tree->height = height;
    }

    RelativeAddr newNodeAddr = RELATIVE_ADDR_NULL;

    u16 urlLen = (u16)strlen(newUrl);
    node = mallocFromPool(env, urlLen + 1 + sizeof(nodePara), nextNodeAddr);
    if(node != NULL)
    {
        newNodeAddr = *nextNodeAddr;
        urlNode *tail;

        strcpy(node->url, newUrl);
        node->para.md5_64 = newMd5_64;
        node->para.len = urlLen;

        node->para.left = RELATIVE_ADDR_NULL;
        node->para.right = RELATIVE_ADDR_NULL;

        node->para.parent = nodeAddrAbsToRelative(parent);

        node->para.containerPage = spiderPara->curPageNode;
        node->para.title = RELATIVE_ADDR_NULL;

        node->para.nextToLoad = RELATIVE_ADDR_NULL;
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

    *nodeAddr = newNodeAddr;
    return true;
}


jlong jniAddUrl(JNIEnv *env,
                jobject thiz, jstring jUrl, jbyteArray jMd5, jint jType, jlongArray jParam)
{
    urlTree *curTree =
            (jType == URL_TYPE_PAGE) ? (&(spiderPara->pageUrlTree)) : (&(spiderPara->imgUrlTree));
    const char *url = (*env)->GetStringUTFChars(env, jUrl, NULL);

    AshmAllocObjectInstance = thiz;

    u8 *md5 = (*env)->GetByteArrayElements(env, jMd5, NULL);
    u64 md5_64;
    memcpy((u8 *) &md5_64, md5 + 4, 8);

    jlong urlRelativeAddr;

    //LOGI("jniAddUrl %s", url);

    //if(curTree->len<100)
    {
        //LOGI("len:%d %d md5_64:%08X type:%d url:%s",curTree->len, strlen(url), (u32)(md5_64>>32), jType, url);

        if(urlTreeInsert(env, curTree, url, md5_64, &urlRelativeAddr) && jType == URL_TYPE_IMG)
        {
            pageWithoutNewImgCnt = 0;
        }

        /*
        urlNode *tail=nodeAddrRelativeToAbs(curTree->tail);
        urlNode *head=nodeAddrRelativeToAbs(curTree->head);
        LOGI("tail:%08X head:%08X", tail, head);
        */
    }

    jlong *param = (*env)->GetLongArrayElements(env, jParam, NULL);
    param[PARA_TOTAL] = curTree->len;
    param[PARA_HEIGHT] = curTree->height;
    (*env)->ReleaseLongArrayElements(env, jParam, param, 0);


    (*env)->ReleaseStringUTFChars(env, jUrl, url);
    (*env)->ReleaseByteArrayElements(env, jMd5, md5, 0);

    return urlRelativeAddr;
}


void jniSetSrcPageUrl(JNIEnv *env, jobject thiz,
                      jstring jPageUrl, jbyteArray jSrcPageUrlMd5, jlongArray jParam)
{
    spiderPara->srcPageNode = jniAddUrl(env, thiz, jPageUrl, jSrcPageUrlMd5, URL_TYPE_PAGE, jParam);
    LOGI("jniSetSrcPageUrl %08X", spiderPara->srcPageNode);
}


void deleteUrlNodeFromList(urlTree *curTree, urlNode *curNode)
{

    LOGI("deleteUrlNodeFromList cur %s", curNode->url);

    urlNode *prev = nodeAddrRelativeToAbs(curNode->para.prevToLoad);
    urlNode *next = nodeAddrRelativeToAbs(curNode->para.nextToLoad);

    if(prev != NULL || next != NULL)
    {
        curTree->processed++;

        if(prev == NULL)
        {
            curTree->head = curNode->para.nextToLoad;
        }
        else
        {
            LOGI("prev %s", prev->url);
            prev->para.nextToLoad = curNode->para.nextToLoad;
        }

        if(next == NULL)
        {
            curTree->tail = curNode->para.prevToLoad;
        }
        else
        {
            LOGI("next %s", next->url);
            next->para.prevToLoad = curNode->para.prevToLoad;
        }

        curNode->para.nextToLoad = RELATIVE_ADDR_NULL;
        curNode->para.prevToLoad = RELATIVE_ADDR_NULL;
    }
    else
    {

        if((curTree->tail == curTree->head) &&
           (curTree->tail != RELATIVE_ADDR_NULL))
        {
            curTree->processed++;
            LOGI("list cleared!");
        }
        else
        {
            LOGI("deleteUrlNodeFromList ERROR NODE!");
        }

        curTree->head = RELATIVE_ADDR_NULL;
        curTree->tail = RELATIVE_ADDR_NULL;
    }
}


void jniSavePageTitle(
        JNIEnv *env, jobject thiz, jstring jCurPageTitle)
{
    urlNode *curNode = nodeAddrRelativeToAbs(spiderPara->curPageNode);

    //LOGI("jniSavePageTitle");

    AshmAllocObjectInstance = thiz;

    //保存页面标题
    if(jCurPageTitle != NULL)
    {
        const char *title = (*env)->GetStringUTFChars(env, jCurPageTitle, NULL);
        RelativeAddr titleAddr;
        char *titleBuf = mallocFromPool(env, strlen(title) + 1, &titleAddr);

        if(titleBuf != NULL)
        {
            curNode->para.title = titleAddr;
            strcpy(titleBuf, title);
        }

        (*env)->ReleaseStringUTFChars(env, jCurPageTitle, title);
    }
}

int urlSimilarity(const char *url1, int len1, const char *url2, int len2)
{
    int i;
    int len = (len1 > len2) ? len2 : len1;
    int lenInWord = len / sizeof(u32);
    const u32 *urlInWord1 = (u32 *) url1;
    const u32 *urlInWord2 = (u32 *) url2;

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

#define SRCURL_RATIO_FORNEXT    5
#define CURURL_RATIO_FORNEXT   (10-SRCURL_RATIO_FORNEXT)

int pageUrlRankForNext(urlNode *srcUrl, urlNode *curUrl, urlNode *testedUrl, bool isDiffWithCur)
{
    int curUrlRatio = isDiffWithCur ? (0 - CURURL_RATIO_FORNEXT) : CURURL_RATIO_FORNEXT;
    //LOGI("pageUrlRankForNext %s %d %s %d %s %d", srcUrl->url, srcUrl->para.len, curUrl->url, curUrl->para.len, testedUrl->url, testedUrl->para.len);
    int srcUrlSimilarity = urlSimilarity(srcUrl->url, srcUrl->para.len, testedUrl->url,
                                         testedUrl->para.len);
    int curUrlSimilarity = urlSimilarity(curUrl->url, curUrl->para.len, testedUrl->url,
                                         testedUrl->para.len);
    int rank = srcUrlSimilarity * SRCURL_RATIO_FORNEXT + curUrlSimilarity * curUrlRatio;
    //LOGI("srcUrlSimilarity %d curUrlSimilarity %d rank %d", srcUrlSimilarity, curUrlSimilarity, rank);

    return rank;
}


jstring jniFindNextPageUrl(
        JNIEnv *env, jobject thiz, jlongArray jPageParam)
{
    urlTree *curTree = &(spiderPara->pageUrlTree);
    urlNode *nextNode = NULL;

    if(curTree->len == 1)
    {
        nextNode = nodeAddrRelativeToAbs(curTree->head);
    }
    else
    {
        urlNode *curNode = nodeAddrRelativeToAbs(spiderPara->curPageNode);
        urlNode *srcNode = nodeAddrRelativeToAbs(spiderPara->srcPageNode);
        LOGI("jniFindNextPageUrl %08X %08X", curNode, srcNode);

        LOGI("srcUrl:%s prevUrl:%s pageUrlTreeLen:%d", srcNode->url, curNode->url, curTree->len);
        LOGI("title:%s", (char *) addrRelativeToAbs(curNode->para.title));

        int urlRankMax = 0 - 0x7FFFFFFF;

        bool selectDiffPageWithCur;
        urlNode *prev;
        urlNode *next;
        int searchStepPerDirection;

        if(pageWithoutNewImgCnt >= SELECT_DIFF_PAGE_CNT)
        {
            selectDiffPageWithCur = true;
            pageWithoutNewImgCnt = 0;
            prev = nodeAddrRelativeToAbs(curTree->tail);
            next = nodeAddrRelativeToAbs(curTree->head);
            searchStepPerDirection = SEARCH_STEPS_ON_SEL_DIFF_PAGE;
        }
        else
        {
            selectDiffPageWithCur = false;
            prev = nodeAddrRelativeToAbs(curNode->para.prevToLoad);
            next = nodeAddrRelativeToAbs(curNode->para.nextToLoad);
            searchStepPerDirection = SEARCH_STEPS_FOR_NEXT_PAGE;
        }

        //当前url已经被下载，从未下载url链表中删除
        deleteUrlNodeFromList(curTree, curNode);


        int i;
        for(i = 0; i < searchStepPerDirection; i++)
        {

            if(next == NULL)
            {
                break;
            }
            else
            {
                int curRank = pageUrlRankForNext(srcNode, curNode, next, selectDiffPageWithCur);

                //LOGI("next %d rank %d %s", i, curRank, next->url);

                if(curRank > urlRankMax)
                {
                    urlRankMax = curRank;
                    nextNode = next;
                    LOGI("rank %d step %d %s", curRank, i, next->url);
                }
            }
            next = nodeAddrRelativeToAbs(next->para.nextToLoad);
        }

        int prevStep;
        for(prevStep = 0; prevStep < 2; prevStep++)
        {
            for(i = 0; i < searchStepPerDirection; i++)
            {
                if(prev == NULL)
                {
                    break;
                }
                else
                {
                    int curRank = pageUrlRankForNext(srcNode, curNode, prev, selectDiffPageWithCur);

                    //LOGI("prev %d rank %d %s", i, curRank, prev->url);

                    if(curRank > urlRankMax)
                    {
                        urlRankMax = curRank;
                        nextNode = prev;
                        LOGI("rank %d step %d %s", curRank, i, prev->url);
                    }
                }
                prev = nodeAddrRelativeToAbs(prev->para.prevToLoad);
            }

            if(searchStepPerDirection == SEARCH_STEPS_FOR_NEXT_PAGE)
            {
                prev = nodeAddrRelativeToAbs(curTree->tail);
            }
            else
            {
                break;
            }
        }

    }

    char *nextUrl = NULL;
    if(nextNode != NULL)
    {
        nextUrl = nextNode->url;
        spiderPara->curPageNode = nodeAddrAbsToRelative(nextNode);
        pageWithoutNewImgCnt++;
    }

    u64 *param = (*env)->GetLongArrayElements(env, jPageParam, NULL);
    param[PARA_PROCESSED] = curTree->processed;
    (*env)->ReleaseLongArrayElements(env, jPageParam, param, 0);


    LOGI("jniFindNextPageUrl:%s", nextUrl);
    return (*env)->NewStringUTF(env, nextUrl);
}

void logNode(urlNode *node)
{
    LOGI("curNode:%s", node->url);

    urlNode *prev = nodeAddrRelativeToAbs(node->para.prevToLoad);
    if(prev != NULL)
    {
        LOGI("prev:%s", prev->url);
    }
    else
    {
        LOGI("prev:null");
    }

    urlNode *next = nodeAddrRelativeToAbs(node->para.nextToLoad);
    if(next != NULL)
    {
        LOGI("next:%s", next->url);
    }
    else
    {
        LOGI("next:null");
    }
}

void jniOnImgUrlProcessed(JNIEnv *env, jobject thiz, jint jLastImgUrlAddr, jlongArray jImgParam)
{
    urlTree *curTree = &(spiderPara->imgUrlTree);

    LOGI("jniOnImgUrlProcessed lastImgAddr:%08X curTree head %08X tail %08X", jLastImgUrlAddr,
         curTree->head, curTree->tail);

    // see if we find new url successfully at last time
    urlNode *lastImgNode = nodeAddrRelativeToAbs((RelativeAddr) jLastImgUrlAddr);
    if(lastImgNode != NULL)
    {
        downloadingImgNum--;
        deleteUrlNodeFromList(curTree, lastImgNode);
    }
    else
    {
        LOGI("invalid lastUrlAddr %08X", jLastImgUrlAddr);
    }

    u64 *param = (*env)->GetLongArrayElements(env, jImgParam, NULL);
    param[PARA_PAYLOAD] = downloadingImgNum;
    param[PARA_PROCESSED] = curTree->processed;
    (*env)->ReleaseLongArrayElements(env, jImgParam, param, 0);
}

jstring jniFindNextImgUrl(JNIEnv *env, jobject thiz, jlongArray jImgParam)
{
    urlTree *curTree = &(spiderPara->imgUrlTree);


    char *nextUrl = NULL;

    urlNode *nextNode = nodeAddrRelativeToAbs(curTree->head);

    int i;
    for(i = 0; i < downloadingImgNum; i++)
    {
        if(nextNode == NULL)
        {
            break;
        }

        //logNode(nextNode);

        nextNode = nodeAddrRelativeToAbs(nextNode->para.nextToLoad);
    }

    if(nextNode != NULL)
    {
        //logNode(nextNode);
        downloadingImgNum++;

        nextUrl = urlBuf;
        sprintf(nextUrl, "%s %08X %s %08X", nextNode->url,
                (u32) nodeAddrAbsToRelative(nextNode),
                nodeAddrRelativeToAbs(nextNode->para.containerPage)->url,
                (u32) (nextNode->para.containerPage));
    }


    u64 *param = (*env)->GetLongArrayElements(env, jImgParam, NULL);
    param[PARA_PAYLOAD] = downloadingImgNum;
    (*env)->ReleaseLongArrayElements(env, jImgParam, param, 0);


    LOGI("jniFindNextImgUrl:%s", nextUrl);
    return (*env)->NewStringUTF(env, nextUrl);
}

void jniSaveImgStorageInfo(
        JNIEnv *env, jobject thiz, jint jImgUrlAddr, jint jPageUrlAddr, jlongArray jImgParam,
        jint jCurImgFileSize)
{
    u32 imgUrlAddr = (u32) jImgUrlAddr;
    u32 pageUrlAddr = (u32) jPageUrlAddr;
    //LOGI("jniSaveImgStorageInfo img:%08X:%s page:%08X:%s", (u32)imgUrlAddr, nodeAddrRelativeToAbs(imgUrlAddr)->url, (u32)pageUrlAddr, nodeAddrRelativeToAbs(pageUrlAddr)->url);

    AshmAllocObjectInstance = thiz;

    RelativeAddr infoAddr;
    t_storageImgInfo *imgInfo = mallocFromPool(env, sizeof(t_storageImgInfo), &infoAddr);

    if(imgInfo != NULL)
    {
        t_storageImgInfoList *infoList = &(spiderPara->storageImgList);

        imgInfo->imgUrl = imgUrlAddr;
        imgInfo->pageUrl = pageUrlAddr;

        imgInfo->prev = infoList->tail;
        imgInfo->next = RELATIVE_ADDR_NULL;

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

        spiderPara->imgTotalSize += jCurImgFileSize;

        u64 *param = (*env)->GetLongArrayElements(env, jImgParam, NULL);
        param[PARA_DOWNLOAD] = infoList->num;
        param[PARA_TOTAL_SIZE] = spiderPara->imgTotalSize;
        (*env)->ReleaseLongArrayElements(env, jImgParam, param, 0);
    }
}
