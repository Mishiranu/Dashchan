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

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>
#include <libavutil/imgutils.h>
#include <libavutil/pixdesc.h>
#include <libavutil/opt.h>

#include <libyuv.h>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <android/native_window_jni.h>

#include <jni.h>
#include <unistd.h>
#include <pthread.h>
#include <inttypes.h>

#include "player.h"
#include "util.h"

#define unlockAndGoTo(mutex, label) {pthread_mutex_unlock(mutex); goto label;}
#define getStream(index) player->formatContext->streams[index]
#define sendBridgeMessage(what) (*env)->CallVoidMethod(env, player->nativeBridge, bridge->methodOnMessage, what)

#define jlongCast(addr) (jlong) (long) addr
#define pointerCast(addr) (void *) (long) addr

#define ERROR_LOAD_IO 1
#define ERROR_LOAD_FORMAT 2
#define ERROR_START_THREAD 3
#define ERROR_FIND_STREAM_INFO 4
#define ERROR_FIND_STREAM 5
#define ERROR_FIND_CODEC 6
#define ERROR_OPEN_CODEC 7

#define BRIDGE_MESSAGE_PLAYBACK_COMPLETE 1
#define BRIDGE_MESSAGE_SIZE_CHANGED 2
#define BRIDGE_MESSAGE_START_SEEKING 3
#define BRIDGE_MESSAGE_END_SEEKING 4

#define UNDEFINED -1
#define GAINING_THRESHOLD 100
#define AUDIO_MAX_ENQUEUE_SIZE 256
#define WINDOW_FORMAT_YV12 0x32315659
#define MAX_FPS 60

#define WRITE_LOGS 0

#if WRITE_LOGS
#define logp(...) log(__VA_ARGS__)
#else
#define logp(...)
#endif

static JavaVM * loadJavaVM;
static SLEngineItf slEngine;

typedef struct Player Player;
typedef struct Bridge Bridge;
typedef struct PacketHolder PacketHolder;
typedef struct AudioBuffer AudioBuffer;
typedef struct VideoFrameExtra VideoFrameExtra;
typedef struct ScaleHolder ScaleHolder;

struct Player
{
	int errorCode;
	int seekAnyFrame;
	
	jobject nativeBridge;
	SparceArray bridges;
	
	AVIOContext * ioContext;
	AVFormatContext * formatContext;
	
	int interrupt;
	pthread_t decodePacketsThread;
	pthread_t decodeAudioThread;
	pthread_t decodeVideoThread;
	pthread_t drawThread;
	pthread_mutex_t decodePacketsReadMutex;
	pthread_cond_t decodePacketsFlowCond;
	pthread_mutex_t decodePacketsFlowMutex;
	pthread_mutex_t decodeAudioFrameMutex;
	pthread_mutex_t decodeVideoFrameMutex;
	
	int playing;
	int decodeFinished;
	int audioFinished;
	int videoFinished;
	pthread_cond_t playFinishCond;
	pthread_mutex_t playFinishMutex;
	
	int audioStreamIndex;
	int videoStreamIndex;
	AVCodecContext * audioCodecContext;
	AVCodecContext * videoCodecContext;
	
	BlockingQueue audioPacketQueue;
	BlockingQueue videoPacketQueue;
	
	SLObjectItf slOutputMix;
	SLObjectItf slPlayer;
	SLPlayItf slPlay;
	SLAndroidSimpleBufferQueueItf slQueue;
	int resampleSampleRate;
	uint64_t resampleChannels;
	int audioBufferNeedEnqueueAfterDecode;
	BlockingQueue audioBufferQueue;
	AudioBuffer * audioBuffer;
	pthread_cond_t audioSleepCond;
	pthread_cond_t audioBufferCond;
	pthread_mutex_t audioSleepBufferMutex;
	
	pthread_cond_t videoSleepCond;
	pthread_mutex_t videoSleepDrawMutex;
	pthread_cond_t videoQueueCond;
	pthread_mutex_t videoQueueMutex;
	BufferQueue * videoBufferQueue;
	ANativeWindow * videoWindow;
	uint8_t * videoLastBuffer;
	int videoUseLibyuv;
	int videoLastBufferWidth;
	int videoLastBufferHeight;
	int videoLastBufferSize;
	int videoFormat;
	
	int64_t audioPosition;
	int64_t videoPosition;
	int audioPositionNotSync;
	int videoPositionNotSync;
	int64_t startTime;
	int64_t pausedPosition;
	int64_t lastDrawTimes[2];
	int ignoreReadFrame;
	int audioIgnoreWorkFrame;
	int videoIgnoreWorkFrame;
	int drawIgnoreWorkFrame;
};

struct Bridge
{
	JNIEnv * env;
	jmethodID methodGetBuffer;
	jmethodID methodOnRead;
	jmethodID methodOnSeek;
	jmethodID methodOnMessage;
};

struct PacketHolder
{
	AVPacket * packet;
	int finish;
};

struct AudioBuffer
{
	uint8_t * buffer;
	int size;
	int index;
	int64_t position;
	int64_t divider;
};

struct VideoFrameExtra
{
	int width;
	int height;
	int64_t position;
};

struct ScaleHolder
{
	int bufferSize;
	uint8_t * scaleBuffer;
	uint8_t * scaleData[4];
	int scaleLinesize[4];
};

static Bridge * obtainBridge(Player * player, JNIEnv * env)
{
	int index = pthread_self();
	Bridge * bridge = sparceArrayGet(&player->bridges, index);
	if (bridge == NULL)
	{
		bridge = malloc(sizeof(Bridge));
		jclass class = (*env)->GetObjectClass(env, player->nativeBridge);
		bridge->env = env;
		bridge->methodGetBuffer = (*env)->GetMethodID(env, class, "getBuffer", "()[B");
		bridge->methodOnRead = (*env)->GetMethodID(env, class, "onRead", "(I)I");
		bridge->methodOnSeek = (*env)->GetMethodID(env, class, "onSeek", "(JI)J");
		bridge->methodOnMessage = (*env)->GetMethodID(env, class, "onMessage", "(I)V");
		sparceArrayAdd(&player->bridges, index, bridge);
	}
	return bridge;
}

static int getBytesPerPixel(int videoFormat)
{
	switch (videoFormat)
	{
		case AV_PIX_FMT_YUV420P: return 1;
		case AV_PIX_FMT_RGBA: return 4;
		case AV_PIX_FMT_RGB565LE: return 2;
	}
	return 0;
}

static void packetQueueFreeCallback(void * data)
{
	PacketHolder * packetHolder = (PacketHolder *) data;
	if (packetHolder->packet != NULL)
	{
		av_packet_unref(packetHolder->packet);
		free(packetHolder->packet);
	}
	free(packetHolder);
}

static void audioBufferQueueFreeCallback(void * data)
{
	AudioBuffer * audioBuffer = (AudioBuffer *) data;
	if (audioBuffer != NULL)
	{
		av_freep(&audioBuffer->buffer);
		free(audioBuffer);
	}
}

static void videoBufferQueueFreeCallback(BufferItem * bufferItem)
{
	if (bufferItem->extra != NULL)
	{
		free(bufferItem->extra);
		bufferItem->extra = NULL;
	}
}

static void updateAudioPositionSurrogate(Player * player, int64_t position, int forceUpdate)
{
	if (forceUpdate || player->audioPositionNotSync)
	{
		player->startTime = getTime() - position;
		if ((player->audioStreamIndex == UNDEFINED || player->audioFinished) && !forceUpdate)
		{
			player->audioPositionNotSync = 0;
		}
	}
}

static int64_t calculatePosition(Player * player, int mayCalculateStartTime)
{
	if (player->audioStreamIndex == UNDEFINED || player->audioFinished)
	{
		if (player->playing)
		{
			if (mayCalculateStartTime || !player->videoFinished) return getTime() - player->startTime;
			else return player->videoPosition;
		}
		else return player->pausedPosition;
	}
	else return player->audioPosition;
}

static void markStreamFinished(Player * player, int video)
{
	if (video)
	{
		if (bufferQueueCount(player->videoBufferQueue) == 0 && blockingQueueCount(&player->videoPacketQueue) == 0)
		{
			player->videoFinished = 1;
			condBroadcastLocked(&player->playFinishCond, &player->playFinishMutex);
		}
	}
	else
	{
		if (player->audioBuffer == NULL && blockingQueueCount(&player->audioBufferQueue) == 0
				&& blockingQueueCount(&player->audioPacketQueue) == 0)
		{
			player->audioFinished = 1;
			condBroadcastLocked(&player->playFinishCond, &player->playFinishMutex);
		}
	}
}

static int64_t calculateFrameTime(int64_t waitTime)
{
	return getTime() + waitTime - min64(max64(waitTime / 2, 25), 100);
}

