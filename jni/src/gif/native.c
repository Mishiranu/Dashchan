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
#include "gif.h"

jlong Java_com_mishiranu_dashchan_media_GifDecoder_init(JNIEnv * env, jobject this, jstring fileName) {
	return init(env, fileName);
}

void Java_com_mishiranu_dashchan_media_GifDecoder_destroy(JNIEnv * env, jobject this, jlong pointer) {
	destroy(env, pointer);
}

jint Java_com_mishiranu_dashchan_media_GifDecoder_getErrorCode(JNIEnv * env, jobject this, jlong pointer) {
	return getErrorCode(env, pointer);
}

void Java_com_mishiranu_dashchan_media_GifDecoder_getSummary(JNIEnv * env, jobject this, jlong pointer,
		jintArray output) {
	getSummary(env, pointer, output);
}

jint Java_com_mishiranu_dashchan_media_GifDecoder_draw(JNIEnv * env, jobject this, jlong pointer, jobject bitmap) {
	return draw(env, pointer, bitmap);
}