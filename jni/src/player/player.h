#ifndef PLAYER_H
#define PLAYER_H

#include <jni.h>

jlong preInit(JNIEnv *, jint);
void init(JNIEnv *, jlong, jobject, jboolean);
void destroy(JNIEnv *, jlong, jboolean);

jint getErrorCode(jlong);
void getSummary(JNIEnv *, jlong, jintArray);

jlong getDuration(jlong);
jlong getPosition(jlong);
void setPosition(JNIEnv *, jlong, jlong);

void setRange(jlong, jlong, jlong, jlong);
void setCancelSeek(jlong, jboolean);

void setPlaying(jlong, jboolean);
void setSurface(JNIEnv *, jlong, jobject);

jintArray getCurrentFrame(JNIEnv *, jlong, jintArray);
jobjectArray getMetadata(JNIEnv *, jlong);

void initLibs(JavaVM * javaVM);

#endif // PLAYER_H