static int enqueueAudioBuffer(Player * player)
{
	if (!player->playing)
	{
		player->audioBufferNeedEnqueueAfterDecode = 1;
		return 0;
	}
	int64_t endAudioPosition = -1;
	if (player->audioBuffer != NULL)
	{
		AudioBuffer * audioBuffer = player->audioBuffer;
		if (audioBuffer->index >= audioBuffer->size)
		{
			endAudioPosition = audioBuffer->position + audioBuffer->size * 1000 / audioBuffer->divider;
			player->audioBuffer = NULL;
			av_freep(&audioBuffer->buffer);
			free(audioBuffer);
		}
	}
	if (player->audioBuffer == NULL) player->audioBuffer = blockingQueueGet(&player->audioBufferQueue, 0);
	if (player->audioBuffer != NULL)
	{
		AudioBuffer * audioBuffer = player->audioBuffer;
		if (audioBuffer->position >= 0)
		{
			player->audioPosition = audioBuffer->position + audioBuffer->index * 1000 / audioBuffer->divider;
			player->audioPositionNotSync = 0;
			logp("play audio %" PRId64, player->audioPosition);
		}
		int enqueueSize = min32(audioBuffer->size - audioBuffer->index, AUDIO_MAX_ENQUEUE_SIZE);
		(*player->slQueue)->Enqueue(player->slQueue, audioBuffer->buffer + audioBuffer->index, enqueueSize);
		audioBuffer->index += enqueueSize;
		player->audioBufferNeedEnqueueAfterDecode = 0;
		return 1;
	}
	else
	{
		player->audioBufferNeedEnqueueAfterDecode = 1;
		if (blockingQueueCount(&player->audioPacketQueue) == 0 && endAudioPosition >= 0)
		{
			updateAudioPositionSurrogate(player, endAudioPosition, 1);
		}
		return 0;
	}
}

static void audioPlayerCallback(SLAndroidSimpleBufferQueueItf slQueue, void * context)
{
	Player * player = (Player *) context;
	if (player->interrupt) return;
	logp("audio callback");
	pthread_mutex_lock(&player->audioSleepBufferMutex);
	int result = enqueueAudioBuffer(player);
	if (result) pthread_cond_broadcast(&player->audioBufferCond);
	pthread_mutex_unlock(&player->audioSleepBufferMutex);
	markStreamFinished(player, 0);
}

static void * performDecodeAudio(void * data)
{
	Player * player = (Player *) data;
	player->audioBufferNeedEnqueueAfterDecode = 1;
	AVStream * stream = getStream(player->audioStreamIndex);
	AVCodecContext * codecContext = player->audioCodecContext;
	AVFrame * frame = av_frame_alloc();
	SwrContext * resampleContext = swr_alloc();
	PacketHolder * packetHolder = NULL;
	
	while (!player->interrupt)
	{
		packetHolder = (PacketHolder *) blockingQueueGet(&player->audioPacketQueue, 1);
		if (player->audioIgnoreWorkFrame) player->audioIgnoreWorkFrame = 0;
		if (packetHolder == NULL || player->interrupt) break;
		condBroadcastLocked(&player->decodePacketsFlowCond, &player->decodePacketsFlowMutex);
		if (player->interrupt) break;
		
		pthread_mutex_lock(&player->playFinishMutex);
		while (!player->interrupt && !player->playing)
		{
			pthread_cond_wait(&player->playFinishCond, &player->playFinishMutex);
		}
		pthread_mutex_unlock(&player->playFinishMutex);
		if (player->interrupt) break;
		
		int send = 1;
		while (1)
		{
			int success = 0;
			uint8_t ** dstData = NULL;
			if (player->audioIgnoreWorkFrame) goto IGNORE_AUDIO_FRAME;
			AVPacket * packet = packetHolder->packet;
			if (packet == NULL) goto IGNORE_AUDIO_FRAME;
			pthread_mutex_lock(&player->decodeAudioFrameMutex);
			if (player->audioIgnoreWorkFrame) unlockAndGoTo(&player->decodeAudioFrameMutex, IGNORE_AUDIO_FRAME);
			if (send)
			{
				send = 0;
				avcodec_send_packet(codecContext, packet);
			}
			int ready = !avcodec_receive_frame(codecContext, frame);
			pthread_mutex_unlock(&player->decodeAudioFrameMutex);
			
			if (ready)
			{
				int64_t position;
				if (frame->pkt_pts == AV_NOPTS_VALUE) position = -1;
				else position = max64(frame->pkt_pts * 1000 * stream->time_base.num / stream->time_base.den, 0);
				if (player->seekAnyFrame && player->audioPositionNotSync && position < player->audioPosition)
				{
					success = 1;
					goto IGNORE_AUDIO_FRAME;
				}
				
				if (frame->channel_layout == 0) frame->channel_layout = av_get_default_channel_layout(frame->channels);
				uint64_t srcChannelLayout = frame->channel_layout;
				uint64_t dstChannelLayout = player->resampleChannels != 0 ? player->resampleChannels : srcChannelLayout;
				int srcSamples = frame->nb_samples;
				int srcSampleRate = frame->sample_rate;
				int dstSampleRate = player->resampleSampleRate != 0 ? player->resampleSampleRate : srcSampleRate;
				int dstFormat = AV_SAMPLE_FMT_S16;
				av_opt_set_int(resampleContext, "in_channel_layout", srcChannelLayout, 0);
				av_opt_set_int(resampleContext, "out_channel_layout", dstChannelLayout,  0);
				av_opt_set_int(resampleContext, "in_sample_rate", srcSampleRate, 0);
				av_opt_set_int(resampleContext, "out_sample_rate", dstSampleRate, 0);
				av_opt_set_sample_fmt(resampleContext, "in_sample_fmt", frame->format, 0);
				av_opt_set_sample_fmt(resampleContext, "out_sample_fmt", dstFormat,  0);
				if (swr_init(resampleContext) < 0 || player->audioIgnoreWorkFrame) goto IGNORE_AUDIO_FRAME;
				int dstSamples = av_rescale_rnd(srcSamples, dstSampleRate, srcSampleRate, AV_ROUND_UP);
				int dstChannels = av_get_channel_layout_nb_channels(dstChannelLayout);
				int result = av_samples_alloc_array_and_samples(&dstData, frame->linesize, dstChannels,
						dstSamples, dstFormat, 0);
				if (result < 0 || player->audioIgnoreWorkFrame) goto IGNORE_AUDIO_FRAME;
				dstSamples = av_rescale_rnd(swr_get_delay(resampleContext, srcSampleRate) + srcSamples,
						dstSampleRate, srcSampleRate, AV_ROUND_UP);
				result = swr_convert(resampleContext, dstData, dstSamples, (const uint8_t **) frame->data, srcSamples);
				if (result < 0 || player->audioIgnoreWorkFrame) goto IGNORE_AUDIO_FRAME;
				
				int size = av_samples_get_buffer_size(NULL, dstChannels, result, dstFormat, 1);
				if (size < 0) goto IGNORE_AUDIO_FRAME;
				pthread_mutex_lock(&player->audioSleepBufferMutex);
				if (player->audioIgnoreWorkFrame) unlockAndGoTo(&player->audioSleepBufferMutex, IGNORE_AUDIO_FRAME);
				while (!player->interrupt && blockingQueueCount(&player->audioBufferQueue) >= 5)
				{
					pthread_cond_wait(&player->audioBufferCond, &player->audioSleepBufferMutex);
				}
				if (player->audioIgnoreWorkFrame) unlockAndGoTo(&player->audioSleepBufferMutex, IGNORE_AUDIO_FRAME);
				if (position >= 0 && player->audioBufferNeedEnqueueAfterDecode)
				{
					player->audioPosition = position;
					player->audioPositionNotSync = 0;
				}
				while (!player->interrupt && player->videoPositionNotSync)
				{
					pthread_cond_wait(&player->audioSleepCond, &player->audioSleepBufferMutex);
				}
				if (player->audioIgnoreWorkFrame) unlockAndGoTo(&player->audioSleepBufferMutex, IGNORE_AUDIO_FRAME);
				int64_t videoPosition = player->videoPosition;
				int64_t gaining = player->videoFinished ? 0 : position - videoPosition;
				if (gaining > GAINING_THRESHOLD)
				{
					logp("sleep audio %" PRId64 " %" PRId64, gaining, position);
					int64_t time = calculateFrameTime(gaining);
					while (!player->interrupt && !player->audioIgnoreWorkFrame)
					{
						if (condSleepUntilMs(&player->audioSleepCond, &player->audioSleepBufferMutex, time)) break;
					}
				}
				if (player->audioIgnoreWorkFrame) unlockAndGoTo(&player->audioSleepBufferMutex, IGNORE_AUDIO_FRAME);
				AudioBuffer * audioBuffer = malloc(sizeof(AudioBuffer));
				audioBuffer->buffer = dstData[0];
				audioBuffer->index = 0;
				audioBuffer->size = size;
				audioBuffer->position = position;
				audioBuffer->divider = 2 * frame->channels * dstSampleRate;
				int needEnqueue = player->audioBufferNeedEnqueueAfterDecode;
				blockingQueueAdd(&player->audioBufferQueue, audioBuffer);
				if (needEnqueue) enqueueAudioBuffer(player);
				pthread_mutex_unlock(&player->audioSleepBufferMutex);
				success = 1;
			}
			
			IGNORE_AUDIO_FRAME:
			if (dstData != NULL)
			{
				if (!success) av_freep(&dstData[0]);
				av_freep(&dstData);
			}
			if (!success || !packetHolder->finish) break;
			if (packetHolder->finish && frame->pkt_pts >= packet->pts)
			{
				pthread_mutex_lock(&player->decodeAudioFrameMutex);
				if (!player->audioIgnoreWorkFrame) avcodec_flush_buffers(codecContext);
				pthread_mutex_unlock(&player->decodeAudioFrameMutex);
				break;
			}
		}
		packetQueueFreeCallback(packetHolder);
		packetHolder = NULL;
	}
	if (packetHolder != NULL) packetQueueFreeCallback(packetHolder);
	swr_free(&resampleContext);
	av_frame_free(&frame);
	return NULL;
}

