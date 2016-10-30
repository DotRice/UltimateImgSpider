#include <jni.h>

void jniTryDeleteProjectAshmem(JNIEnv *env, jobject thiz);
void Java_com_gk969_UltimateImgSpider_WatchdogService_jniTryDeleteProjectAshmem(JNIEnv *env, jobject thiz){
    jniTryDeleteProjectAshmem(env, thiz);
}

void jniRestoreProjectData(JNIEnv *env, jobject thiz, jstring jDataFileFullPath);
void Java_com_gk969_UltimateImgSpider_WatchdogService_jniRestoreProjectData(JNIEnv *env, jobject thiz, jstring jDataFileFullPath){
    jniRestoreProjectData(env, thiz, jDataFileFullPath);
}

void jniStoreProjectData(JNIEnv *env, jobject thiz, jstring jDataFileFullPath);
void Java_com_gk969_UltimateImgSpider_WatchdogService_jniStoreProjectData(JNIEnv *env, jobject thiz, jstring jDataFileFullPath){
    jniStoreProjectData(env, thiz, jDataFileFullPath);
}

int jniGetAshmem(JNIEnv *env, jobject thiz, jstring jname, jint size);
int Java_com_gk969_UltimateImgSpider_WatchdogService_jniGetAshmem(JNIEnv *env, jobject thiz, jstring jname, jint size){
    return jniGetAshmem(env, thiz, jname, size);
}

jstring jniGetProjectInfo(JNIEnv *env, jobject thiz, jstring jDataFileFullPath, jlongArray jImgParam, jlongArray jPageParam);
jstring Java_com_gk969_UltimateImgSpider_WatchdogService_jniGetProjectInfo(JNIEnv *env, jobject thiz, jstring jDataFileFullPath, jlongArray jImgParam, jlongArray jPageParam){
    return jniGetProjectInfo(env, thiz, jDataFileFullPath, jImgParam, jPageParam);
}



jstring stringFromJNI(JNIEnv *env, jobject thiz, jstring jSrcStr);
jstring Java_com_gk969_UltimateImgSpider_SpiderService_stringFromJNI(JNIEnv *env, jobject thiz, jstring jSrcStr){
    return stringFromJNI(env, thiz, jSrcStr);
}

jstring jniSpiderInit(JNIEnv *env, jobject thiz, jlongArray jImgParam, jlongArray jPageParam);
jstring Java_com_gk969_UltimateImgSpider_SpiderService_jniSpiderInit(JNIEnv *env, jobject thiz, jlongArray jImgParam, jlongArray jPageParam){
    return jniSpiderInit(env, thiz, jImgParam, jPageParam);
}

jlong jniAddUrl(JNIEnv *env, jobject thiz, jstring jUrl, jbyteArray jMd5, jint jType, jlongArray jParam);
jlong Java_com_gk969_UltimateImgSpider_SpiderService_jniAddUrl(JNIEnv *env, jobject thiz, jstring jUrl, jbyteArray jMd5, jint jType, jlongArray jParam){
    return jniAddUrl(env, thiz, jUrl, jMd5, jType, jParam);
}

void jniSetSrcPageUrl(JNIEnv *env, jobject thiz,
                      jstring jPageUrl, jbyteArray jSrcPageUrlMd5, jlongArray jParam);
void Java_com_gk969_UltimateImgSpider_SpiderService_jniSetSrcPageUrl(JNIEnv *env, jobject thiz,
                      jstring jPageUrl, jbyteArray jSrcPageUrlMd5, jlongArray jParam){
    jniSetSrcPageUrl(env, thiz, jPageUrl, jSrcPageUrlMd5, jParam);
}

void jniSavePageTitle(JNIEnv *env, jobject thiz, jstring jCurPageTitle);
void Java_com_gk969_UltimateImgSpider_SpiderService_jniSavePageTitle(JNIEnv *env, jobject thiz, jstring jCurPageTitle){
    jniSavePageTitle(env, thiz, jCurPageTitle);
}

jstring jniFindNextPageUrl(JNIEnv *env, jobject thiz, jlongArray jPageParam);
jstring Java_com_gk969_UltimateImgSpider_SpiderService_jniFindNextPageUrl(JNIEnv *env, jobject thiz, jlongArray jPageParam){
    return jniFindNextPageUrl(env, thiz, jPageParam);
}

void jniOnImgUrlProcessed(JNIEnv *env, jobject thiz, jint jLastImgUrlAddr, jlongArray jImgParam);
void Java_com_gk969_UltimateImgSpider_SpiderService_jniOnImgUrlProcessed(JNIEnv *env, jobject thiz, jint jLastImgUrlAddr, jlongArray jImgParam){
    jniOnImgUrlProcessed(env, thiz, jLastImgUrlAddr, jImgParam);
}

jstring jniFindNextImgUrl(JNIEnv *env, jobject thiz, jlongArray jImgParam);
jstring Java_com_gk969_UltimateImgSpider_SpiderService_jniFindNextImgUrl(JNIEnv *env, jobject thiz, jlongArray jImgParam){
    return jniFindNextImgUrl(env, thiz, jImgParam);
}

void jniSaveImgStorageInfo(JNIEnv *env, jobject thiz, jint jImgUrlAddr, jint jPageUrlAddr, jlongArray jImgParam, jint jCurImgFileSize);
void Java_com_gk969_UltimateImgSpider_SpiderService_jniSaveImgStorageInfo(JNIEnv *env, jobject thiz, jint jImgUrlAddr, jint jPageUrlAddr, jlongArray jImgParam, jint jCurImgFileSize){
    jniSaveImgStorageInfo(env, thiz, jImgUrlAddr, jPageUrlAddr, jImgParam, jCurImgFileSize);
}