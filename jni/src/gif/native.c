#include "gif.h"
#include "util.h"

#define JCALL(name) Java_com_mishiranu_dashchan_media_GifDecoder_##name

jlong JCALL(init)(JNIEnv * env, UNUSED jobject this, jstring fileName) {
	return init(env, fileName);
}

void JCALL(destroy)(UNUSED JNIEnv * env, UNUSED jobject this, jlong pointer) {
	destroy(pointer);
}

jint JCALL(getErrorCode)(UNUSED JNIEnv * env, UNUSED jobject this, jlong pointer) {
	return getErrorCode(pointer);
}

void JCALL(getSummary)(JNIEnv * env, UNUSED jobject this, jlong pointer, jintArray output) {
	getSummary(env, pointer, output);
}

jint JCALL(draw)(JNIEnv * env, UNUSED jobject this, jlong pointer, jobject bitmap) {
	return draw(env, pointer, bitmap);
}