static void drawWindow(Player * player, uint8_t * buffer, int width, int height, int lastWidth, int lastHeight,
		JNIEnv * env)
{
	if (player->videoWindow != NULL)
	{
		if (width != lastWidth || height != lastHeight)
		{
			ANativeWindow_setBuffersGeometry(player->videoWindow, width, height,
					ANativeWindow_getFormat(player->videoWindow));
			Bridge * bridge = obtainBridge(player, env);
			sendBridgeMessage(BRIDGE_MESSAGE_SIZE_CHANGED);
		}
		ANativeWindow_Buffer canvas;
		if (ANativeWindow_lock(player->videoWindow, &canvas, NULL) == 0)
		{
			if (canvas.width >= width && canvas.height >= height)
			{
				// Width and height can be smaller in the moment of surface changing and before it was handled
				void * to = canvas.bits;
				if (player->videoFormat == AV_PIX_FMT_YUV420P)
				{
					for (int i = 0; i < height; i++)
					{
						memcpy(to, buffer, width);
						to += canvas.stride;
						buffer += width;
					}
					memset(to, 127, canvas.stride * height / 2);
					for (int i = 0; i < height / 2; i++)
					{
						memcpy(to, buffer, width / 2);
						to += canvas.stride / 2;
						buffer += width / 2;
					}
					if (canvas.stride % 32 != 0) to += height / 2 * 8; // Align to 16
					for (int i = 0; i < height / 2; i++)
					{
						memcpy(to, buffer, width / 2);
						to += canvas.stride / 2;
						buffer += width / 2;
					}
				}
				else
				{
					int bytesPerPixel = getBytesPerPixel(player->videoFormat);
					if (bytesPerPixel > 0)
					{
						for (int i = 0; i < height; i++)
						{
							memcpy(to, buffer, bytesPerPixel * width);
							to += bytesPerPixel * canvas.stride;
							buffer += bytesPerPixel * width;
						}
					}
				}
			}
			ANativeWindow_unlockAndPost(player->videoWindow);
		}
	}
}

static void * performDraw(void * data)
{
	Player * player = (Player *) data;
	JNIEnv * env;
	(*loadJavaVM)->AttachCurrentThread(loadJavaVM, &env, NULL);
	AVStream * stream = getStream(player->videoStreamIndex);
	int lastWidth = player->videoCodecContext->width;
	int lastHeight = player->videoCodecContext->height;
	while (!player->interrupt)
	{
		BufferItem * bufferItem = NULL;
		pthread_mutex_lock(&player->videoQueueMutex);
		while (!player->interrupt && bufferItem == NULL)
		{
			if (player->videoBufferQueue != NULL) bufferItem = bufferQueueSeize(player->videoBufferQueue);
			if (bufferItem == NULL) pthread_cond_wait(&player->videoQueueCond, &player->videoQueueMutex);
		}
		player->drawIgnoreWorkFrame = 0;
		pthread_mutex_unlock(&player->videoQueueMutex);
		if (player->interrupt) goto IGNORE_DRAW_FRAME;
		
		pthread_mutex_lock(&player->playFinishMutex);
		while (!player->interrupt && !player->playing)
		{
			pthread_cond_wait(&player->playFinishCond, &player->playFinishMutex);
		}
		pthread_mutex_unlock(&player->playFinishMutex);
		if (player->interrupt) goto IGNORE_DRAW_FRAME;
		
		pthread_mutex_lock(&player->videoSleepDrawMutex);
		if (player->drawIgnoreWorkFrame) unlockAndGoTo(&player->videoSleepDrawMutex, IGNORE_DRAW_FRAME);
		VideoFrameExtra * extra = bufferItem->extra;
		int64_t position = calculatePosition(player, 1);
		int64_t waitTime = 0;
		if (extra->position >= 0)
		{
			player->videoPosition = extra->position;
			waitTime = extra->position - position;
			if (player->videoPositionNotSync)
			{
				player->videoPositionNotSync = 0;
				Bridge * bridge = obtainBridge(player, env);
				sendBridgeMessage(BRIDGE_MESSAGE_END_SEEKING);
				pthread_mutex_unlock(&player->videoSleepDrawMutex);
				condBroadcastLocked(&player->audioSleepCond, &player->audioSleepBufferMutex);
				pthread_mutex_lock(&player->videoSleepDrawMutex);
				if (player->drawIgnoreWorkFrame) unlockAndGoTo(&player->videoSleepDrawMutex, IGNORE_DRAW_FRAME);
			}
		}
		if (waitTime > 0)
		{
			logp("sleep video %" PRId64 " %" PRId64 " %" PRId64, waitTime, player->videoPosition, position);
			int64_t time = calculateFrameTime(waitTime);
			while (!player->interrupt && !player->drawIgnoreWorkFrame)
			{
				if (condSleepUntilMs(&player->videoSleepCond, &player->videoSleepDrawMutex, time)) break;
			}
			waitTime = 0;
		}
		if (player->drawIgnoreWorkFrame) unlockAndGoTo(&player->videoSleepDrawMutex, IGNORE_DRAW_FRAME);
		if (player->audioPositionNotSync) updateAudioPositionSurrogate(player, position, 0); else
		{
			int64_t gaining = -waitTime;
			if (player->audioStreamIndex == UNDEFINED && gaining > GAINING_THRESHOLD) player->startTime += gaining;
		}
		logp("draw video %" PRId64, player->videoPosition);
		int bufferSize = bufferItem->bufferSize;
		if (bufferSize > player->videoLastBufferSize)
		{
			player->videoLastBuffer = realloc(player->videoLastBuffer, bufferSize);
			player->videoLastBufferSize = bufferSize;
		}
		memcpy(player->videoLastBuffer, bufferItem->buffer, bufferSize);
		player->videoLastBufferWidth = extra->width;
		player->videoLastBufferHeight = extra->height;
		if ((player->lastDrawTimes[0] - player->lastDrawTimes[1]) * MAX_FPS >= 1000
				|| (getTime() - player->lastDrawTimes[0]) * MAX_FPS >= 1000)
		{
			// Avoid FPS > MAX_FPS
			drawWindow(player, bufferItem->buffer, extra->width, extra->height, lastWidth, lastHeight, env);
			lastWidth = extra->width;
			lastHeight = extra->height;
			player->lastDrawTimes[1] = player->lastDrawTimes[0];
			player->lastDrawTimes[0] = getTime();
		}
		pthread_mutex_unlock(&player->videoSleepDrawMutex);
		
		IGNORE_DRAW_FRAME:
		if (bufferItem != NULL)
		{
			free(bufferItem->extra);
			bufferItem->extra = NULL;
			pthread_mutex_lock(&player->videoQueueMutex);
			bufferQueueRelease(player->videoBufferQueue, bufferItem);
			pthread_cond_broadcast(&player->videoQueueCond);
			pthread_mutex_unlock(&player->videoQueueMutex);
			markStreamFinished(player, 1);
		}
	}
	(*loadJavaVM)->DetachCurrentThread(loadJavaVM);
	return NULL;
}

static int getVideoBufferSize(int videoFormat, int width, int height)
{
	switch (videoFormat)
	{
		case AV_PIX_FMT_RGBA: return width * height * 4;
		case AV_PIX_FMT_RGB565LE: return width * height * 2;
		case AV_PIX_FMT_YUV420P: return width * height * 3 / 2;
		default: return 0;
	}
}

static void extendScaleHolder(ScaleHolder * scaleHolder, int bufferSize, int width, int height,
		int bytesPerPixel, int isYUV)
{
	if (bufferSize > scaleHolder->bufferSize)
	{
		scaleHolder->bufferSize = bufferSize;
		if (scaleHolder->scaleBuffer != NULL) av_free(scaleHolder->scaleBuffer);
		scaleHolder->scaleBuffer = av_malloc(bufferSize);
	}
	scaleHolder->scaleData[0] = scaleHolder->scaleBuffer;
	scaleHolder->scaleData[1] = isYUV ? scaleHolder->scaleBuffer + width * height + width * height / 4 : NULL;
	scaleHolder->scaleData[2] = isYUV ? scaleHolder->scaleBuffer + width * height : NULL;
	scaleHolder->scaleData[3] = NULL;
	scaleHolder->scaleLinesize[0] = bytesPerPixel * width;
	scaleHolder->scaleLinesize[1] = isYUV ? width / 2 : 0;
	scaleHolder->scaleLinesize[2] = isYUV ? width / 2 : 0;
	scaleHolder->scaleLinesize[3] = 0;
}

