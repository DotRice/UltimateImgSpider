#ifndef TYPEDEF_H
#define TYPEDEF_H

typedef unsigned char 		u8;
typedef unsigned short 		u16;
typedef unsigned int 		u32;
typedef unsigned long long	u64;

typedef char 				s8;
typedef short 				s16;
typedef int 				s32;
typedef long long 			s64;

typedef u8 bool;

#define false 0
#define true 1


#if defined(__arm__)
#if defined(__ARM_ARCH_7A__)
#if defined(__ARM_NEON__)
#define ABI "armeabi-v7a/NEON"
#else
#define ABI "armeabi-v7a"
#endif
#else
#define ABI "armeabi"
#endif
#elif defined(__i386__)
#define ABI "x86"
#elif defined(__mips__)
#define ABI "mips"
#else
#define ABI "unknown"
#endif


#endif
