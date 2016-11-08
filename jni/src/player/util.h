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

#ifndef PLAYER_UTIL_H
#define PLAYER_UTIL_H

#include <jni.h>
#include <pthread.h>
#include <stdarg.h>

#include <android/log.h>
#define log(...) __android_log_print(ANDROID_LOG_DEBUG, "Dashchan", __VA_ARGS__)

typedef struct QueueItem QueueItem;
typedef struct Queue Queue;

struct QueueItem {
	void * data;
	QueueItem * next;
};

struct Queue {
	QueueItem * first;
	QueueItem * last;
	int count;
};

void queueInit(Queue * queue);
void queueClear(Queue * queue, void callback(void * data));
void queueDestroy(Queue * queue, void callback(void * data));
void queueAdd(Queue * queue, void * data);
void * queueGet(Queue * queue);

typedef struct BlockingQueue BlockingQueue;

struct BlockingQueue {
	Queue queue;
	int interrupted;
	pthread_cond_t cond;
	pthread_mutex_t mutex;
};

void blockingQueueInit(BlockingQueue * blockingQueue);
void blockingQueueInterrupt(BlockingQueue * blockingQueue);
void blockingQueueClear(BlockingQueue * blockingQueue, void callback(void * data));
void blockingQueueDestroy(BlockingQueue * blockingQueue, void callback(void * data));
void blockingQueueAdd(BlockingQueue * blockingQueue, void * data);
void * blockingQueueGet(BlockingQueue * blockingQueue, int wait);
int blockingQueueCount(BlockingQueue * blockingQueue);

typedef struct BufferItem BufferItem;
typedef struct BufferQueue BufferQueue;

struct BufferItem {
	uint8_t * buffer;
	int bufferSize;
	void * extra;
};

struct BufferQueue {
	Queue freeQueue;
	Queue busyQueue;
	int bufferSize;
};

void bufferQueueInit(BufferQueue * bufferQueue, int bufferSize, int maxCount);
void bufferQueueClear(BufferQueue * bufferQueue, void callback(BufferItem * bufferItem));
void bufferQueueDestroy(BufferQueue * bufferQueue, void callback(BufferItem * bufferItem));
void bufferQueueExtend(BufferQueue * bufferQueue, int bufferSize);
BufferItem * bufferQueuePrepare(BufferQueue * bufferQueue);
void bufferQueueAdd(BufferQueue * bufferQueue, BufferItem * bufferItem);
BufferItem * bufferQueueSeize(BufferQueue * bufferQueue);
void bufferQueueRelease(BufferQueue * bufferQueue, BufferItem * bufferItem);
int bufferQueueCount(BufferQueue * bufferQueue);

typedef struct SparceArrayItem SparceArrayItem;
typedef struct SparceArray SparceArray;

struct SparceArrayItem {
	int index;
	void * data;
};

struct SparceArray {
	SparceArrayItem * items;
	int capacity;
	int count;
};

void sparceArrayInit(SparceArray * sparceArray, int initialCapacity);
void sparceArrayDestroy(SparceArray * sparceArray, void callback(void * data));
void sparceArrayAdd(SparceArray * sparceArray, int index, void * data);
void * sparceArrayGet(SparceArray * sparceArray, int index);

#define sparceArrayDestroyEach(a, b) {void callback(void * data) {b;} sparceArrayDestroy(a, callback);}

int64_t getTime();
int64_t getTimeUs();

void condBroadcastLocked(pthread_cond_t * cond, pthread_mutex_t * mutex);
int condSleepUntilMs(pthread_cond_t * cond, pthread_mutex_t * mutex, int64_t timeMs);

int32_t min32(int32_t a, int32_t b);
int64_t min64(int64_t a, int64_t b);
int64_t max64(int64_t a, int64_t b);

#endif // PLAYER_UTIL_H