static void * performDecodeVideo(void * data)
{
	Player * player = (Player *) data;
	AVStream * stream = getStream(player->videoStreamIndex);
	AVCodecContext * codecContext = player->videoCodecContext;
	pthread_mutex_lock(&player->videoSleepDrawMutex);
	while (!player->interrupt && player->videoBufferQueue == NULL)
	{
		pthread_cond_wait(&player->videoSleepCond, &player->videoSleepDrawMutex);
	}
	pthread_mutex_unlock(&player->videoSleepDrawMutex);
	if (player->interrupt) return NULL;
	
	int bytesPerPixel = getBytesPerPixel(player->videoFormat);
	int isYUV = player->videoFormat == AV_PIX_FMT_YUV420P;
	AVFrame * frame = av_frame_alloc();
	ScaleHolder scaleHolder;
	scaleHolder.bufferSize = 0;
	scaleHolder.scaleBuffer = NULL;
	int lastWidth = codecContext->width;
	int lastHeight = codecContext->height;
	extendScaleHolder(&scaleHolder, player->videoBufferQueue->bufferSize, lastWidth, lastHeight, bytesPerPixel, isYUV);
	SparceArray scaleContexts;
	sparceArrayInit(&scaleContexts, 1);
	PacketHolder * packetHolder = NULL;
	
	int totalMeasurements = 10;
	int currentMeasurement = 0;
	int measurements[2 * totalMeasurements];
	
	while (!player->interrupt)
	{
		packetHolder = (PacketHolder *) blockingQueueGet(&player->videoPacketQueue, 1);
		if (player->videoIgnoreWorkFrame) player->videoIgnoreWorkFrame = 0;
		if (packetHolder == NULL || player->interrupt) break;
		condBroadcastLocked(&player->decodePacketsFlowCond, &player->decodePacketsFlowMutex);
		if (player->interrupt) break;
		
		pthread_mutex_lock(&player->playFinishMutex);
		while (!player->interrupt && !player->playing)
		{
			pthread_cond_wait(&player->playFinishCond, &player->playFinishMutex);
		}
		pthread_mutex_unlock(&player->playFinishMutex);
		if (player->interrupt) break;
		
		int send = 1;
		while (1)
		{
			int success = 0;
			VideoFrameExtra * extra = NULL;
			if (player->videoIgnoreWorkFrame) goto IGNORE_VIDEO_FRAME;
			AVPacket * packet = packetHolder->packet;
			if (packet == NULL) goto IGNORE_VIDEO_FRAME;
			pthread_mutex_lock(&player->decodeVideoFrameMutex);
			if (player->videoIgnoreWorkFrame) unlockAndGoTo(&player->decodeVideoFrameMutex, IGNORE_VIDEO_FRAME);
			if (send)
			{
				send = 0;
				avcodec_send_packet(codecContext, packet);
			}
			int ready = !avcodec_receive_frame(codecContext, frame);
			pthread_mutex_unlock(&player->decodeVideoFrameMutex);
			
			if (ready)
			{
				extra = malloc(sizeof(VideoFrameExtra));
				extra->width = frame->width;
				extra->height = frame->height;
				if (frame->pkt_pts == AV_NOPTS_VALUE) extra->position = -1;
				else extra->position = max64(frame->pkt_pts * 1000 * stream->time_base.num / stream->time_base.den, 0);
				if (player->seekAnyFrame && player->videoPositionNotSync && extra->position < player->videoPosition)
				{
					success = 1;
					goto IGNORE_VIDEO_FRAME;
				}
				
				int extendedBufferSize = 0;
				if (lastWidth != frame->width || lastHeight != frame->height)
				{
					extendedBufferSize = getVideoBufferSize(player->videoFormat, frame->width, frame->height);
					extendScaleHolder(&scaleHolder, extendedBufferSize, frame->width, frame->height,
							bytesPerPixel, isYUV);
					lastWidth = frame->width;
					lastHeight = frame->height;
				}
				int useLibyuv = frame->format == AV_PIX_FMT_YUV420P && player->videoFormat == AV_PIX_FMT_RGBA;
				uint64_t startTime = 0;
				if (useLibyuv)
				{
					if (player->videoUseLibyuv >= 0) useLibyuv = player->videoUseLibyuv; else
					{
						if (currentMeasurement < totalMeasurements) useLibyuv = 0;
						if (currentMeasurement < 2 * totalMeasurements) startTime = getTimeUs();
					}
				}
				if (useLibyuv)
				{
					I420ToABGR(frame->data[0], frame->linesize[0], frame->data[1], frame->linesize[1],
							frame->data[2], frame->linesize[2], scaleHolder.scaleBuffer, 4 * frame->width,
							frame->width, frame->height);
				}
				else
				{
					int scaleContextIndex = (frame->width) << 16 | frame->height;
					struct SwsContext * scaleContext = sparceArrayGet(&scaleContexts, scaleContextIndex);
					if (scaleContext == NULL)
					{
						scaleContext = sws_getContext(frame->width, frame->height, frame->format,
								frame->width, frame->height, player->videoFormat, SWS_FAST_BILINEAR, NULL, NULL, NULL);
						sparceArrayAdd(&scaleContexts, scaleContextIndex, scaleContext);
					}
					sws_scale(scaleContext, (uint8_t const * const *) frame->data, frame->linesize,
							0, frame->height, scaleHolder.scaleData, scaleHolder.scaleLinesize);
				}
				if (startTime != 0)
				{
					if (currentMeasurement < 2 * totalMeasurements)
					{
						measurements[currentMeasurement++] = (int) (getTimeUs() - startTime);
						if (currentMeasurement == 2 * totalMeasurements)
						{
							int avg1 = 0;
							int avg2 = 0;
							for (int i = 0; i < totalMeasurements; i++) avg1 += measurements[i];
							for (int i = totalMeasurements; i < 2 * totalMeasurements; i++) avg2 += measurements[i];
							player->videoUseLibyuv = avg2 <= avg1 ? 1 : 0;
						}
					}
				}
				
				pthread_mutex_lock(&player->videoQueueMutex);
				if (player->videoIgnoreWorkFrame) unlockAndGoTo(&player->videoQueueMutex, IGNORE_VIDEO_FRAME);
				if (extendedBufferSize > 0) bufferQueueExtend(player->videoBufferQueue, extendedBufferSize);
				BufferItem * bufferItem = NULL;
				while (!player->interrupt && !player->videoIgnoreWorkFrame && bufferItem == NULL)
				{
					bufferItem = bufferQueuePrepare(player->videoBufferQueue);
					if (bufferItem == NULL) pthread_cond_wait(&player->videoQueueCond, &player->videoQueueMutex);
				}
				if (bufferItem != NULL)
				{
					memcpy(bufferItem->buffer, scaleHolder.scaleBuffer, player->videoBufferQueue->bufferSize);
					bufferItem->extra = extra;
					bufferQueueAdd(player->videoBufferQueue, bufferItem);
					pthread_cond_broadcast(&player->videoQueueCond);
					success = 1;
				}
				pthread_mutex_unlock(&player->videoQueueMutex);
			}
			
			IGNORE_VIDEO_FRAME:
			if (!success && extra != NULL) free(extra);
			if (!success || !packetHolder->finish) break;
			if (packetHolder->finish && frame->pkt_pts >= packet->pts)
			{
				pthread_mutex_lock(&player->decodeVideoFrameMutex);
				if (!player->videoIgnoreWorkFrame) avcodec_flush_buffers(codecContext);
				pthread_mutex_unlock(&player->decodeVideoFrameMutex);
				break;
			}
		}
		markStreamFinished(player, 1);
		packetQueueFreeCallback(packetHolder);
		packetHolder = NULL;
	}
	if (packetHolder != NULL) packetQueueFreeCallback(packetHolder);
	sparceArrayDestroyEach(&scaleContexts, sws_freeContext(data));
	av_free(scaleHolder.scaleBuffer);
	av_frame_free(&frame);
	return NULL;
}

static PacketHolder * createPacketHolder(int allocPacket, int finish)
{
	PacketHolder * packetHolder = malloc(sizeof(PacketHolder));
	packetHolder->packet = allocPacket ? malloc(sizeof(AVPacket)) : NULL;
	packetHolder->finish = finish;
	return packetHolder;
}

