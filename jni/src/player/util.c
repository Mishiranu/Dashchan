#include "util.h"

#include <stdlib.h>
#include <string.h>

void queueInit(Queue * queue) {
	memset(queue, 0, sizeof(Queue));
}

void queueClear(Queue * queue, void callback(void * data)) {
	QueueItem * queueItem = queue->first;
	while (queueItem) {
		if (callback && queueItem->data) {
			callback(queueItem->data);
		}
		QueueItem * nextQueueItem = queueItem->next;
		free(queueItem);
		queueItem = nextQueueItem;
	}
	queue->first = NULL;
	queue->last = NULL;
	queue->count = 0;
}

void queueAdd(Queue * queue, void * data) {
	QueueItem * queueItem = malloc(sizeof(QueueItem));
	queueItem->data = data;
	queueItem->next = NULL;
	if (queue->last) {
		queue->last->next = queueItem;
	} else {
		queue->first = queueItem;
	}
	queue->last = queueItem;
	queue->count++;
}

void * queueGet(Queue * queue) {
	void * data = NULL;
	QueueItem * queueItem = queue->first;
	if (queueItem) {
		queue->first = queueItem->next;
		if (!queue->first) {
			queue->last = NULL;
		}
		queue->count--;
		data = queueItem->data;
		free(queueItem);
	}
	return data;
}

void blockingQueueInit(BlockingQueue * blockingQueue) {
	memset(blockingQueue, 0, sizeof(BlockingQueue));
	queueInit(&blockingQueue->queue);
	pthread_cond_init(&blockingQueue->cond, NULL);
	pthread_mutex_init(&blockingQueue->mutex, NULL);
}

void blockingQueueInterrupt(BlockingQueue * blockingQueue) {
	pthread_mutex_lock(&blockingQueue->mutex);
	blockingQueue->interrupted = 1;
	pthread_cond_broadcast(&blockingQueue->cond);
	pthread_mutex_unlock(&blockingQueue->mutex);
}

void blockingQueueClear(BlockingQueue * blockingQueue, void callback(void * data)) {
	pthread_mutex_lock(&blockingQueue->mutex);
	queueClear(&blockingQueue->queue, callback);
	pthread_mutex_unlock(&blockingQueue->mutex);
}

void blockingQueueDestroy(BlockingQueue * blockingQueue, void callback(void * data)) {
	blockingQueueClear(blockingQueue, callback);
	pthread_cond_destroy(&blockingQueue->cond);
	pthread_mutex_destroy(&blockingQueue->mutex);
}

void blockingQueueAdd(BlockingQueue * blockingQueue, void * data) {
	pthread_mutex_lock(&blockingQueue->mutex);
	queueAdd(&blockingQueue->queue, data);
	pthread_cond_broadcast(&blockingQueue->cond);
	pthread_mutex_unlock(&blockingQueue->mutex);
}

void * blockingQueueGet(BlockingQueue * blockingQueue, int wait) {
	void * data = NULL;
	pthread_mutex_lock(&blockingQueue->mutex);
	while (!blockingQueue->interrupted) {
		if (blockingQueue->queue.first) {
			data = queueGet(&blockingQueue->queue);
			break;
		} else if (wait) {
			pthread_cond_wait(&blockingQueue->cond, &blockingQueue->mutex);
		} else {
			break;
		}
	}
	pthread_mutex_unlock(&blockingQueue->mutex);
	return data;
}

int blockingQueueCount(BlockingQueue * blockingQueue) {
	return blockingQueue->queue.count;
}

void bufferQueueInit(BufferQueue * bufferQueue, int bufferSize, int maxCount) {
	queueInit(&bufferQueue->freeQueue);
	queueInit(&bufferQueue->busyQueue);
	bufferQueue->bufferSize = bufferSize;
	for (int i = 0; i < maxCount; i++) {
		uint8_t * buffer = malloc(bufferSize);
		BufferItem * bufferItem = malloc(sizeof(BufferItem));
		bufferItem->buffer = buffer;
		bufferItem->bufferSize = bufferSize;
		bufferItem->extra = NULL;
		queueAdd(&bufferQueue->freeQueue, bufferItem);
	}
}

void bufferQueueClear(BufferQueue * bufferQueue, void callback(BufferItem * bufferItem)) {
	while (1) {
		BufferItem * bufferItem = queueGet(&bufferQueue->busyQueue);
		if (!bufferItem) {
			break;
		}
		if (callback) {
			callback(bufferItem);
		}
		queueAdd(&bufferQueue->freeQueue, bufferItem);
	}
}

static void bufferQueueDestroyItem(void * data) {
	BufferItem * bufferItem = data;
	free(bufferItem->buffer);
	free(bufferItem);
}

