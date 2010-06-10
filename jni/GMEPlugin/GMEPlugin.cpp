
#include <stdlib.h>
#include <stdio.h>

#include <jni.h>

#include "GMEPlugin.h"

#define INFO_TITLE 1
#define INFO_AUTHOR 2
#define INFO_LENGTH 3
#define INFO_TYPE 4
#define INFO_COPYRIGHT 5
#define INFO_SUBSONGS 6
#define INFO_STARTSONG 7

#include "gme/gme.h"

//#include "log.h"

JNIEXPORT jboolean JNICALL Java_com_ssb_droidsound_GMEPlugin_N_1canHandle(JNIEnv *env, jobject obj, jstring name)
{
	return true;
}

Music_Emu *emu = NULL;
int currentSong = 0;

JNIEXPORT jboolean JNICALL Java_com_ssb_droidsound_GMEPlugin_N_1load(JNIEnv *env, jobject obj, jbyteArray bArray, int size)
{
	jbyte *ptr = env->GetByteArrayElements(bArray, NULL);
	emu = NULL;

	gme_err_t err = gme_open_data(ptr, size, &emu, 44100);


	if(!err)
		err = gme_start_track(emu, 0);


	env->ReleaseByteArrayElements(bArray, ptr, 0);
	return (err == NULL);
}

JNIEXPORT void JNICALL Java_com_ssb_droidsound_GMEPlugin_N_1unload(JNIEnv *env, jobject obj)
{
	if(emu)
		gme_delete(emu);
	emu = NULL;
}

JNIEXPORT jboolean JNICALL Java_com_ssb_droidsound_GMEPlugin_N_1setSong(JNIEnv *env, jobject obj, jint song)
{
	gme_err_t err = gme_start_track(emu, song);
	currentSong = song;
	return true;
}


JNIEXPORT jint JNICALL Java_com_ssb_droidsound_GMEPlugin_N_1getSoundData(JNIEnv *env, jobject obj, jshortArray bArray, int size)
{
	jshort *ptr = env->GetShortArrayElements(bArray, NULL);
	//__android_log_print(ANDROID_LOG_VERBOSE, "GMEPlugin", "Getting %d shorts from %p", size, emu);
	gme_err_t err = gme_play(emu, size, ptr);
	//__android_log_print(ANDROID_LOG_VERBOSE, "GMEPlugin", "Result %d", err);
	env->ReleaseShortArrayElements(bArray, ptr, 0);
	return size;
}

JNIEXPORT jboolean JNICALL Java_com_ssb_droidsound_GMEPlugin_N_1seekTo(JNIEnv *env, jobject obj, int where)
{
	gme_seek(emu, where);
	return true;
}


JNIEXPORT jstring JNICALL Java_com_ssb_droidsound_GMEPlugin_N_1getStringInfo(JNIEnv *env, jobject obj, jint what)
{
	track_info_t track;
	gme_err_t err = gme_track_info(emu, &track, 0);

	switch(what)
	{
	case INFO_TITLE:
		return env->NewStringUTF(track.song);
	case INFO_AUTHOR:
		return env->NewStringUTF(track.author);
	case INFO_COPYRIGHT:
		return env->NewStringUTF(track.copyright);
	case INFO_TYPE:
		return env->NewStringUTF(track.system);
	}
	return 0;
}

JNIEXPORT jint JNICALL Java_com_ssb_droidsound_GMEPlugin_N_1getIntInfo(JNIEnv *env, jobject obj, jint what)
{
	track_info_t track;
	gme_err_t err = gme_track_info(emu, &track, currentSong);

	switch(what)
	{
	case INFO_LENGTH:
		return track.length;
	case INFO_SUBSONGS:
		return track.track_count;
	case INFO_STARTSONG:
		return 0;
	}
	return -1;

}