static void * performDecodePackets(void * data)
{
	Player * player = (Player *) data;
	JNIEnv * env;
	(*loadJavaVM)->AttachCurrentThread(loadJavaVM, &env, NULL);
	Bridge * bridge = obtainBridge(player, env);
	AVFormatContext * formatContext = player->formatContext;
	AVPacket packet;
	while (!player->interrupt)
	{
		while (!player->interrupt)
		{
			player->ignoreReadFrame = 0;
			pthread_mutex_lock(&player->decodePacketsReadMutex);
			int success = av_read_frame(formatContext, &packet) >= 0;
			pthread_mutex_unlock(&player->decodePacketsReadMutex);
			if (!success) break;
			pthread_mutex_lock(&player->decodePacketsFlowMutex);
			if (player->ignoreReadFrame) goto SKIP_FRAME;
			while (!player->interrupt
					&& (player->videoStreamIndex == UNDEFINED || blockingQueueCount(&player->videoPacketQueue) >= 10)
					&& (player->audioStreamIndex == UNDEFINED || blockingQueueCount(&player->audioPacketQueue) >= 20))
			{
				pthread_cond_wait(&player->decodePacketsFlowCond, &player->decodePacketsFlowMutex);
			}
			if (player->ignoreReadFrame) goto SKIP_FRAME;
			int isAudio = packet.stream_index == player->audioStreamIndex;
			int isVideo = packet.stream_index == player->videoStreamIndex;
			if (isAudio || isVideo)
			{
				PacketHolder * packetHolder = createPacketHolder(1, 0);
				av_copy_packet(packetHolder->packet, &packet);
				if (isAudio)
				{
					blockingQueueAdd(&player->audioPacketQueue, packetHolder);
					player->audioFinished = 0;
					logp("enqueue audio %" PRId64, packet.pts);
				}
				else if (isVideo)
				{
					blockingQueueAdd(&player->videoPacketQueue, packetHolder);
					player->videoFinished = 0;
					logp("enqueue video %" PRId64, packet.pts);
				}
			}
			SKIP_FRAME:
			av_packet_unref(&packet);
			pthread_mutex_unlock(&player->decodePacketsFlowMutex);
		}
		pthread_mutex_lock(&player->decodePacketsFlowMutex);
		if (!player->ignoreReadFrame)
		{
			if (player->audioStreamIndex != UNDEFINED)
			{
				blockingQueueAdd(&player->audioPacketQueue, createPacketHolder(0, 1));
				player->audioFinished = 0;
			}
			if (player->videoStreamIndex != UNDEFINED)
			{
				blockingQueueAdd(&player->videoPacketQueue, createPacketHolder(0, 1));
				player->videoFinished = 0;
			}
		}
		pthread_mutex_unlock(&player->decodePacketsFlowMutex);
		pthread_mutex_lock(&player->playFinishMutex);
		player->decodeFinished = 1;
		int needSendFinishMessage = 1;
		while (!player->interrupt && player->decodeFinished)
		{
			if (needSendFinishMessage && (player->audioFinished || player->audioStreamIndex == UNDEFINED)
					&& (player->videoFinished || player->videoStreamIndex == UNDEFINED))
			{
				needSendFinishMessage = 0;
				sendBridgeMessage(BRIDGE_MESSAGE_PLAYBACK_COMPLETE);
			}
			pthread_cond_wait(&player->playFinishCond, &player->playFinishMutex);
		}
		pthread_mutex_unlock(&player->playFinishMutex);
	}
	blockingQueueAdd(&player->audioPacketQueue, NULL);
	blockingQueueAdd(&player->videoPacketQueue, NULL);
	pthread_join(player->decodeAudioThread, NULL);
	pthread_join(player->decodeVideoThread, NULL);
	pthread_join(player->drawThread, NULL);
	(*loadJavaVM)->DetachCurrentThread(loadJavaVM);
	return NULL;
}

static void updatePlayerSurface(JNIEnv * env, Player * player, jobject surface, int lockAndCheck)
{
	if (lockAndCheck) pthread_mutex_lock(&player->videoSleepDrawMutex);
	if (player->videoWindow != NULL)
	{
		ANativeWindow_release(player->videoWindow);
		player->videoWindow = NULL;
	}
	if (surface != NULL)
	{
		player->videoWindow = ANativeWindow_fromSurface(env, surface);
		int format = ANativeWindow_getFormat(player->videoWindow);
		AVStream * stream = getStream(player->videoStreamIndex);
		AVCodecContext * codecContext = player->videoCodecContext;
		int width = codecContext->width;
		int height = codecContext->height;
		if (player->videoBufferQueue == NULL)
		{
			int videoFormat = -1;
			switch (format)
			{
				case WINDOW_FORMAT_RGBA_8888:
				case WINDOW_FORMAT_RGBX_8888:
				{
					videoFormat = AV_PIX_FMT_RGBA;
					break;
				}
				case WINDOW_FORMAT_RGB_565:
				{
					videoFormat = AV_PIX_FMT_RGB565LE;
					break;
				}
				case WINDOW_FORMAT_YV12:
				{
					videoFormat = AV_PIX_FMT_YUV420P;
					break;
				}
			}
			if (videoFormat >= 0)
			{
				int videoBufferSize = getVideoBufferSize(videoFormat, width, height);
				player->videoFormat = videoFormat;
				player->videoBufferQueue = malloc(sizeof(BufferQueue));
				bufferQueueInit(player->videoBufferQueue, videoBufferSize, 3);
				player->videoLastBuffer = malloc(videoBufferSize);
				player->videoLastBufferSize = videoBufferSize;
				if (videoFormat == AV_PIX_FMT_RGBA)
				{
					// RGBA_8888 "black" buffer
					int count = 4 * width * height;
					memset(player->videoLastBuffer, 0x00, count);
					for (int i = 3; i < count; i += 4) player->videoLastBuffer[i] = 0xff;
				}
				else if (videoFormat == AV_PIX_FMT_RGB565LE)
				{
					// RGB_565 "black" buffer
					memset(player->videoLastBuffer, 0x00, 2 * width * height);
				}
				else if (videoFormat == AV_PIX_FMT_YUV420P)
				{
					// YV12 "black" buffer
					memset(player->videoLastBuffer, 0, width * height);
					memset(player->videoLastBuffer + width * height, 0x7f, width * height / 2);
				}
				pthread_cond_broadcast(&player->videoSleepCond);
			}
		}
		if (player->videoLastBufferWidth >= 0) width = player->videoLastBufferWidth;
		if (player->videoLastBufferHeight >= 0) height = player->videoLastBufferHeight;
		ANativeWindow_setBuffersGeometry(player->videoWindow, width, height, format);
		if (player->videoLastBuffer != NULL)
		{
			drawWindow(player, player->videoLastBuffer, width, height, width, height, env);
		}
	}
	if (lockAndCheck) pthread_mutex_unlock(&player->videoSleepDrawMutex);
}

static int bufferReadData(void * opaque, uint8_t * buf, int buf_size)
{
	Player * player = opaque;
	if (player->interrupt) return -1;
	Bridge * bridge = sparceArrayGet(&player->bridges, (int) pthread_self());
	if (bridge == NULL) return -1;
	int count = (*bridge->env)->CallIntMethod(bridge->env, player->nativeBridge, bridge->methodOnRead, buf_size);
	if (count > 0)
	{
		jbyteArray buffer = (*bridge->env)->CallObjectMethod(bridge->env, player->nativeBridge,
				bridge->methodGetBuffer);
		(*bridge->env)->GetByteArrayRegion(bridge->env, buffer, 0, count, buf);
		(*bridge->env)->DeleteLocalRef(bridge->env, buffer);
	}
	return count;
}

static int64_t bufferSeekData(void * opaque, int64_t offset, int whence)
{
	Player * player = opaque;
	if (player->interrupt) return -1;
	Bridge * bridge = sparceArrayGet(&player->bridges, (int) pthread_self());
	if (bridge == NULL) return -1;
	jlong value = offset;
	return (*bridge->env)->CallLongMethod(bridge->env, player->nativeBridge, bridge->methodOnSeek, value, whence);
}

static Player * createPlayer()
{
	Player * player = malloc(sizeof(Player));
	memset(player, 0, sizeof(Player));
	player->audioStreamIndex = UNDEFINED;
	player->videoStreamIndex = UNDEFINED;
	player->videoUseLibyuv = -1;
	player->videoLastBufferWidth = -1;
	player->videoLastBufferHeight = -1;
	sparceArrayInit(&player->bridges, 4);
	pthread_mutex_init(&player->decodePacketsReadMutex, NULL);
	pthread_cond_init(&player->decodePacketsFlowCond, NULL);
	pthread_mutex_init(&player->decodePacketsFlowMutex, NULL);
	pthread_mutex_init(&player->decodeAudioFrameMutex, NULL);
	pthread_mutex_init(&player->decodeVideoFrameMutex, NULL);
	pthread_cond_init(&player->playFinishCond, NULL);
	pthread_mutex_init(&player->playFinishMutex, NULL);
	pthread_cond_init(&player->audioSleepCond, NULL);
	pthread_cond_init(&player->audioBufferCond, NULL);
	pthread_mutex_init(&player->audioSleepBufferMutex, NULL);
	pthread_cond_init(&player->videoSleepCond, NULL);
	pthread_mutex_init(&player->videoSleepDrawMutex, NULL);
	pthread_cond_init(&player->videoQueueCond, NULL);
	pthread_mutex_init(&player->videoQueueMutex, NULL);
	blockingQueueInit(&player->audioPacketQueue);
	blockingQueueInit(&player->videoPacketQueue);
	blockingQueueInit(&player->audioBufferQueue);
	return player;
}

