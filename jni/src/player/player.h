#ifndef PLAYER_H
#define PLAYER_H

jlong init(JNIEnv *, jobject, jboolean);
void destroy(JNIEnv *, jlong);

jint getErrorCode(JNIEnv *, jlong);
void getSummary(JNIEnv *, jlong, jintArray);

jlong getDuration(JNIEnv *, jlong);
jlong getPosition(JNIEnv *, jlong);

void setPosition(JNIEnv *, jlong, jlong);
void setPlaying(JNIEnv *, jlong, jboolean);
void setSurface(JNIEnv *, jlong, jobject);

jintArray getCurrentFrame(JNIEnv *, jlong);
jobjectArray getTechnicalInfo(JNIEnv *, jlong);

void initLibs(JavaVM * javaVM);

#endif // PLAYER_H
