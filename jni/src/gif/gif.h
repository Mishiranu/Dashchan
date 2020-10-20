#ifndef GIF_H
#define GIF_H

#include <jni.h>

jlong init(JNIEnv *, jstring);
void destroy(jlong);

jint getErrorCode(jlong);
void getSummary(JNIEnv *, jlong, jintArray);

jint draw(JNIEnv *, jlong, jobject);

#endif // GIF_H