#define NEED_RESAMPLE_NO 0
#define NEED_RESAMPLE_MAY_48000 1
#define NEED_RESAMPLE_FORCE_44100 2

jlong init(JNIEnv * env, jobject nativeBridge, jboolean seekAnyFrame)
{
	Player * player = createPlayer();
	player->seekAnyFrame = !!seekAnyFrame;
	player->nativeBridge = (*env)->NewGlobalRef(env, nativeBridge);
	Bridge * bridge = obtainBridge(player, env);
	jbyteArray buffer = (*env)->CallObjectMethod(env, player->nativeBridge, bridge->methodGetBuffer);
	int contextBufferSize = (*env)->GetArrayLength(env, buffer);
	uint8_t * contextBuffer = av_malloc(contextBufferSize);
	AVIOContext * ioContext = avio_alloc_context(contextBuffer, contextBufferSize, 0, player,
			&bufferReadData, NULL, &bufferSeekData);
	if (ioContext == NULL)
	{
		av_free(contextBuffer);
		player->errorCode = ERROR_LOAD_FORMAT;
		return jlongCast(player);
	}
	player->ioContext = ioContext;
	AVFormatContext * formatContext = avformat_alloc_context();
	formatContext->pb = ioContext;
	int result = avformat_open_input(&formatContext, "", NULL, NULL);
	if (result != 0)
	{
		player->errorCode = ERROR_LOAD_FORMAT;
		return jlongCast(player);
	}
	player->formatContext = formatContext;
	if (avformat_find_stream_info(formatContext, NULL) < 0)
	{
		player->errorCode = ERROR_FIND_STREAM_INFO;
		return jlongCast(player);
	}
	int audioStreamIndex = UNDEFINED;
	int videoStreamIndex = UNDEFINED;
	for (int i = 0; i < formatContext->nb_streams; i++)
	{
		int codecType = formatContext->streams[i]->codecpar->codec_type;
		if (audioStreamIndex == UNDEFINED && codecType == AVMEDIA_TYPE_AUDIO) audioStreamIndex = i;
		else if (videoStreamIndex == UNDEFINED && codecType == AVMEDIA_TYPE_VIDEO) videoStreamIndex = i;
	}
	if (videoStreamIndex == UNDEFINED)
	{
		player->errorCode = ERROR_FIND_STREAM;
		return jlongCast(player);
	}
	player->audioStreamIndex = audioStreamIndex;
	player->videoStreamIndex = videoStreamIndex;
	AVStream * audioStream = audioStreamIndex != UNDEFINED ? formatContext->streams[audioStreamIndex] : NULL;
	AVStream * videoStream = videoStreamIndex != UNDEFINED ? formatContext->streams[videoStreamIndex] : NULL;
	AVCodecContext * audioCodecContext = NULL;
	AVCodecContext * videoCodecContext = NULL;
	if (audioStream != NULL)
	{
		audioCodecContext = avcodec_alloc_context3(NULL);
		avcodec_parameters_to_context(audioCodecContext, audioStream->codecpar);
	}
	if (videoStream != NULL)
	{
		videoCodecContext = avcodec_alloc_context3(NULL);
		avcodec_parameters_to_context(videoCodecContext, videoStream->codecpar);
	}
	player->audioCodecContext = audioCodecContext;
	player->videoCodecContext = videoCodecContext;
	AVCodec * audioCodec = audioStream != NULL ? avcodec_find_decoder(audioCodecContext->codec_id) : NULL;
	AVCodec * videoCodec = videoStream != NULL ? avcodec_find_decoder(videoCodecContext->codec_id) : NULL;
	if (videoCodec == NULL)
	{
		player->errorCode = ERROR_FIND_CODEC;
		return jlongCast(player);
	}
	if (audioCodec != NULL && avcodec_open2(audioCodecContext, audioCodec, NULL) < 0)
	{
		player->errorCode = ERROR_OPEN_CODEC;
		return jlongCast(player);
	}
	if (videoCodec != NULL && avcodec_open2(videoCodecContext, videoCodec, NULL) < 0)
	{
		player->errorCode = ERROR_OPEN_CODEC;
		return jlongCast(player);
	}
	if (audioStream != NULL)
	{
		SLresult result;
		int success = 0;
		int channels = audioCodecContext->channels;
		if (channels != 1 && channels != 2)
		{
			channels = 2;
			player->resampleChannels = AV_CH_FRONT_LEFT | AV_CH_FRONT_RIGHT;
		}
		int channelMask = channels == 2 ? SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT : SL_SPEAKER_FRONT_CENTER;
		const SLInterfaceID volumeIds[] = {SL_IID_VOLUME};
		const SLboolean volumeRequired[] = {SL_BOOLEAN_FALSE};
		result = (*slEngine)->CreateOutputMix(slEngine, &player->slOutputMix, 1, volumeIds, volumeRequired);
		if (result != SL_RESULT_SUCCESS) goto HANDLE_SL_INIT_ERROR;
		result = (*player->slOutputMix)->Realize(player->slOutputMix, SL_BOOLEAN_FALSE);
		if (result != SL_RESULT_SUCCESS) goto HANDLE_SL_INIT_ERROR;
		SLDataLocator_AndroidSimpleBufferQueue locatorQueue = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
		SLDataFormat_PCM formatPCM = {SL_DATAFORMAT_PCM, channels, 0, SL_PCMSAMPLEFORMAT_FIXED_16,
				SL_PCMSAMPLEFORMAT_FIXED_16, channelMask, SL_BYTEORDER_LITTLEENDIAN};
		SLDataSource dataSource = {&locatorQueue, &formatPCM};
		SLDataLocator_OutputMix locatorOutputMix = {SL_DATALOCATOR_OUTPUTMIX, player->slOutputMix};
		SLDataSink dataSink = {&locatorOutputMix, NULL};
		const SLInterfaceID queueIds[] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
		const SLboolean queueRequired[] = {SL_BOOLEAN_TRUE};
		int needResampleSR = NEED_RESAMPLE_NO;
		int slSampleRate = 0;
		int sampleRate = audioCodecContext->sample_rate;
		switch (sampleRate)
		{
			case 8000: slSampleRate = SL_SAMPLINGRATE_8; break;
			case 11025: slSampleRate = SL_SAMPLINGRATE_11_025; break;
			case 12000: slSampleRate = SL_SAMPLINGRATE_12; break;
			case 16000: slSampleRate = SL_SAMPLINGRATE_16; break;
			case 22050: slSampleRate = SL_SAMPLINGRATE_22_05; break;
			case 24000: slSampleRate = SL_SAMPLINGRATE_24; break;
			case 32000: slSampleRate = SL_SAMPLINGRATE_32; break;
			case 44100: slSampleRate = SL_SAMPLINGRATE_44_1; break;
			case 48000: slSampleRate = SL_SAMPLINGRATE_48; break;
			case 64000: slSampleRate = SL_SAMPLINGRATE_64; break;
			case 88200: slSampleRate = SL_SAMPLINGRATE_88_2; break;
			case 96000: slSampleRate = SL_SAMPLINGRATE_96; break;
			case 192000: slSampleRate = SL_SAMPLINGRATE_192; break;
			default: needResampleSR = NEED_RESAMPLE_MAY_48000;
		}
		while (1)
		{
			int mayRepeat = 1;
			if (needResampleSR == NEED_RESAMPLE_MAY_48000 && sampleRate % 48000 == 0)
			{
				slSampleRate = SL_SAMPLINGRATE_48;
				player->resampleSampleRate = 48000;
			}
			else if (needResampleSR == NEED_RESAMPLE_MAY_48000 || needResampleSR == NEED_RESAMPLE_FORCE_44100)
			{
				slSampleRate = SL_SAMPLINGRATE_44_1;
				player->resampleSampleRate = 44100;
				mayRepeat = 0;
			}
			formatPCM.samplesPerSec = slSampleRate;
			result = (*slEngine)->CreateAudioPlayer(slEngine, &player->slPlayer, &dataSource, &dataSink, 1, queueIds,
					queueRequired);
			if (result == SL_RESULT_CONTENT_UNSUPPORTED && mayRepeat)
			{
				if (needResampleSR == NEED_RESAMPLE_NO) needResampleSR = NEED_RESAMPLE_MAY_48000;
				else if (needResampleSR == NEED_RESAMPLE_MAY_48000) needResampleSR = NEED_RESAMPLE_FORCE_44100;
			}
			else break;
		}
		if (result != SL_RESULT_SUCCESS) goto HANDLE_SL_INIT_ERROR;
		result = (*player->slPlayer)->Realize(player->slPlayer, SL_BOOLEAN_FALSE);
		if (result != SL_RESULT_SUCCESS) goto HANDLE_SL_INIT_ERROR;
		result = (*player->slPlayer)->GetInterface(player->slPlayer, SL_IID_BUFFERQUEUE, &player->slQueue);
		if (result != SL_RESULT_SUCCESS) goto HANDLE_SL_INIT_ERROR;
		result = (*player->slPlayer)->GetInterface(player->slPlayer, SL_IID_PLAY, &player->slPlay);
		if (result != SL_RESULT_SUCCESS) goto HANDLE_SL_INIT_ERROR;
		result = (*player->slQueue)->RegisterCallback(player->slQueue, audioPlayerCallback, player);
		if (result != SL_RESULT_SUCCESS) goto HANDLE_SL_INIT_ERROR;
		result = (*player->slPlay)->SetPlayState(player->slPlay, SL_PLAYSTATE_PLAYING);
		if (result != SL_RESULT_SUCCESS) goto HANDLE_SL_INIT_ERROR;
		success = 1;
		HANDLE_SL_INIT_ERROR:
		if (!success)
		{
			avcodec_close(audioCodecContext);
			audioStreamIndex = UNDEFINED;
			player->audioStreamIndex = UNDEFINED;
			player->audioCodecContext = NULL;
			audioStream = NULL;
			audioCodecContext = NULL;
			audioCodec = NULL;
		}
	}
	if (videoStream != NULL && pthread_create(&player->drawThread, NULL, &performDraw, player) != 0)
	{
		player->errorCode = ERROR_START_THREAD;
		return jlongCast(player);
	}
	if (audioStream != NULL && pthread_create(&player->decodeAudioThread, NULL, &performDecodeAudio, player) != 0)
	{
		player->errorCode = ERROR_START_THREAD;
		return jlongCast(player);
	}
	if (videoStream != NULL && pthread_create(&player->decodeVideoThread, NULL, &performDecodeVideo, player) != 0)
	{
		player->errorCode = ERROR_START_THREAD;
		return jlongCast(player);
	}
	if (pthread_create(&player->decodePacketsThread, NULL, &performDecodePackets, player) != 0)
	{
		player->errorCode = ERROR_START_THREAD;
		return jlongCast(player);
	}
	return jlongCast(player);
}

