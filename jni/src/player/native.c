/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include "player.h"

jlong Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_init(JNIEnv * env, jobject this, jobject nativeBridge,
		jboolean seekAnyFrame) {
	return init(env, nativeBridge, seekAnyFrame);
}

void Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_destroy(JNIEnv * env, jobject this, jlong pointer) {
	destroy(env, pointer);
}

jint Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_getErrorCode(JNIEnv * env, jobject this, jlong pointer) {
	return getErrorCode(env, pointer);
}

void Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_getSummary(JNIEnv * env, jobject this, jlong pointer,
		jintArray output) {
	getSummary(env, pointer, output);
}

jlong Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_getDuration(JNIEnv * env, jobject this, jlong pointer) {
	return getDuration(env, pointer);
}

jlong Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_getPosition(JNIEnv * env, jobject this, jlong pointer) {
	return getPosition(env, pointer);
}

void Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_setPosition(JNIEnv * env, jobject this, jlong pointer,
		jlong position) {
	setPosition(env, pointer, position);
}

void Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_setPlaying(JNIEnv * env, jobject this, jlong pointer,
		jboolean playing) {
	setPlaying(env, pointer, playing);
}

void Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_setSurface(JNIEnv * env, jobject this, jlong pointer,
		jobject surface) {
	setSurface(env, pointer, surface);
}

jintArray Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_getCurrentFrame(JNIEnv * env, jobject this,
		jlong pointer) {
	return getCurrentFrame(env, pointer);
}

jobjectArray Java_com_mishiranu_dashchan_media_VideoPlayer_00024Holder_getTechnicalInfo(JNIEnv * env, jobject this,
		jlong pointer) {
	return getTechnicalInfo(env, pointer);
}

jint JNI_OnLoad(JavaVM * javaVM, void * reserved) {
	initLibs(javaVM);
	return JNI_VERSION_1_6;
}