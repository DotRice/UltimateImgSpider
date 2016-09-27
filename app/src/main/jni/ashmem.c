#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <jni.h>
#include <malloc.h>
#include <android/log.h>
#include <linux/ashmem.h>
#include <sys/mman.h>
#include <sys/ioctl.h>
#include <fcntl.h>

#include "typeDef.h"
#include "funcName.h"

#include "ashmem.h"

/*
 * 使用共享内存存储urlList，实现重启下载服务进程后能恢复工作现场。
 * Spider需要新申请一段内存时，向看门狗进程发出命令，并提供共享内存段名称和大小。
 * 看门狗进程创建共享内存，并将其映射到自己的内存空间，然后向Spider进程返回共享内存的文件描述符。
 * Spider进程用这个文件描述符映射此段共享内存到自己的内存空间。
 */

#define ASHM_NAME_SIZE  32

int ashmem_create_region(const char *name, u32 size) {
    int fd = -1;
    int fdWithOption;
    char buf[ASHM_NAME_SIZE];

    while(name && size) {
        fd = open(ASHMEM_NAME_DEF, O_RDWR);

        if(fd < 0) {
            break;
        }

        LOGI("ashmem open success %d", fd);

        strlcpy(buf, name, sizeof(buf));
        fdWithOption = ioctl(fd, ASHMEM_SET_NAME, buf);

        if(fdWithOption < 0) {
            close(fd);
            fd = -1;
            break;
        }

        fdWithOption = ioctl(fd, ASHMEM_SET_SIZE, size);

        if(fdWithOption < 0) {
            close(fd);
            fd = -1;
            break;
        }

        break;
    }

    return fd;
}


AshmNode *ashmemChainHead = NULL;
AshmNode *ashmemChainTail = NULL;


AshmNode *findAshmemByName(const char *name) {
    AshmNode *ashmNode = ashmemChainHead;

    while(ashmNode != NULL) {
        if(strcmp(ashmNode->name, name) == 0) {
            return ashmNode;
        }

        ashmNode = ashmNode->next;
    }

    return NULL;
}

int createNewAshmem(const char *name, int size, u8 **addr) {
    int sizeWithStat=size+SIZE_IN_STRUCT(AshmBlock, ashmStat);
    int fd = ashmem_create_region(name, sizeWithStat);

    if(fd >= 0) {
        LOGI("create ashmem name:%s size:%d %d fd:%d success!", name, size, sizeWithStat, fd);

        AshmBlock *ashm = mmap(NULL, sizeWithStat, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);

        if(ashm != NULL) {
            AshmNode *newAshmNode = malloc(sizeof(AshmNode));

            if(newAshmNode != NULL) {
                ashm->ashmStat = 0;

                if(addr != NULL) {
                    *addr = ashm->data;
                }

                newAshmNode->ashmem = ashm;
                newAshmNode->fd = fd;
                strncpy(newAshmNode->name, name, ASHM_NAME_SIZE);
                newAshmNode->next = NULL;
                newAshmNode->size = size;

                if(ashmemChainHead == NULL) {
                    ashmemChainHead = newAshmNode;
                }

                if(ashmemChainTail != NULL) {
                    ashmemChainTail->next = newAshmNode;
                }

                ashmemChainTail = newAshmNode;


                LOGI("ashmem mmap %d to watchdog process success!", (u32) ashm);

                if(strcmp(name, "ashmTest") == 0) {
                    int i;

                    for(i = 0; i < 8; i++) {
                        ashm->data[i] = i;
                    }
                }
            }
        }
    }

    return fd;
}

void jniRestoreProjectData(JNIEnv *env, jobject thiz, jstring jDataFileFullPath) {
    const u8 *dataFileFullPath = (*env)->GetStringUTFChars(env, jDataFileFullPath, NULL);

    LOGI("jniRestoreProjectData path:%s", dataFileFullPath);

    if(ashmemChainHead!=NULL){
        LOGI("ashmem chain not empty");
        AshmNode *nextAshmNode;
        AshmNode *ashmNode=ashmemChainHead;
        while(ashmNode!=NULL){
            LOGI("free ashmem node %s", ashmNode->name);
            munmap(ashmNode->ashmem, ashmNode->size + SIZE_IN_STRUCT(AshmBlock, ashmStat));
            close(ashmNode->fd);
            nextAshmNode=ashmNode->next;
            free(ashmNode);
            ashmNode=nextAshmNode;
        }
        ashmemChainHead=NULL;
        ashmemChainTail=NULL;
    }

    FILE *dataFile = fopen(dataFileFullPath, "r");

    if(dataFile != NULL) {
        AshmParaStore ashmParaStore;

        while(true) {
            if(fread(&ashmParaStore, sizeof(AshmParaStore), 1, dataFile) != 1) {
                LOGI("fread ashmParaStore error");
                break;
            }

            u8 *data;
            int fd = createNewAshmem(ashmParaStore.name, ashmParaStore.size, &data);

            if(fd < 0) {
                LOGI("createNewAshmem error");
                break;
            }

            if(fread(data, ashmParaStore.size, 1, dataFile) != 1) {
                LOGI("fread data error");
                break;
            }

            LOGI("Restore AshmNode Name:%s Size:%d Success", ashmParaStore.name,
                 ashmParaStore.size);
        }

        fclose(dataFile);
    }

    (*env)->ReleaseStringUTFChars(env, jDataFileFullPath, dataFileFullPath);
}

