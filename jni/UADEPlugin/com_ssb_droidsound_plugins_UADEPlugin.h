/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_ssb_droidsound_plugins_UADEPlugin */

#ifndef _Included_com_ssb_droidsound_plugins_UADEPlugin
#define _Included_com_ssb_droidsound_plugins_UADEPlugin
#ifdef __cplusplus
extern "C" {
#endif
#undef com_ssb_droidsound_plugins_UADEPlugin_INFO_TITLE
#define com_ssb_droidsound_plugins_UADEPlugin_INFO_TITLE 0L
#undef com_ssb_droidsound_plugins_UADEPlugin_INFO_AUTHOR
#define com_ssb_droidsound_plugins_UADEPlugin_INFO_AUTHOR 1L
#undef com_ssb_droidsound_plugins_UADEPlugin_INFO_LENGTH
#define com_ssb_droidsound_plugins_UADEPlugin_INFO_LENGTH 2L
#undef com_ssb_droidsound_plugins_UADEPlugin_INFO_TYPE
#define com_ssb_droidsound_plugins_UADEPlugin_INFO_TYPE 3L
#undef com_ssb_droidsound_plugins_UADEPlugin_INFO_COPYRIGHT
#define com_ssb_droidsound_plugins_UADEPlugin_INFO_COPYRIGHT 4L
#undef com_ssb_droidsound_plugins_UADEPlugin_INFO_GAME
#define com_ssb_droidsound_plugins_UADEPlugin_INFO_GAME 5L
#undef com_ssb_droidsound_plugins_UADEPlugin_INFO_SUBTUNES
#define com_ssb_droidsound_plugins_UADEPlugin_INFO_SUBTUNES 6L
#undef com_ssb_droidsound_plugins_UADEPlugin_INFO_STARTTUNE
#define com_ssb_droidsound_plugins_UADEPlugin_INFO_STARTTUNE 7L
#undef com_ssb_droidsound_plugins_UADEPlugin_INFO_SUBTUNE_TITLE
#define com_ssb_droidsound_plugins_UADEPlugin_INFO_SUBTUNE_TITLE 8L
#undef com_ssb_droidsound_plugins_UADEPlugin_SIZEOF_INFO
#define com_ssb_droidsound_plugins_UADEPlugin_SIZEOF_INFO 9L
#undef com_ssb_droidsound_plugins_UADEPlugin_OPT_FILTER
#define com_ssb_droidsound_plugins_UADEPlugin_OPT_FILTER 1L
/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_init
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1init
  (JNIEnv *, jobject, jstring);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_exit
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1exit
  (JNIEnv *, jobject);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_setOption
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1setOption
  (JNIEnv *, jclass, jint, jint);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_canHandle
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1canHandle
  (JNIEnv *, jobject, jstring);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_load
 * Signature: ([BI)J
 */
JNIEXPORT jlong JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1load
  (JNIEnv *, jobject, jbyteArray, jint);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_loadFile
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1loadFile
  (JNIEnv *, jobject, jstring);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_unload
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1unload
  (JNIEnv *, jobject, jlong);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_getSoundData
 * Signature: (J[SI)I
 */
JNIEXPORT jint JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1getSoundData
  (JNIEnv *, jobject, jlong, jshortArray, jint);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_seekTo
 * Signature: (JI)Z
 */
JNIEXPORT jboolean JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1seekTo
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_setTune
 * Signature: (JI)Z
 */
JNIEXPORT jboolean JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1setTune
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_getStringInfo
 * Signature: (JI)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1getStringInfo
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     com_ssb_droidsound_plugins_UADEPlugin
 * Method:    N_getIntInfo
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_ssb_droidsound_plugins_UADEPlugin_N_1getIntInfo
  (JNIEnv *, jobject, jlong, jint);

#ifdef __cplusplus
}
#endif
#endif
