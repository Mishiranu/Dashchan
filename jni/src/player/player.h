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