#define FILE_BLOCK_UPDATE_SIZE  4096

int fileIncrementalUpdate(FILE *file, void *srcData, int size) {
    void *buf = malloc(FILE_BLOCK_UPDATE_SIZE);
    if(buf != 0) {
        int offset = 0;
        int updateSize = 0;
        do {
            int blockSize = size - offset;
            u8 *data = srcData + offset;
            offset += FILE_BLOCK_UPDATE_SIZE;
            if(offset < size) {
                blockSize = FILE_BLOCK_UPDATE_SIZE;
            }

            while(true) {
                if(fread(buf, blockSize, 1, file) == 1) {
                    if(memcmp(buf, data, blockSize) != 0) {
                        fseek(file, 0 - blockSize, SEEK_CUR);
                    }
                    else {
                        break;
                    }
                }

                updateSize += blockSize;
                fwrite(data, blockSize, 1, file);
                break;
            }

        } while(offset < size);

        //LOGI("fileIncrementalUpdate rawSize:%d updateSize:%d", size, updateSize);
        free(buf);
        return updateSize;
    }

    return 0;
}

void jniStoreProjectData(JNIEnv *env, jobject thiz, jstring jDataFileFullPath) {
    const char *dataFileFullPath = (*env)->GetStringUTFChars(env, jDataFileFullPath, NULL);

    LOGI("jniStoreProjectData path:%s", dataFileFullPath);

    FILE *dataFile = fopen(dataFileFullPath, "a");
    if(dataFile != NULL) {
        fclose(dataFile);
    }

    dataFile = fopen(dataFileFullPath, "rb+");

    int totalSize = 0;
    int updateSize = 0;

    if(dataFile != NULL) {
        AshmNode *ashmNode;

        for(ashmNode = ashmemChainHead; ashmNode != NULL; ashmNode = ashmNode->next) {
            AshmParaStore ashmParaStore;
            strncpy(ashmParaStore.name, ashmNode->name, ASHM_NAME_SIZE);
            ashmParaStore.size = ashmNode->size;
            ashmParaStore.ashmStat = ashmNode->ashmem->ashmStat;

            totalSize += sizeof(AshmParaStore);
            updateSize += fileIncrementalUpdate(dataFile, &ashmParaStore, sizeof(AshmParaStore));

            totalSize += ashmNode->size;
            updateSize += fileIncrementalUpdate(dataFile, ashmNode->ashmem->data, ashmNode->size);
        }

        fclose(dataFile);
    }

    (*env)->ReleaseStringUTFChars(env, jDataFileFullPath, dataFileFullPath);

    LOGI("fileIncrementalUpdate total:%d update:%d", totalSize, updateSize);
}

int jniGetAshmem(JNIEnv *env, jobject thiz, jstring jname, jint size) {
    int fd;
    const char *name = (*env)->GetStringUTFChars(env, jname, NULL);

    LOGI("WatchdogService_jniGetAshmem %s", name);
    AshmNode *ashmNode = findAshmemByName(name);

    if(ashmNode != NULL) {
        LOGI("ashmNode %s Exist", name);
        ashmNode->ashmem->ashmStat = ASHM_EXIST;
        fd = ashmNode->fd;
    }
    else {
        fd = createNewAshmem(name, size, NULL);
    }

    (*env)->ReleaseStringUTFChars(env, jname, name);

    return fd;
}

jobject AshmAllocObjectInstance = NULL;

jclass SpiderServiceClass = NULL;

jmethodID getAshmemFromWatchdogMID = NULL;

void *spiderGetAshmemFromWatchdog(JNIEnv *env, const char *name, int size) {
    void *ashmem = NULL;

    LOGI("spiderGetAshmemFromWatchdog name:%s size:%d", name, size);

    if(getAshmemFromWatchdogMID == NULL) {
        SpiderServiceClass = (*env)->FindClass(env, "com/gk969/UltimateImgSpider/SpiderService");

        if(SpiderServiceClass != NULL) {
            getAshmemFromWatchdogMID = (*env)->GetMethodID(env, SpiderServiceClass,
                                                           "getAshmemFromWatchdog",
                                                           "(Ljava/lang/String;I)I");
        }
    }

    if(getAshmemFromWatchdogMID != NULL) {
        jstring jname = (*env)->NewStringUTF(env, name);


        LOGI("spiderGetAshmemFromWatchdog call method");
        LOGI("AshmAllocObjectInstance %08X %08X", AshmAllocObjectInstance,
             getAshmemFromWatchdogMID);
        int fd = (*env)->CallIntMethod(env, AshmAllocObjectInstance, getAshmemFromWatchdogMID,
                                       jname, size);

        if(fd >= 0) {
            LOGI("spiderGetAshmemFromWatchdog mmap");
            ashmem = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        }

    }

    return ashmem;
}


void ashmemTest(JNIEnv *env) {
    AshmBlock *ashm = spiderGetAshmemFromWatchdog(env, "ashmTest", 32);

    if(ashm != NULL) {
        u8 i;

        for(i = 0; i < 8; i++) {
            LOGI("%d", ashm->data[i]);
        }

        if(ashm->ashmStat == ASHM_EXIST) {
            LOGI("ashmem already exist");
        }
    }
}