void destroy(JNIEnv * env, jlong pointer)
{
	Player * player = pointerCast(pointer);
	player->interrupt = 1;
	blockingQueueInterrupt(&player->audioPacketQueue);
	blockingQueueInterrupt(&player->videoPacketQueue);
	blockingQueueInterrupt(&player->audioBufferQueue);
	
	condBroadcastLocked(&player->audioSleepCond, &player->audioSleepBufferMutex);
	condBroadcastLocked(&player->audioBufferCond, &player->audioSleepBufferMutex);
	condBroadcastLocked(&player->videoSleepCond, &player->videoSleepDrawMutex);
	condBroadcastLocked(&player->videoQueueCond, &player->videoQueueMutex);
	condBroadcastLocked(&player->playFinishCond, &player->playFinishMutex);
	condBroadcastLocked(&player->decodePacketsFlowCond, &player->decodePacketsFlowMutex);
	
	pthread_join(player->decodePacketsThread, NULL);
	pthread_mutex_destroy(&player->decodePacketsReadMutex);
	pthread_mutex_destroy(&player->decodePacketsFlowMutex);
	pthread_mutex_destroy(&player->decodeAudioFrameMutex);
	pthread_mutex_destroy(&player->decodeVideoFrameMutex);
	pthread_mutex_destroy(&player->playFinishMutex);
	pthread_mutex_destroy(&player->audioSleepBufferMutex);
	pthread_mutex_destroy(&player->videoSleepDrawMutex);
	pthread_mutex_destroy(&player->videoQueueMutex);
	pthread_cond_destroy(&player->decodePacketsFlowCond);
	pthread_cond_destroy(&player->playFinishCond);
	pthread_cond_destroy(&player->audioSleepCond);
	pthread_cond_destroy(&player->audioBufferCond);
	pthread_cond_destroy(&player->videoSleepCond);
	pthread_cond_destroy(&player->videoQueueCond);
	
	blockingQueueDestroy(&player->audioPacketQueue, packetQueueFreeCallback);
	blockingQueueDestroy(&player->videoPacketQueue, packetQueueFreeCallback);
	blockingQueueDestroy(&player->audioBufferQueue, audioBufferQueueFreeCallback);
	if (player->videoBufferQueue != NULL)
	{
		bufferQueueDestroy(player->videoBufferQueue, videoBufferQueueFreeCallback);
		free(player->videoBufferQueue);
		free(player->videoLastBuffer);
	}
	
	if (player->slPlayer != NULL) (*player->slPlayer)->Destroy(player->slPlayer);
	if (player->slOutputMix != NULL) (*player->slOutputMix)->Destroy(player->slOutputMix);
	if (player->audioCodecContext) avcodec_close(player->audioCodecContext);
	if (player->videoCodecContext) avcodec_close(player->videoCodecContext);
	if (player->formatContext != NULL) avformat_close_input(&player->formatContext);
	if (player->ioContext != NULL)
	{
		av_free(player->ioContext->buffer);
		av_free(player->ioContext);
	}
	if (player->audioBuffer != NULL) free(player->audioBuffer);
	updatePlayerSurface(env, player, NULL, 0);
	sparceArrayDestroyEach(&player->bridges, free(data));
	(*env)->DeleteGlobalRef(env, player->nativeBridge);
	free(player);
}

jint getErrorCode(JNIEnv * env, jlong pointer)
{
	Player * player = pointerCast(pointer);
	return player->errorCode;
}

void getSummary(JNIEnv * env, jlong pointer, jintArray output)
{
	Player * player = pointerCast(pointer);
	jint result[3];
	result[0] = player->videoCodecContext->width;
	result[1] = player->videoCodecContext->height;
	result[2] = player->audioStreamIndex != UNDEFINED;
	(*env)->SetIntArrayRegion(env, output, 0, 3, result);
}

jlong getDuration(JNIEnv * env, jlong pointer)
{
	Player * player = pointerCast(pointer);
	return max64(player->formatContext->duration / 1000, 0);
}

jlong getPosition(JNIEnv * env, jlong pointer)
{
	Player * player = pointerCast(pointer);
	return max64(calculatePosition(player, 0), 0);
}

void setPosition(JNIEnv * env, jlong pointer, jlong position)
{
	Player * player = pointerCast(pointer);
	if (position >= 0)
	{
		// Leave the call below even without variable declaration to init a bridge here
		Bridge * bridge = obtainBridge(player, env);
		pthread_mutex_lock(&player->playFinishMutex);
		pthread_mutex_lock(&player->decodePacketsReadMutex);
		pthread_mutex_lock(&player->decodePacketsFlowMutex);
		pthread_mutex_lock(&player->decodeAudioFrameMutex);
		pthread_mutex_lock(&player->decodeVideoFrameMutex);
		pthread_mutex_lock(&player->audioSleepBufferMutex);
		pthread_mutex_lock(&player->videoSleepDrawMutex);
		pthread_mutex_lock(&player->videoQueueMutex);
		blockingQueueClear(&player->audioPacketQueue, packetQueueFreeCallback);
		blockingQueueClear(&player->videoPacketQueue, packetQueueFreeCallback);
		blockingQueueClear(&player->audioBufferQueue, audioBufferQueueFreeCallback);
		audioBufferQueueFreeCallback(player->audioBuffer);
		if (player->slQueue != NULL)
		{
			(*player->slQueue)->Clear(player->slQueue);
			player->audioBufferNeedEnqueueAfterDecode = 1;
		}
		player->audioBuffer = NULL;
		if (player->videoBufferQueue != NULL) bufferQueueClear(player->videoBufferQueue, videoBufferQueueFreeCallback);
		if (player->audioCodecContext != NULL) avcodec_flush_buffers(player->audioCodecContext);
		if (player->videoCodecContext != NULL) avcodec_flush_buffers(player->videoCodecContext);
		if (player->seekAnyFrame)
		{
			int64_t audioPosition = player->audioStreamIndex != UNDEFINED ? -1 : position;
			int64_t videoPosition = player->videoStreamIndex != UNDEFINED ? -1 : position;
			AVPacket packet;
			for (int i = 1; audioPosition == -1 || videoPosition == -1; i++)
			{
				int64_t seekPosition = max64(position - i * i * 1000, 0);
				int64_t maxPosition = max64(position - (i - 1) * (i - 1) * 1000, 0);
				av_seek_frame(player->formatContext, -1, seekPosition * 1000, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);
				while (1)
				{
					if (av_read_frame(player->formatContext, &packet) < 0) break;
					if (packet.pts != AV_NOPTS_VALUE)
					{
						int64_t * outPosition = NULL;
						if (packet.stream_index == player->audioStreamIndex) outPosition = &audioPosition;
						else if (packet.stream_index == player->videoStreamIndex) outPosition = &videoPosition;
						if (outPosition != NULL)
						{
							AVRational timeBase = getStream(packet.stream_index)->time_base;
							int64_t timestamp = packet.pts * 1000 * timeBase.num / timeBase.den;
							if (timestamp > maxPosition)
							{
								av_packet_unref(&packet);
								break;
							}
							if (timestamp > *outPosition) *outPosition = timestamp;
						}
					}
					av_packet_unref(&packet);
				}
				if (seekPosition <= 0) break;
			}
			if (audioPosition == -1) audioPosition = position;
			if (videoPosition == -1) videoPosition = position;
			position = min64(audioPosition, videoPosition);
		}
		logp("seek %lld", position);
		av_seek_frame(player->formatContext, -1, position * 1000, AVSEEK_FLAG_BACKWARD);
		player->decodeFinished = 0;
		player->audioFinished = 0;
		player->videoFinished = 0;
		updateAudioPositionSurrogate(player, position, 1);
		player->audioPosition = position;
		player->videoPosition = position;
		player->pausedPosition = position;
		player->audioPositionNotSync = 1;
		player->videoPositionNotSync = 1;
		player->ignoreReadFrame = 1;
		player->audioIgnoreWorkFrame = 1;
		player->videoIgnoreWorkFrame = 1;
		player->drawIgnoreWorkFrame = 1;
		player->lastDrawTimes[0] = 0;
		player->lastDrawTimes[1] = 0;
		if (player->videoStreamIndex != UNDEFINED) sendBridgeMessage(BRIDGE_MESSAGE_START_SEEKING);
		pthread_cond_broadcast(&player->playFinishCond);
		pthread_cond_broadcast(&player->decodePacketsFlowCond);
		pthread_cond_broadcast(&player->audioSleepCond);
		pthread_cond_broadcast(&player->audioBufferCond);
		pthread_cond_broadcast(&player->videoSleepCond);
		pthread_cond_broadcast(&player->videoQueueCond);
		pthread_mutex_unlock(&player->videoQueueMutex);
		pthread_mutex_unlock(&player->videoSleepDrawMutex);
		pthread_mutex_unlock(&player->audioSleepBufferMutex);
		pthread_mutex_unlock(&player->decodeVideoFrameMutex);
		pthread_mutex_unlock(&player->decodeAudioFrameMutex);
		pthread_mutex_unlock(&player->decodePacketsFlowMutex);
		pthread_mutex_unlock(&player->decodePacketsReadMutex);
		pthread_mutex_unlock(&player->playFinishMutex);
	}
}

