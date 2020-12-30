#include "player.h"
#include "util.h"

#define JCALL(name) Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_##name

jlong JCALL(preInit)(JNIEnv * env, UNUSED jobject this, jint fd) {
	return preInit(env, fd);
}

void JCALL(init)(JNIEnv * env, UNUSED jobject this, jlong pointer, jobject nativeBridge, jboolean seekAnyFrame) {
	init(env, pointer, nativeBridge, seekAnyFrame);
}

void JCALL(destroy)(JNIEnv * env, UNUSED jobject this, jlong pointer, jboolean initOnly) {
	destroy(env, pointer, initOnly);
}

jint JCALL(getErrorCode)(UNUSED JNIEnv * env, UNUSED jobject this, jlong pointer) {
	return getErrorCode(pointer);
}

void JCALL(getSummary)(JNIEnv * env, UNUSED jobject this, jlong pointer, jintArray output) {
	getSummary(env, pointer, output);
}

jlong JCALL(getDuration)(UNUSED JNIEnv * env, UNUSED jobject this, jlong pointer) {
	return getDuration(pointer);
}

jlong JCALL(getPosition)(UNUSED JNIEnv * env, UNUSED jobject this, jlong pointer) {
	return getPosition(pointer);
}

void JCALL(setPosition)(JNIEnv * env, UNUSED jobject this, jlong pointer, jlong position) {
	setPosition(env, pointer, position);
}

void JCALL(setRange)(UNUSED JNIEnv * env, UNUSED jobject this,
		jlong pointer, jlong start, jlong end, jlong total) {
	setRange(pointer, start, end, total);
}

void JCALL(setCancelSeek)(UNUSED JNIEnv * env, UNUSED jobject this, jlong pointer, jboolean cancelSeek) {
	setCancelSeek(pointer, cancelSeek);
}

void JCALL(setPlaying)(UNUSED JNIEnv * env, UNUSED jobject this, jlong pointer, jboolean playing) {
	setPlaying(pointer, playing);
}

void JCALL(setSurface)(JNIEnv * env, UNUSED jobject this, jlong pointer, jobject surface) {
	setSurface(env, pointer, surface);
}

jintArray JCALL(getCurrentFrame)(JNIEnv * env, UNUSED jobject this, jlong pointer, jintArray dimensions) {
	return getCurrentFrame(env, pointer, dimensions);
}

jobjectArray JCALL(getMetadata)(JNIEnv * env, UNUSED jobject this, jlong pointer) {
	return getMetadata(env, pointer);
}

jint JNI_OnLoad(JavaVM * javaVM, UNUSED void * reserved) {
	initLibs(javaVM);
	return JNI_VERSION_1_6;
}
