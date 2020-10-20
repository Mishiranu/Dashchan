#ifndef PLAYER_UTIL_H
#define PLAYER_UTIL_H

#include <pthread.h>

#define UNUSED __attribute__((unused))

#ifdef DEBUG_VERBOSE
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_DEBUG, "Dashchan", __VA_ARGS__)
#else
#define LOG(...)
#endif

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

typedef struct SparseArrayItem SparseArrayItem;
typedef struct SparseArray SparseArray;

struct SparseArrayItem {
	int index;
	void * data;
};

struct SparseArray {
	SparseArrayItem * items;
	int capacity;
	int count;
};

typedef void (*SparseArrayDestroyCallback)(void *);
void sparseArrayInit(SparseArray * sparseArray, int initialCapacity);
void sparseArrayDestroy(SparseArray * sparseArray, SparseArrayDestroyCallback callback);
void sparseArrayAdd(SparseArray * sparseArray, int index, void * data);
void * sparseArrayGet(SparseArray * sparseArray, int index);

int64_t getTime();
int64_t getTimeUs();

void condBroadcastLocked(pthread_cond_t * cond, pthread_mutex_t * mutex);
int condSleepUntilMs(pthread_cond_t * cond, pthread_mutex_t * mutex, int64_t timeMs);

int32_t min32(int32_t a, int32_t b);
int64_t min64(int64_t a, int64_t b);
int64_t max64(int64_t a, int64_t b);

#endif // PLAYER_UTIL_H