void bufferQueueDestroy(BufferQueue * bufferQueue, void callback(BufferItem * bufferItem)) {
	bufferQueueClear(bufferQueue, callback);
	queueClear(&bufferQueue->freeQueue, bufferQueueDestroyItem);
	queueClear(&bufferQueue->busyQueue, NULL);
}

void bufferQueueExtend(BufferQueue * bufferQueue, int bufferSize)
{
	if (bufferSize > bufferQueue->bufferSize) {
		bufferQueue->bufferSize = bufferSize;
	}
}

BufferItem * bufferQueuePrepare(BufferQueue * bufferQueue) {
	BufferItem * bufferItem = queueGet(&bufferQueue->freeQueue);
	if (bufferItem && bufferItem->bufferSize < bufferQueue->bufferSize) {
		uint8_t * buffer = malloc(bufferQueue->bufferSize);
		memcpy(buffer, bufferItem->buffer, bufferItem->bufferSize);
		free(bufferItem->buffer);
		bufferItem->buffer = buffer;
		bufferItem->bufferSize = bufferQueue->bufferSize;
	}
	return bufferItem;
}

void bufferQueueAdd(BufferQueue * bufferQueue, BufferItem * bufferItem) {
	queueAdd(&bufferQueue->busyQueue, bufferItem);
}

BufferItem * bufferQueueSeize(BufferQueue * bufferQueue) {
	return queueGet(&bufferQueue->busyQueue);
}

void bufferQueueRelease(BufferQueue * bufferQueue, BufferItem * bufferItem) {
	queueAdd(&bufferQueue->freeQueue, bufferItem);
}

int bufferQueueCount(BufferQueue * bufferQueue) {
	return bufferQueue->busyQueue.count;
}

void sparseArrayInit(SparseArray * sparseArray, int initialCapacity) {
	sparseArray->items = malloc(sizeof(SparseArrayItem) * initialCapacity);
	sparseArray->capacity = initialCapacity;
	sparseArray->count = 0;
}

void sparseArrayDestroy(SparseArray * sparseArray, SparseArrayDestroyCallback callback) {
	if (callback) {
		for (int i = 0; i < sparseArray->count; i++) {
			SparseArrayItem sparseArrayItem = sparseArray->items[i];
			if (sparseArrayItem.data) {
				callback(sparseArrayItem.data);
			}
		}
	}
	free(sparseArray->items);
	sparseArray->count = 0;
}

void sparseArrayAdd(SparseArray * sparseArray, int index, void * data) {
	if (sparseArray->count == sparseArray->capacity) {
		int newCapacity = sparseArray->capacity * 2;
		SparseArrayItem * items = malloc(sizeof(SparseArrayItem) * newCapacity);
		memcpy(items, sparseArray->items, sizeof(SparseArrayItem) * sparseArray->capacity);
		free(sparseArray->items);
		sparseArray->capacity = newCapacity;
		sparseArray->items = items;
	}
	sparseArray->items[sparseArray->count].data = data;
	sparseArray->items[sparseArray->count].index = index;
	sparseArray->count++;
}

void * sparseArrayGet(SparseArray * sparseArray, int index) {
	void * data = NULL;
	for (int i = 0; i < sparseArray->count; i++) {
		SparseArrayItem sparseArrayItem = sparseArray->items[i];
		if (sparseArrayItem.index == index) {
			data = sparseArrayItem.data;
			break;
		}
	}
	return data;
}

int64_t getTime() {
	struct timeval now;
	gettimeofday(&now, NULL);
	return (int64_t) now.tv_sec * 1000 + now.tv_usec / 1000;
}

int64_t getTimeUs() {
	struct timeval now;
	gettimeofday(&now, NULL);
	return (int64_t) now.tv_sec * 1000000 + now.tv_usec;
}

void condBroadcastLocked(pthread_cond_t * cond, pthread_mutex_t * mutex) {
	pthread_mutex_lock(mutex);
	pthread_cond_broadcast(cond);
	pthread_mutex_unlock(mutex);
}

int condSleepUntilMs(pthread_cond_t * cond, pthread_mutex_t * mutex, int64_t timeMs) {
	struct timespec wait;
	wait.tv_sec = timeMs / 1000;
	wait.tv_nsec = (timeMs % 1000) * 1000000;
	pthread_cond_timedwait(cond, mutex, &wait);
	struct timeval now;
	gettimeofday(&now, NULL);
	int64_t nowMs = (int64_t) now.tv_sec * 1000 + (now.tv_usec + 999) / 1000;
	return nowMs >= timeMs;
}

int32_t min32(int32_t a, int32_t b) {
	return a < b ? a : b;
}

int64_t min64(int64_t a, int64_t b) {
	return a < b ? a : b;
}

int64_t max64(int64_t a, int64_t b) {
	return a > b ? a : b;
}