void setPlaying(JNIEnv * env, jlong pointer, jboolean playing)
{
	Player * player = pointerCast(pointer);
	playing = !!playing;
	if (player->playing != playing)
	{
		logp("switch playing %d", playing);
		pthread_mutex_lock(&player->playFinishMutex);
		if (playing) updateAudioPositionSurrogate(player, player->pausedPosition, 1);
		else player->pausedPosition = calculatePosition(player, 1);
		player->playing = playing;
		pthread_cond_broadcast(&player->playFinishCond);
		pthread_mutex_unlock(&player->playFinishMutex);
		if (player->audioStreamIndex != UNDEFINED)
		{
			pthread_mutex_lock(&player->audioSleepBufferMutex);
			(*player->slPlay)->SetPlayState(player->slPlay, playing ? SL_PLAYSTATE_PLAYING : SL_PLAYSTATE_PAUSED);
			if (playing && player->audioBufferNeedEnqueueAfterDecode
					&& blockingQueueCount(&player->audioBufferQueue) > 0)
			{
				// Queue count checked to free from obligation to handle audio finish flag
				enqueueAudioBuffer(player);
			}
			pthread_mutex_unlock(&player->audioSleepBufferMutex);
		}
	}
}

void setSurface(JNIEnv * env, jlong pointer, jobject surface)
{
	Player * player = pointerCast(pointer);
	updatePlayerSurface(env, player, surface, 1);
}

jintArray getCurrentFrame(JNIEnv * env, jlong pointer)
{
	Player * player = pointerCast(pointer);
	pthread_mutex_lock(&player->videoSleepDrawMutex);
	uint8_t * buffer = player->videoLastBuffer;
	int width = player->videoLastBufferWidth;
	int height = player->videoLastBufferHeight;
	jintArray result = 0;
	if (buffer != 0 && width > 0 && height > 0)
	{
		int size = 4 * width * height;
		if (player->videoFormat != AV_PIX_FMT_RGB565LE && player->videoFormat != AV_PIX_FMT_YUV420P
				&& player->videoFormat != AV_PIX_FMT_RGBA)
		{
			goto CANCEL;
		}
		struct SwsContext * scaleContext = sws_getContext(width, height, player->videoFormat, width, height,
				AV_PIX_FMT_BGRA, SWS_FAST_BILINEAR, NULL, NULL, NULL);
		uint8_t * newBuffer = malloc(size);
		uint8_t * newData[4] = {newBuffer, 0, 0, 0};
		int newLinesize[4] = {4 * width, 0, 0, 0};
		if (player->videoFormat == AV_PIX_FMT_RGBA)
		{
			if (player->videoLastBufferSize < 4 * width * height)
			{
				sws_freeContext(scaleContext);
				goto CANCEL;
			}
			const uint8_t * const oldData[4] = {buffer, 0, 0, 0};
			int oldLinesize[4] = {4 * width, 0, 0, 0};
			sws_scale(scaleContext, oldData, oldLinesize, 0, height, newData, newLinesize);
		}
		else if (player->videoFormat == AV_PIX_FMT_RGB565LE)
		{
			if (player->videoLastBufferSize < 2 * width * height)
			{
				sws_freeContext(scaleContext);
				goto CANCEL;
			}
			const uint8_t * const oldData[4] = {buffer, 0, 0, 0};
			int oldLinesize[4] = {2 * width, 0, 0, 0};
			sws_scale(scaleContext, oldData, oldLinesize, 0, height, newData, newLinesize);
		}
		else if (player->videoFormat == AV_PIX_FMT_YUV420P)
		{
			if (player->videoLastBufferSize < width * height * 3 / 2)
			{
				sws_freeContext(scaleContext);
				goto CANCEL;
			}
			const uint8_t * const oldData[4] = {buffer, buffer + width * height + width * height / 4,
					buffer + width * height, 0};
			int oldLinesize[4] = {width, width / 2, width / 2, 0};
			sws_scale(scaleContext, oldData, oldLinesize, 0, height, newData, newLinesize);
		}
		sws_freeContext(scaleContext);
		result = (*env)->NewIntArray(env, size / 4);
		(*env)->SetIntArrayRegion(env, result, 0, size / 4, (int *) newBuffer);
		free(newBuffer);
	}
	CANCEL: pthread_mutex_unlock(&player->videoSleepDrawMutex);
	return result;
}

jobjectArray getTechnicalInfo(JNIEnv * env, jlong pointer)
{
	char buffer[24];
	Player * player = pointerCast(pointer);
	int entries = av_dict_count(player->formatContext->metadata);
	// Format, width, height, frame rate, pixel format, canvas format, libyuv
	if (player->videoCodecContext != NULL) entries += 7;
	// Format, channels, sample rate
	if (player->audioCodecContext != NULL) entries += 3;
	jobjectArray result = (*env)->NewObjectArray(env, 2 * entries, (*env)->FindClass(env, "java/lang/String"), NULL);
	int index = 0;
	if (player->videoCodecContext != NULL)
	{
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "video_format"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env,
				player->videoCodecContext->codec->long_name));
		sprintf(buffer, "%d", player->videoCodecContext->width);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "width"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
		sprintf(buffer, "%d", player->videoCodecContext->height);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "height"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
		sprintf(buffer, "%.3lf", av_q2d(getStream(player->videoStreamIndex)->r_frame_rate));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "frame_rate"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
		const AVPixFmtDescriptor * pixFmtDesctiptor = av_pix_fmt_desc_get(player->videoCodecContext->pix_fmt);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "pixel_format"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env,
				pixFmtDesctiptor != NULL ? pixFmtDesctiptor->name : "Unknown"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "surface_format"));
		int format = player->videoWindow != 0 ? ANativeWindow_getFormat(player->videoWindow) : -1;
		switch (format)
		{
			case WINDOW_FORMAT_RGBA_8888:
			{
				(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "RGBA 8888"));
				break;
			}
			case WINDOW_FORMAT_RGBX_8888:
			{
				(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "RGBX 8888"));
				break;
			}
			case WINDOW_FORMAT_RGB_565:
			{
				(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "RGB 565"));
				break;
			}
			case WINDOW_FORMAT_YV12:
			{
				(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "YV12"));
				break;
			}
			default:
			{
				(*env)->SetObjectArrayElement(env, result, index++, NULL);
				break;
			}
		}
		sprintf(buffer, "%d", player->videoUseLibyuv);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "use_libyuv"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
	}
	if (player->audioCodecContext != NULL)
	{
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "audio_format"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env,
				player->audioCodecContext->codec->long_name));
		sprintf(buffer, "%d", player->audioCodecContext->channels);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "channels"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
		sprintf(buffer, "%d", player->audioCodecContext->sample_rate);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "sample_rate"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
	}
	AVDictionaryEntry * entry = NULL;
	while ((entry = av_dict_get(player->formatContext->metadata, "", entry, AV_DICT_IGNORE_SUFFIX)) != NULL)
	{
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, entry->key));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, entry->value));
	}
	return result;
}

void initLibs(JavaVM * javaVM)
{
	loadJavaVM = javaVM;
	av_register_all();
	SLObjectItf engineObject;
	slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
	(*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
	(*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &slEngine);
}