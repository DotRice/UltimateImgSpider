#ifndef ASHMEM_H
#define ASHMEM_H

#define ASHM_NAME_SIZE  32

#define ASHM_EXIST  0x12345678
#pragma pack(1)
typedef struct
{
    u32 ashmStat;
    u8 data[4];
} AshmBlock;

typedef struct
{
    char name[ASHM_NAME_SIZE];
    int size;
    u32 ashmStat;
} AshmParaStore;

#pragma pack()

typedef struct t_ashm
{
    char name[ASHM_NAME_SIZE];
    int size;
    int fd;
    AshmBlock *ashmem;
    struct t_ashm *next;
} AshmNode;

extern jobject AshmAllocObjectInstance;

#endif