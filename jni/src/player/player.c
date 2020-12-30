#include "player.h"
#include "util.h"

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

#include <unistd.h>

#define POINTER_CAST(addr) (void *) (long) addr
#define UNLOCK_AND_GOTO(mutex, label) {pthread_mutex_unlock(mutex); goto label;}
#define SEND_MESSAGE(env, p, b, what) (*(env))->CallVoidMethod(env, (p)->bridge.native, (b)->methodOnMessage, what)

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

#define INDEX_NO_STREAM -1
#define GAINING_THRESHOLD 100
#define AUDIO_MAX_ENQUEUE_SIZE 256
#define WINDOW_FORMAT_YV12 0x32315659
#define MAX_FPS 60

#define HAS_STREAM(p, stream) ((p)->av.stream##StreamIndex != INDEX_NO_STREAM)
#define GET_STREAM(p, stream) ((p)->av.format->streams[(p)->av.stream##StreamIndex])
#define GET_CONTEXT(p, stream) ((p)->av.stream##Context)

static JavaVM * loadJavaVM;
static SLEngineItf slEngine;

typedef struct Player Player;
typedef struct Bridge Bridge;
typedef struct PacketHolder PacketHolder;
typedef struct AudioBuffer AudioBuffer;
typedef struct VideoFrameExtra VideoFrameExtra;
typedef struct ScaleHolder ScaleHolder;

struct Player {
	struct {
		int interrupt;
		int errorCode;
		int seekAnyFrame;
	} meta;

	struct {
		int fd;
		long start;
		long end;
		long total;
		int cancelSeek;
		pthread_cond_t controlCond;
		pthread_mutex_t controlMutex;
	} file;

	struct {
		jobject native;
		SparseArray array;
	} bridge;

	struct {
		AVFormatContext * format;
		int audioStreamIndex;
		int videoStreamIndex;
		AVCodecContext * audioContext;
		AVCodecContext * videoContext;
	} av;

	struct {
		struct {
			int finished;
			pthread_t thread;
			pthread_mutex_t readMutex;
			pthread_cond_t flowCond;
			pthread_mutex_t flowMutex;
		} packets;

		struct {
			pthread_t thread;
			pthread_mutex_t frameMutex;
		} audio;

		struct {
			pthread_t thread;
			pthread_mutex_t frameMutex;
		} video;
	} decode;

	struct {
		int playing;
		pthread_cond_t finishCond;
		pthread_mutex_t finishMutex;
	} play;

	struct {
		struct {
			SLObjectItf outputMix;
			SLObjectItf player;
			SLPlayItf play;
			SLAndroidSimpleBufferQueueItf queue;
		} sl;

		BlockingQueue packetQueue;
		int finished;
		int resampleSampleRate;
		uint64_t resampleChannels;
		int bufferNeedEnqueueAfterDecode;
		BlockingQueue bufferQueue;
		AudioBuffer * buffer;
		pthread_cond_t sleepCond;
		pthread_cond_t bufferCond;
		pthread_mutex_t sleepBufferMutex;
	} audio;

	struct {
		BlockingQueue packetQueue;
		int finished;
		pthread_cond_t sleepCond;
		pthread_mutex_t sleepDrawMutex;
		pthread_cond_t queueCond;
		pthread_mutex_t queueMutex;
		BufferQueue * bufferQueue;
		pthread_t drawThread;
		ANativeWindow * window;
		int useLibyuv;
		int format;

		struct {
			uint8_t * data;
			int width;
			int height;
			int size;
		} lastBuffer;
	} video;

	struct {
		int64_t audioPosition;
		int64_t videoPosition;
		int audioPositionNotSync;
		int videoPositionNotSync;
		int64_t startTime;
		int64_t pausedPosition;
		int64_t lastDrawTimes[2];

		struct {
			int readFrame;
			int audioWorkFrame;
			int videoWorkFrame;
			int drawWorkFrame;
		} skip;
	} sync;
};

struct Bridge {
	JNIEnv * env;
	jmethodID methodOnSeek;
	jmethodID methodOnMessage;
};

struct PacketHolder {
	AVPacket * packet;
	int finish;
};

struct AudioBuffer {
	uint8_t * buffer;
	int size;
	int index;
	int64_t position;
	int64_t divider;
};

struct VideoFrameExtra {
	int width;
	int height;
	int64_t position;
};

struct ScaleHolder {
	int bufferSize;
	uint8_t * scaleBuffer;
	uint8_t * scaleData[4];
	int scaleLinesize[4];
};

static Bridge * obtainBridge(Player * player, JNIEnv * env) {
	int index = pthread_self();
	Bridge * bridge = sparseArrayGet(&player->bridge.array, index);
	if (!bridge) {
		bridge = malloc(sizeof(Bridge));
		jclass class = (*env)->GetObjectClass(env, player->bridge.native);
		bridge->env = env;
		bridge->methodOnSeek = (*env)->GetMethodID(env, class, "onSeek", "(J)V");
		bridge->methodOnMessage = (*env)->GetMethodID(env, class, "onMessage", "(I)V");
		sparseArrayAdd(&player->bridge.array, index, bridge);
	}
	return bridge;
}

static int getBytesPerPixel(int videoFormat) {
	switch (videoFormat) {
		case AV_PIX_FMT_YUV420P: return 1;
		case AV_PIX_FMT_RGBA: return 4;
		case AV_PIX_FMT_RGB565LE: return 2;
	}
	return 0;
}

static void packetQueueFreeCallback(void * data) {
	PacketHolder * packetHolder = (PacketHolder *) data;
	if (packetHolder->packet) {
		av_packet_unref(packetHolder->packet);
		free(packetHolder->packet);
	}
	free(packetHolder);
}

static void audioBufferQueueFreeCallback(void * data) {
	AudioBuffer * audioBuffer = (AudioBuffer *) data;
	if (audioBuffer) {
		av_freep(&audioBuffer->buffer);
		free(audioBuffer);
	}
}

static void videoBufferQueueFreeCallback(BufferItem * bufferItem) {
	if (bufferItem->extra) {
		free(bufferItem->extra);
		bufferItem->extra = NULL;
	}
}

static void updateAudioPositionSurrogate(Player * player, int64_t position, int forceUpdate) {
	if (forceUpdate || player->sync.audioPositionNotSync) {
		player->sync.startTime = getTime() - position;
		if ((!HAS_STREAM(player, audio) || player->audio.finished) && !forceUpdate) {
			player->sync.audioPositionNotSync = 0;
		}
	}
}

static int64_t calculatePosition(Player * player, int mayCalculateStartTime) {
	if (!HAS_STREAM(player, audio) || player->audio.finished) {
		if (player->play.playing) {
			if (mayCalculateStartTime || !player->video.finished) {
				return getTime() - player->sync.startTime;
			} else {
				return player->sync.videoPosition;
			}
		} else {
			return player->sync.pausedPosition;
		}
	} else {
		return player->sync.audioPosition;
	}
}

static void markStreamFinished(Player * player, int video) {
	if (video) {
		if (bufferQueueCount(player->video.bufferQueue) == 0 &&
				blockingQueueCount(&player->video.packetQueue) == 0) {
			player->video.finished = 1;
			condBroadcastLocked(&player->play.finishCond, &player->play.finishMutex);
		}
	} else {
		if (!player->audio.buffer && blockingQueueCount(&player->audio.bufferQueue) == 0
				&& blockingQueueCount(&player->audio.packetQueue) == 0) {
			player->audio.finished = 1;
			condBroadcastLocked(&player->play.finishCond, &player->play.finishMutex);
		}
	}
}

static int64_t calculateFrameTime(int64_t waitTime) {
	return getTime() + waitTime - min64(max64(waitTime / 2, 25), 100);
}

static int decodeFrame(Player * player, AVPacket * packet, AVFrame * frame, int video, int finish) {
	AVCodecContext * context = video ? GET_CONTEXT(player, video) : GET_CONTEXT(player, audio);
	avcodec_send_packet(context, packet);
	int ready = !avcodec_receive_frame(context, frame);
	if (finish) {
		// Sometimes need to decode more times after packets decoding finished to get more frames
		int finishAttempts = 10;
		while (!ready && finishAttempts-- > 0) {
			avcodec_send_packet(context, packet);
			ready = !avcodec_receive_frame(context, frame);
		}
	}
	return ready;
}

static int enqueueAudioBuffer(Player * player) {
	if (!player->play.playing) {
		player->audio.bufferNeedEnqueueAfterDecode = 1;
		return 0;
	}
	int64_t endAudioPosition = -1;
	if (player->audio.buffer) {
		AudioBuffer * audioBuffer = player->audio.buffer;
		if (audioBuffer->index >= audioBuffer->size) {
			endAudioPosition = audioBuffer->position + audioBuffer->size * 1000 / audioBuffer->divider;
			player->audio.buffer = NULL;
			av_freep(&audioBuffer->buffer);
			free(audioBuffer);
		}
	}
	if (!player->audio.buffer) {
		player->audio.buffer = blockingQueueGet(&player->audio.bufferQueue, 0);
	}
	if (player->audio.buffer) {
		AudioBuffer * audioBuffer = player->audio.buffer;
		if (audioBuffer->position >= 0) {
			player->sync.audioPosition = audioBuffer->position + audioBuffer->index * 1000 / audioBuffer->divider;
			player->sync.audioPositionNotSync = 0;
			LOG("play audio %" PRId64, player->sync.audioPosition);
		}
		int enqueueSize = min32(audioBuffer->size - audioBuffer->index, AUDIO_MAX_ENQUEUE_SIZE);
		(*player->audio.sl.queue)->Enqueue(player->audio.sl.queue,
				audioBuffer->buffer + audioBuffer->index, enqueueSize);
		audioBuffer->index += enqueueSize;
		player->audio.bufferNeedEnqueueAfterDecode = 0;
		return 1;
	} else {
		player->audio.bufferNeedEnqueueAfterDecode = 1;
		if (blockingQueueCount(&player->audio.packetQueue) == 0 && endAudioPosition >= 0) {
			updateAudioPositionSurrogate(player, endAudioPosition, 1);
		}
		return 0;
	}
}

static void audioPlayerCallback(UNUSED SLAndroidSimpleBufferQueueItf slQueue, void * context) {
	Player * player = (Player *) context;
	if (player->meta.interrupt) {
		return;
	}
	LOG("audio callback");
	pthread_mutex_lock(&player->audio.sleepBufferMutex);
	int result = enqueueAudioBuffer(player);
	if (result) {
		pthread_cond_broadcast(&player->audio.bufferCond);
	}
	pthread_mutex_unlock(&player->audio.sleepBufferMutex);
	markStreamFinished(player, 0);
}

static void * performDecodeAudio(void * data) {
	Player * player = (Player *) data;
	player->audio.bufferNeedEnqueueAfterDecode = 1;
	AVStream * stream = GET_STREAM(player, audio);
	AVCodecContext * context = GET_CONTEXT(player, audio);
	AVFrame * frame = av_frame_alloc();
	SwrContext * resampleContext = swr_alloc();
	int silentAudioLength = -1;
	PacketHolder * packetHolder = NULL;
	AVPacket lastPacket;
	int lastPacketValid = 0;

	while (!player->meta.interrupt) {
		packetHolder = (PacketHolder *) blockingQueueGet(&player->audio.packetQueue, 1);
		if (player->sync.skip.audioWorkFrame) {
			if (lastPacketValid) {
				av_packet_unref(&lastPacket);
				lastPacketValid = 0;
			}
			player->sync.skip.audioWorkFrame = 0;
		}
		if (!packetHolder || player->meta.interrupt) {
			break;
		}
		if (packetHolder->packet) {
			if (lastPacketValid) {
				av_packet_unref(&lastPacket);
			}
			av_packet_ref(&lastPacket, packetHolder->packet);
			lastPacketValid = 1;
		}
		condBroadcastLocked(&player->decode.packets.flowCond, &player->decode.packets.flowMutex);
		if (player->meta.interrupt) {
			break;
		}

		pthread_mutex_lock(&player->play.finishMutex);
		while (!player->meta.interrupt && !player->play.playing) {
			pthread_cond_wait(&player->play.finishCond, &player->play.finishMutex);
		}
		pthread_mutex_unlock(&player->play.finishMutex);
		if (player->meta.interrupt) {
			break;
		}

		while (1) {
			int success = 0;
			uint8_t ** dstData = NULL;
			if (player->sync.skip.audioWorkFrame) {
				goto SKIP_AUDIO_FRAME;
			}
			AVPacket * packet = packetHolder->packet;
			if (!packet) {
				if (!lastPacketValid) {
					goto SKIP_AUDIO_FRAME;
				}
				packet = &lastPacket;
			}
			pthread_mutex_lock(&player->decode.audio.frameMutex);
			if (player->sync.skip.audioWorkFrame) {
				UNLOCK_AND_GOTO(&player->decode.audio.frameMutex, SKIP_AUDIO_FRAME);
			}
			int ready = decodeFrame(player, packet, frame, 0, packetHolder->finish);
			pthread_mutex_unlock(&player->decode.audio.frameMutex);

			if (ready) {
				int64_t position;
				if (frame->pts == AV_NOPTS_VALUE) {
					position = -1;
				} else {
					position = max64(frame->pts * 1000 * stream->time_base.num / stream->time_base.den, 0);
				}
				if (player->meta.seekAnyFrame && player->sync.audioPositionNotSync &&
						position < player->sync.audioPosition) {
					success = 1;
					goto SKIP_AUDIO_FRAME;
				}

				if (frame->channel_layout == 0) {
					frame->channel_layout = av_get_default_channel_layout(frame->channels);
				}
				uint64_t srcChannelLayout = frame->channel_layout;
				uint64_t dstChannelLayout = player->audio.resampleChannels != 0
						? player->audio.resampleChannels : srcChannelLayout;
				int srcSamples = frame->nb_samples;
				int srcSampleRate = frame->sample_rate;
				int dstSampleRate = player->audio.resampleSampleRate != 0
						? player->audio.resampleSampleRate : srcSampleRate;
				int dstFormat = AV_SAMPLE_FMT_S16;
				av_opt_set_int(resampleContext, "in_channel_layout", srcChannelLayout, 0);
				av_opt_set_int(resampleContext, "out_channel_layout", dstChannelLayout,  0);
				av_opt_set_int(resampleContext, "in_sample_rate", srcSampleRate, 0);
				av_opt_set_int(resampleContext, "out_sample_rate", dstSampleRate, 0);
				av_opt_set_sample_fmt(resampleContext, "in_sample_fmt", frame->format, 0);
				av_opt_set_sample_fmt(resampleContext, "out_sample_fmt", dstFormat,  0);
				if (swr_init(resampleContext) < 0 || player->sync.skip.audioWorkFrame) {
					goto SKIP_AUDIO_FRAME;
				}
				int dstSamples = av_rescale_rnd(srcSamples, dstSampleRate, srcSampleRate, AV_ROUND_UP);
				int dstChannels = av_get_channel_layout_nb_channels(dstChannelLayout);
				int result = av_samples_alloc_array_and_samples(&dstData, frame->linesize, dstChannels,
						dstSamples, dstFormat, 0);
				if (result < 0 || player->sync.skip.audioWorkFrame) {
					goto SKIP_AUDIO_FRAME;
				}
				dstSamples = av_rescale_rnd(swr_get_delay(resampleContext, srcSampleRate) + srcSamples,
						dstSampleRate, srcSampleRate, AV_ROUND_UP);
				result = swr_convert(resampleContext, dstData, dstSamples, (const uint8_t **) frame->data, srcSamples);
				if (result < 0 || player->sync.skip.audioWorkFrame) {
					goto SKIP_AUDIO_FRAME;
				}

				int size = av_samples_get_buffer_size(NULL, dstChannels, result, dstFormat, 1);
				if (size < 0) {
					goto SKIP_AUDIO_FRAME;
				}
				pthread_mutex_lock(&player->audio.sleepBufferMutex);
				if (player->sync.skip.audioWorkFrame) {
					UNLOCK_AND_GOTO(&player->audio.sleepBufferMutex, SKIP_AUDIO_FRAME);
				}
				while (!player->meta.interrupt && blockingQueueCount(&player->audio.bufferQueue) >= 5) {
					pthread_cond_wait(&player->audio.bufferCond, &player->audio.sleepBufferMutex);
				}
				if (player->sync.skip.audioWorkFrame) {
					UNLOCK_AND_GOTO(&player->audio.sleepBufferMutex, SKIP_AUDIO_FRAME);
				}
				if (position >= 0 && player->audio.bufferNeedEnqueueAfterDecode) {
					player->sync.audioPosition = position;
					player->sync.audioPositionNotSync = 0;
				}
				while (!player->meta.interrupt && player->sync.videoPositionNotSync) {
					pthread_cond_wait(&player->audio.sleepCond, &player->audio.sleepBufferMutex);
				}
				if (player->sync.skip.audioWorkFrame) {
					UNLOCK_AND_GOTO(&player->audio.sleepBufferMutex, SKIP_AUDIO_FRAME);
				}
				int64_t videoPosition = player->sync.videoPosition;
				int64_t gaining = player->video.finished ? 0 : position - videoPosition;
				if (gaining > GAINING_THRESHOLD) {
					LOG("sleep audio %" PRId64 " %" PRId64, gaining, position);
					int64_t time = calculateFrameTime(gaining);
					while (!player->meta.interrupt && !player->sync.skip.audioWorkFrame) {
						if (condSleepUntilMs(&player->audio.sleepCond, &player->audio.sleepBufferMutex, time)) {
							break;
						}
					}
				}
				if (player->sync.skip.audioWorkFrame) {
					UNLOCK_AND_GOTO(&player->audio.sleepBufferMutex, SKIP_AUDIO_FRAME);
				}
				AudioBuffer * audioBuffer = malloc(sizeof(AudioBuffer));
				audioBuffer->buffer = dstData[0];
				audioBuffer->index = 0;
				audioBuffer->size = size;
				audioBuffer->position = position;
				audioBuffer->divider = 2 * frame->channels * dstSampleRate;
				// Fix loud click on video start even on low sound level by muting sound buffer for 40 milliseconds
				if (silentAudioLength < 0) {
					silentAudioLength = 40 * audioBuffer->divider / 1000;
				}
				if (silentAudioLength > 0) {
					int count = silentAudioLength >= size ? size : silentAudioLength;
					memset(audioBuffer->buffer, 0, count);
					silentAudioLength -= count;
				}
				int needEnqueue = player->audio.bufferNeedEnqueueAfterDecode;
				blockingQueueAdd(&player->audio.bufferQueue, audioBuffer);
				if (needEnqueue) {
					enqueueAudioBuffer(player);
				}
				pthread_mutex_unlock(&player->audio.sleepBufferMutex);
				success = 1;
			}

			SKIP_AUDIO_FRAME:
			if (dstData) {
				if (!success) {
					av_freep(&dstData[0]);
				}
				av_freep(&dstData);
			}
			if (!success || !packetHolder->finish) {
				break;
			}
			if (packetHolder->finish && frame->pts >= packet->pts) {
				pthread_mutex_lock(&player->decode.audio.frameMutex);
				if (!player->sync.skip.audioWorkFrame) {
					avcodec_flush_buffers(context);
				}
				pthread_mutex_unlock(&player->decode.audio.frameMutex);
				break;
			}
		}
		packetQueueFreeCallback(packetHolder);
		packetHolder = NULL;
	}
	if (packetHolder) {
		packetQueueFreeCallback(packetHolder);
	}
	if (lastPacketValid) {
		av_packet_unref(&lastPacket);
	}
	swr_free(&resampleContext);
	av_frame_free(&frame);
	return NULL;
}

static void drawWindow(Player * player, uint8_t * buffer, int width, int height, int lastWidth, int lastHeight,
		JNIEnv * env) {
	if (player->video.window) {
		if (width != lastWidth || height != lastHeight) {
			ANativeWindow_setBuffersGeometry(player->video.window, width, height,
					ANativeWindow_getFormat(player->video.window));
			Bridge * bridge = obtainBridge(player, env);
			SEND_MESSAGE(env, player, bridge, BRIDGE_MESSAGE_SIZE_CHANGED);
		}
		ANativeWindow_Buffer canvas;
		if (ANativeWindow_lock(player->video.window, &canvas, NULL) == 0) {
			if (canvas.width >= width && canvas.height >= height) {
				// Width and height can be smaller in the moment of surface changing and before it was handled
				uint8_t * to = canvas.bits;
				if (player->video.format == AV_PIX_FMT_YUV420P) {
					for (int i = 0; i < height; i++) {
						memcpy(to, buffer, width);
						to += canvas.stride;
						buffer += width;
					}
					memset(to, 127, canvas.stride * height / 2);
					for (int i = 0; i < height / 2; i++) {
						memcpy(to, buffer, width / 2);
						to += canvas.stride / 2;
						buffer += width / 2;
					}
					if (canvas.stride % 32 != 0) {
						to += height / 2 * 8; // Align to 16
					}
					for (int i = 0; i < height / 2; i++) {
						memcpy(to, buffer, width / 2);
						to += canvas.stride / 2;
						buffer += width / 2;
					}
				} else {
					int bytesPerPixel = getBytesPerPixel(player->video.format);
					if (bytesPerPixel > 0) {
						for (int i = 0; i < height; i++) {
							memcpy(to, buffer, bytesPerPixel * width);
							to += bytesPerPixel * canvas.stride;
							buffer += bytesPerPixel * width;
						}
					}
				}
			}
			ANativeWindow_unlockAndPost(player->video.window);
		}
	}
}

static void * performDraw(void * data) {
	Player * player = (Player *) data;
	JNIEnv * env;
	(*loadJavaVM)->AttachCurrentThread(loadJavaVM, &env, NULL);
	AVCodecContext * context = GET_CONTEXT(player, video);
	int lastWidth = context->width;
	int lastHeight = context->height;
	while (!player->meta.interrupt) {
		BufferItem * bufferItem = NULL;
		pthread_mutex_lock(&player->video.queueMutex);
		while (!player->meta.interrupt && !bufferItem) {
			if (player->video.bufferQueue) {
				bufferItem = bufferQueueSeize(player->video.bufferQueue);
			}
			if (!bufferItem) {
				pthread_cond_wait(&player->video.queueCond, &player->video.queueMutex);
			}
		}
		player->sync.skip.drawWorkFrame = 0;
		pthread_mutex_unlock(&player->video.queueMutex);
		if (player->meta.interrupt) {
			goto SKIP_DRAW_FRAME;
		}

		pthread_mutex_lock(&player->play.finishMutex);
		while (!player->meta.interrupt && !player->play.playing) {
			pthread_cond_wait(&player->play.finishCond, &player->play.finishMutex);
		}
		pthread_mutex_unlock(&player->play.finishMutex);
		if (player->meta.interrupt) {
			goto SKIP_DRAW_FRAME;
		}

		pthread_mutex_lock(&player->video.sleepDrawMutex);
		if (player->sync.skip.drawWorkFrame) {
			UNLOCK_AND_GOTO(&player->video.sleepDrawMutex, SKIP_DRAW_FRAME);
		}
		VideoFrameExtra * extra = bufferItem->extra;
		int64_t position = calculatePosition(player, 1);
		int64_t waitTime = 0;
		if (extra->position >= 0) {
			player->sync.videoPosition = extra->position;
			waitTime = extra->position - position;
			if (player->sync.videoPositionNotSync) {
				player->sync.videoPositionNotSync = 0;
				Bridge * bridge = obtainBridge(player, env);
				SEND_MESSAGE(env, player, bridge, BRIDGE_MESSAGE_END_SEEKING);
				pthread_mutex_unlock(&player->video.sleepDrawMutex);
				condBroadcastLocked(&player->audio.sleepCond, &player->audio.sleepBufferMutex);
				pthread_mutex_lock(&player->video.sleepDrawMutex);
				if (player->sync.skip.drawWorkFrame) {
					UNLOCK_AND_GOTO(&player->video.sleepDrawMutex, SKIP_DRAW_FRAME);
				}
			}
		}
		if (waitTime > 0) {
			LOG("sleep video %" PRId64 " %" PRId64 " %" PRId64, waitTime, player->sync.videoPosition, position);
			int64_t time = calculateFrameTime(waitTime);
			while (!player->meta.interrupt && !player->sync.skip.drawWorkFrame) {
				if (condSleepUntilMs(&player->video.sleepCond, &player->video.sleepDrawMutex, time)) {
					break;
				}
			}
			waitTime = 0;
		}
		if (player->sync.skip.drawWorkFrame) {
			UNLOCK_AND_GOTO(&player->video.sleepDrawMutex, SKIP_DRAW_FRAME);
		}
		if (player->sync.audioPositionNotSync) {
			updateAudioPositionSurrogate(player, position, 0);
		} else {
			int64_t gaining = -waitTime;
			if (!HAS_STREAM(player, audio) && gaining > GAINING_THRESHOLD) {
				player->sync.startTime += gaining;
			}
		}
		LOG("draw video %" PRId64, player->sync.videoPosition);
		int bufferSize = bufferItem->bufferSize;
		if (bufferSize > player->video.lastBuffer.size) {
			player->video.lastBuffer.data = realloc(player->video.lastBuffer.data, bufferSize);
			player->video.lastBuffer.size = bufferSize;
		}
		memcpy(player->video.lastBuffer.data, bufferItem->buffer, bufferSize);
		player->video.lastBuffer.width = extra->width;
		player->video.lastBuffer.height = extra->height;
		if ((player->sync.lastDrawTimes[0] - player->sync.lastDrawTimes[1]) * MAX_FPS >= 1000
				|| (getTime() - player->sync.lastDrawTimes[0]) * MAX_FPS >= 1000) {
			// Avoid FPS > MAX_FPS
			drawWindow(player, bufferItem->buffer, extra->width, extra->height, lastWidth, lastHeight, env);
			lastWidth = extra->width;
			lastHeight = extra->height;
			player->sync.lastDrawTimes[1] = player->sync.lastDrawTimes[0];
			player->sync.lastDrawTimes[0] = getTime();
		}
		pthread_mutex_unlock(&player->video.sleepDrawMutex);

		SKIP_DRAW_FRAME:
		if (bufferItem) {
			free(bufferItem->extra);
			bufferItem->extra = NULL;
			pthread_mutex_lock(&player->video.queueMutex);
			bufferQueueRelease(player->video.bufferQueue, bufferItem);
			pthread_cond_broadcast(&player->video.queueCond);
			pthread_mutex_unlock(&player->video.queueMutex);
			markStreamFinished(player, 1);
		}
	}
	(*loadJavaVM)->DetachCurrentThread(loadJavaVM);
	return NULL;
}

static int getVideoBufferSize(int videoFormat, int width, int height) {
	switch (videoFormat) {
		case AV_PIX_FMT_RGBA: return width * height * 4;
		case AV_PIX_FMT_RGB565LE: return width * height * 2;
		case AV_PIX_FMT_YUV420P: return width * height * 3 / 2;
		default: return 0;
	}
}

static void extendScaleHolder(ScaleHolder * scaleHolder, int bufferSize, int width, int height,
		int bytesPerPixel, int isYUV) {
	if (bufferSize > scaleHolder->bufferSize) {
		scaleHolder->bufferSize = bufferSize;
		if (scaleHolder->scaleBuffer) {
			av_free(scaleHolder->scaleBuffer);
		}
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

static void * performDecodeVideo(void * data) {
	Player * player = (Player *) data;
	AVStream * stream = GET_STREAM(player, video);
	AVCodecContext * context = GET_CONTEXT(player, video);
	pthread_mutex_lock(&player->video.sleepDrawMutex);
	while (!player->meta.interrupt && !player->video.bufferQueue) {
		pthread_cond_wait(&player->video.sleepCond, &player->video.sleepDrawMutex);
	}
	pthread_mutex_unlock(&player->video.sleepDrawMutex);
	if (player->meta.interrupt) {
		return NULL;
	}

	int bytesPerPixel = getBytesPerPixel(player->video.format);
	int isYUV = player->video.format == AV_PIX_FMT_YUV420P;
	AVFrame * frame = av_frame_alloc();
	ScaleHolder scaleHolder;
	scaleHolder.bufferSize = 0;
	scaleHolder.scaleBuffer = NULL;
	int lastWidth = context->width;
	int lastHeight = context->height;
	extendScaleHolder(&scaleHolder, player->video.bufferQueue->bufferSize,
			lastWidth, lastHeight, bytesPerPixel, isYUV);
	SparseArray scaleContexts;
	sparseArrayInit(&scaleContexts, 1);
	PacketHolder * packetHolder = NULL;
	AVPacket lastPacket;
	int lastPacketValid = 0;

	int totalMeasurements = 10;
	int currentMeasurement = 0;
	int measurements[2 * totalMeasurements];

	while (!player->meta.interrupt) {
		packetHolder = (PacketHolder *) blockingQueueGet(&player->video.packetQueue, 1);
		if (player->sync.skip.videoWorkFrame) {
			if (lastPacketValid) {
				av_packet_unref(&lastPacket);
				lastPacketValid = 0;
			}
			player->sync.skip.videoWorkFrame = 0;
		}
		if (!packetHolder || player->meta.interrupt) {
			break;
		}
		if (packetHolder->packet) {
			if (lastPacketValid) {
				av_packet_unref(&lastPacket);
			}
			av_packet_ref(&lastPacket, packetHolder->packet);
			lastPacketValid = 1;
		}
		condBroadcastLocked(&player->decode.packets.flowCond, &player->decode.packets.flowMutex);
		if (player->meta.interrupt) {
			break;
		}

		pthread_mutex_lock(&player->play.finishMutex);
		while (!player->meta.interrupt && !player->play.playing) {
			pthread_cond_wait(&player->play.finishCond, &player->play.finishMutex);
		}
		pthread_mutex_unlock(&player->play.finishMutex);
		if (player->meta.interrupt) {
			break;
		}

		while (1) {
			int success = 0;
			VideoFrameExtra * extra = NULL;
			if (player->sync.skip.videoWorkFrame) {
				goto SKIP_VIDEO_FRAME;
			}
			AVPacket * packet = packetHolder->packet;
			if (!packet) {
				if (!lastPacketValid) {
					goto SKIP_VIDEO_FRAME;
				}
				packet = &lastPacket;
			}
			pthread_mutex_lock(&player->decode.video.frameMutex);
			if (player->sync.skip.videoWorkFrame) {
				UNLOCK_AND_GOTO(&player->decode.video.frameMutex, SKIP_VIDEO_FRAME);
			}
			int ready = decodeFrame(player, packet, frame, 1, packetHolder->finish);
			pthread_mutex_unlock(&player->decode.video.frameMutex);

			if (ready) {
				extra = malloc(sizeof(VideoFrameExtra));
				extra->width = frame->width;
				extra->height = frame->height;
				if (frame->pts == AV_NOPTS_VALUE) {
					extra->position = -1;
				} else {
					extra->position = max64(frame->pts * 1000 * stream->time_base.num / stream->time_base.den, 0);
				}
				if (player->meta.seekAnyFrame && player->sync.videoPositionNotSync &&
						extra->position < player->sync.videoPosition) {
					success = 1;
					goto SKIP_VIDEO_FRAME;
				}

				int extendedBufferSize = 0;
				if (lastWidth != frame->width || lastHeight != frame->height) {
					extendedBufferSize = getVideoBufferSize(player->video.format, frame->width, frame->height);
					extendScaleHolder(&scaleHolder, extendedBufferSize, frame->width, frame->height,
							bytesPerPixel, isYUV);
					lastWidth = frame->width;
					lastHeight = frame->height;
				}
				int useLibyuv = frame->format == AV_PIX_FMT_YUV420P && player->video.format == AV_PIX_FMT_RGBA;
				uint64_t startTime = 0;
				if (useLibyuv) {
					if (player->video.useLibyuv >= 0) {
						useLibyuv = player->video.useLibyuv;
					} else {
						if (currentMeasurement < totalMeasurements) {
							useLibyuv = 0;
						}
						if (currentMeasurement < 2 * totalMeasurements) {
							startTime = getTimeUs();
						}
					}
				}
				if (useLibyuv) {
					I420ToABGR(frame->data[0], frame->linesize[0], frame->data[1], frame->linesize[1],
							frame->data[2], frame->linesize[2], scaleHolder.scaleBuffer, 4 * frame->width,
							frame->width, frame->height);
				} else {
					int scaleContextIndex = (frame->width) << 16 | frame->height;
					struct SwsContext * scaleContext = sparseArrayGet(&scaleContexts, scaleContextIndex);
					if (!scaleContext) {
						scaleContext = sws_getContext(frame->width, frame->height, frame->format,
								frame->width, frame->height, player->video.format, SWS_FAST_BILINEAR, NULL, NULL, NULL);
						sparseArrayAdd(&scaleContexts, scaleContextIndex, scaleContext);
					}
					sws_scale(scaleContext, (uint8_t const * const *) frame->data, frame->linesize,
							0, frame->height, scaleHolder.scaleData, scaleHolder.scaleLinesize);
				}
				if (startTime != 0) {
					if (currentMeasurement < 2 * totalMeasurements) {
						measurements[currentMeasurement++] = (int) (getTimeUs() - startTime);
						if (currentMeasurement == 2 * totalMeasurements) {
							int avg1 = 0;
							int avg2 = 0;
							for (int i = 0; i < totalMeasurements; i++) {
								avg1 += measurements[i];
							}
							for (int i = totalMeasurements; i < 2 * totalMeasurements; i++) {
								avg2 += measurements[i];
							}
							player->video.useLibyuv = avg2 <= avg1 ? 1 : 0;
						}
					}
				}

				pthread_mutex_lock(&player->video.queueMutex);
				if (player->sync.skip.videoWorkFrame) {
					UNLOCK_AND_GOTO(&player->video.queueMutex, SKIP_VIDEO_FRAME);
				}
				if (extendedBufferSize > 0) {
					bufferQueueExtend(player->video.bufferQueue, extendedBufferSize);
				}
				BufferItem * bufferItem = NULL;
				while (!player->meta.interrupt && !player->sync.skip.videoWorkFrame && !bufferItem) {
					bufferItem = bufferQueuePrepare(player->video.bufferQueue);
					if (!bufferItem) {
						pthread_cond_wait(&player->video.queueCond, &player->video.queueMutex);
					}
				}
				if (bufferItem) {
					memcpy(bufferItem->buffer, scaleHolder.scaleBuffer, player->video.bufferQueue->bufferSize);
					bufferItem->extra = extra;
					bufferQueueAdd(player->video.bufferQueue, bufferItem);
					pthread_cond_broadcast(&player->video.queueCond);
					success = 1;
				}
				pthread_mutex_unlock(&player->video.queueMutex);
			}

			SKIP_VIDEO_FRAME:
			if (!success && extra) {
				free(extra);
			}
			if (!success || !packetHolder->finish) {
				break;
			}
			if (packetHolder->finish && frame->pts >= packet->pts) {
				pthread_mutex_lock(&player->decode.video.frameMutex);
				if (!player->sync.skip.videoWorkFrame) {
					avcodec_flush_buffers(context);
				}
				pthread_mutex_unlock(&player->decode.video.frameMutex);
				break;
			}
		}
		markStreamFinished(player, 1);
		packetQueueFreeCallback(packetHolder);
		packetHolder = NULL;
	}
	if (packetHolder) {
		packetQueueFreeCallback(packetHolder);
	}
	if (lastPacketValid) {
		av_packet_unref(&lastPacket);
	}
	sparseArrayDestroy(&scaleContexts, (SparseArrayDestroyCallback) sws_freeContext);
	av_free(scaleHolder.scaleBuffer);
	av_frame_free(&frame);
	return NULL;
}

static PacketHolder * createPacketHolder(int allocPacket, int finish) {
	PacketHolder * packetHolder = malloc(sizeof(PacketHolder));
	packetHolder->packet = allocPacket ? malloc(sizeof(AVPacket)) : NULL;
	packetHolder->finish = finish;
	return packetHolder;
}

static void * performDecodePackets(void * data) {
	Player * player = (Player *) data;
	JNIEnv * env;
	(*loadJavaVM)->AttachCurrentThread(loadJavaVM, &env, NULL);
	Bridge * bridge = obtainBridge(player, env);
	AVPacket packet;
	while (!player->meta.interrupt) {
		while (!player->meta.interrupt) {
			player->sync.skip.readFrame = 0;
			pthread_mutex_lock(&player->decode.packets.readMutex);
			int success = av_read_frame(player->av.format, &packet) >= 0;
			pthread_mutex_unlock(&player->decode.packets.readMutex);
			if (!success) {
				break;
			}
			pthread_mutex_lock(&player->decode.packets.flowMutex);
			if (player->sync.skip.readFrame) {
				goto SKIP_FRAME;
			}
			while (!player->meta.interrupt &&
					(!HAS_STREAM(player, video) || blockingQueueCount(&player->video.packetQueue) >= 10) &&
					(!HAS_STREAM(player, audio) || blockingQueueCount(&player->audio.packetQueue) >= 20)) {
				pthread_cond_wait(&player->decode.packets.flowCond, &player->decode.packets.flowMutex);
			}
			if (player->sync.skip.readFrame) {
				goto SKIP_FRAME;
			}
			int isAudio = packet.stream_index == player->av.audioStreamIndex;
			int isVideo = packet.stream_index == player->av.videoStreamIndex;
			if (isAudio || isVideo) {
				PacketHolder * packetHolder = createPacketHolder(1, 0);
				av_packet_ref(packetHolder->packet, &packet);
				if (isAudio) {
					blockingQueueAdd(&player->audio.packetQueue, packetHolder);
					player->audio.finished = 0;
					LOG("enqueue audio %" PRId64, packet.pts);
				} else if (isVideo) {
					blockingQueueAdd(&player->video.packetQueue, packetHolder);
					player->video.finished = 0;
					LOG("enqueue video %" PRId64, packet.pts);
				}
			}
			SKIP_FRAME:
			av_packet_unref(&packet);
			pthread_mutex_unlock(&player->decode.packets.flowMutex);
		}
		pthread_mutex_lock(&player->decode.packets.flowMutex);
		if (!player->sync.skip.readFrame) {
			if (HAS_STREAM(player, audio)) {
				blockingQueueAdd(&player->audio.packetQueue, createPacketHolder(0, 1));
				player->audio.finished = 0;
			}
			if (HAS_STREAM(player, video)) {
				blockingQueueAdd(&player->video.packetQueue, createPacketHolder(0, 1));
				player->video.finished = 0;
			}
		}
		pthread_mutex_unlock(&player->decode.packets.flowMutex);
		pthread_mutex_lock(&player->play.finishMutex);
		player->decode.packets.finished = 1;
		int needSendFinishMessage = 1;
		while (!player->meta.interrupt && player->decode.packets.finished) {
			if (needSendFinishMessage &&
					(player->audio.finished || !HAS_STREAM(player, audio)) &&
					(player->video.finished || !HAS_STREAM(player, video))) {
				needSendFinishMessage = 0;
				SEND_MESSAGE(env, player, bridge, BRIDGE_MESSAGE_PLAYBACK_COMPLETE);
			}
			pthread_cond_wait(&player->play.finishCond, &player->play.finishMutex);
		}
		pthread_mutex_unlock(&player->play.finishMutex);
	}
	blockingQueueAdd(&player->audio.packetQueue, NULL);
	blockingQueueAdd(&player->video.packetQueue, NULL);
	pthread_join(player->decode.audio.thread, NULL);
	pthread_join(player->decode.video.thread, NULL);
	pthread_join(player->video.drawThread, NULL);
	(*loadJavaVM)->DetachCurrentThread(loadJavaVM);
	return NULL;
}

static void releasePlayerSurface(Player * player) {
	if (player->video.window) {
		ANativeWindow_release(player->video.window);
		player->video.window = NULL;
	}
}

static void setPlayerSurfaceLocked(JNIEnv * env, Player * player, jobject surface) {
	if (surface) {
		player->video.window = ANativeWindow_fromSurface(env, surface);
		int format = ANativeWindow_getFormat(player->video.window);
		AVCodecContext * context = GET_CONTEXT(player, video);
		int width = context->width;
		int height = context->height;
		if (!player->video.bufferQueue) {
			int videoFormat = -1;
			switch (format) {
				case WINDOW_FORMAT_RGBA_8888:
				case WINDOW_FORMAT_RGBX_8888: {
					videoFormat = AV_PIX_FMT_RGBA;
					break;
				}
				case WINDOW_FORMAT_RGB_565: {
					videoFormat = AV_PIX_FMT_RGB565LE;
					break;
				}
				case WINDOW_FORMAT_YV12: {
					videoFormat = AV_PIX_FMT_YUV420P;
					break;
				}
			}
			if (videoFormat >= 0) {
				int videoBufferSize = getVideoBufferSize(videoFormat, width, height);
				player->video.format = videoFormat;
				player->video.bufferQueue = malloc(sizeof(BufferQueue));
				bufferQueueInit(player->video.bufferQueue, videoBufferSize, 3);
				player->video.lastBuffer.data = malloc(videoBufferSize);
				player->video.lastBuffer.size = videoBufferSize;
				if (videoFormat == AV_PIX_FMT_RGBA) {
					// RGBA_8888 "black" buffer
					int count = 4 * width * height;
					memset(player->video.lastBuffer.data, 0x00, count);
					for (int i = 3; i < count; i += 4) {
						player->video.lastBuffer.data[i] = 0xff;
					}
				} else if (videoFormat == AV_PIX_FMT_RGB565LE) {
					// RGB_565 "black" buffer
					memset(player->video.lastBuffer.data, 0x00, 2 * width * height);
				} else if (videoFormat == AV_PIX_FMT_YUV420P) {
					// YV12 "black" buffer
					memset(player->video.lastBuffer.data, 0, width * height);
					memset(player->video.lastBuffer.data + width * height, 0x7f, width * height / 2);
				}
				pthread_cond_broadcast(&player->video.sleepCond);
			}
		}
		if (player->video.lastBuffer.width >= 0) {
			width = player->video.lastBuffer.width;
		}
		if (player->video.lastBuffer.height >= 0) {
			height = player->video.lastBuffer.height;
		}
		ANativeWindow_setBuffersGeometry(player->video.window, width, height, format);
		if (player->video.lastBuffer.data) {
			drawWindow(player, player->video.lastBuffer.data, width, height, width, height, env);
		}
	}
}

static int bufferReadData(void * opaque, uint8_t * buf, int bufSize) {
	int result = -1;
	Player * player = opaque;
	pthread_mutex_lock(&player->file.controlMutex);
	int64_t offset = lseek(player->file.fd, 0, SEEK_CUR);
	LOG("read data from=%" PRId64 " size=%d range=[%ld-%ld/%ld]",
		offset, bufSize, player->file.start, player->file.end, player->file.total);
	if (offset >= 0) {
		int request = 1;
		while (!player->meta.interrupt && !player->file.cancelSeek) {
			if (player->file.total >= 0 && offset >= player->file.total) {
				break;
			}
			if (offset >= player->file.start && offset < player->file.end) {
				int64_t maxCount64 = player->file.end - offset;
				int maxCount = bufSize > maxCount64 ? maxCount64 : bufSize;
				result = read(player->file.fd, buf, maxCount);
				break;
			}
			if (request) {
				request = 0;
				Bridge * bridge = sparseArrayGet(&player->bridge.array, (int) pthread_self());
				if (bridge) {
					LOG("read data request");
					(*bridge->env)->CallVoidMethod(bridge->env, player->bridge.native, bridge->methodOnSeek, offset);
				}
			}
			LOG("read data wait");
			pthread_cond_wait(&player->file.controlCond, &player->file.controlMutex);
		}
	}
	LOG("read data result size=%d", result);
	pthread_mutex_unlock(&player->file.controlMutex);
	return result;
}

static int64_t bufferSeekData(void * opaque, int64_t offset, int whence) {
	int64_t result = -1;
	LOG("seek data offset=%" PRId64 " whence=%d", offset, whence);
	Player * player = opaque;
	pthread_mutex_lock(&player->file.controlMutex);
	if (whence == SEEK_SET || whence == SEEK_CUR) {
		result = lseek(player->file.fd, offset, whence);
	} else if (whence == SEEK_END && player->file.total >= 0) {
		result = lseek(player->file.fd, player->file.total + offset, SEEK_SET);
	} else if (whence == AVSEEK_SIZE && player->file.total >= 0) {
		result = player->file.total;
	}
	LOG("seek data result offset=%" PRId64, result);
	pthread_mutex_unlock(&player->file.controlMutex);
	return result;
}

static Player * createPlayer() {
	Player * player = malloc(sizeof(Player));
	memset(player, 0, sizeof(Player));
	player->file.total = -1;
	player->av.audioStreamIndex = INDEX_NO_STREAM;
	player->av.videoStreamIndex = INDEX_NO_STREAM;
	player->video.useLibyuv = -1;
	player->video.lastBuffer.width = -1;
	player->video.lastBuffer.height = -1;
	sparseArrayInit(&player->bridge.array, 4);
	pthread_mutex_init(&player->file.controlMutex, NULL);
	pthread_cond_init(&player->file.controlCond, NULL);
	pthread_mutex_init(&player->decode.packets.readMutex, NULL);
	pthread_cond_init(&player->decode.packets.flowCond, NULL);
	pthread_mutex_init(&player->decode.packets.flowMutex, NULL);
	pthread_mutex_init(&player->decode.audio.frameMutex, NULL);
	pthread_mutex_init(&player->decode.video.frameMutex, NULL);
	pthread_cond_init(&player->play.finishCond, NULL);
	pthread_mutex_init(&player->play.finishMutex, NULL);
	pthread_cond_init(&player->audio.sleepCond, NULL);
	pthread_cond_init(&player->audio.bufferCond, NULL);
	pthread_mutex_init(&player->audio.sleepBufferMutex, NULL);
	pthread_cond_init(&player->video.sleepCond, NULL);
	pthread_mutex_init(&player->video.sleepDrawMutex, NULL);
	pthread_cond_init(&player->video.queueCond, NULL);
	pthread_mutex_init(&player->video.queueMutex, NULL);
	blockingQueueInit(&player->audio.packetQueue);
	blockingQueueInit(&player->video.packetQueue);
	blockingQueueInit(&player->audio.bufferQueue);
	return player;
}

#define NEED_RESAMPLE_NO 0
#define NEED_RESAMPLE_MAY_48000 1
#define NEED_RESAMPLE_FORCE_44100 2

jlong preInit(UNUSED JNIEnv * env, jint fd) {
	Player * player = createPlayer();
	player->file.fd = fd;
	return (jlong) (long) player;
}

void init(JNIEnv * env, jlong pointer, jobject nativeBridge, jboolean seekAnyFrame) {
	Player * player = POINTER_CAST(pointer);
	player->meta.seekAnyFrame = !!seekAnyFrame;
	player->bridge.native = (*env)->NewGlobalRef(env, nativeBridge);
	obtainBridge(player, env);
	int contextBufferSize = 8 * 1024;
	uint8_t * contextBuffer = av_malloc(contextBufferSize);
	AVIOContext * ioContext = avio_alloc_context(contextBuffer, contextBufferSize, 0, player,
			&bufferReadData, NULL, &bufferSeekData);
	if (!ioContext) {
		av_free(contextBuffer);
		player->meta.errorCode = ERROR_LOAD_IO;
		return;
	}
	AVFormatContext * formatContext = avformat_alloc_context();
	formatContext->pb = ioContext;
	LOG("start avformat_open_input");
	if (avformat_open_input(&formatContext, "", NULL, NULL) != 0) {
		avformat_close_input(&formatContext);
		av_free(ioContext->buffer);
		av_free(ioContext);
		player->meta.errorCode = ERROR_LOAD_FORMAT;
		return;
	}
	LOG("end avformat_open_input");
	player->av.format = formatContext;
	LOG("start avformat_find_stream_info");
	if (avformat_find_stream_info(formatContext, NULL) < 0) {
		player->meta.errorCode = ERROR_FIND_STREAM_INFO;
		return;
	}
	LOG("end avformat_find_stream_info");
	int audioStreamIndex = INDEX_NO_STREAM;
	int videoStreamIndex = INDEX_NO_STREAM;
	for (int i = 0; i < (int) formatContext->nb_streams; i++) {
		int codecType = formatContext->streams[i]->codecpar->codec_type;
		if (audioStreamIndex == INDEX_NO_STREAM && codecType == AVMEDIA_TYPE_AUDIO) {
			audioStreamIndex = i;
		} else if (videoStreamIndex == INDEX_NO_STREAM && codecType == AVMEDIA_TYPE_VIDEO) {
			videoStreamIndex = i;
		}
	}
	if (videoStreamIndex == INDEX_NO_STREAM) {
		player->meta.errorCode = ERROR_FIND_STREAM;
		return;
	}
	AVStream * audioStream = audioStreamIndex != INDEX_NO_STREAM ? formatContext->streams[audioStreamIndex] : NULL;
	AVStream * videoStream = videoStreamIndex != INDEX_NO_STREAM ? formatContext->streams[videoStreamIndex] : NULL;
	AVCodec * audioCodec = audioStream ? avcodec_find_decoder(audioStream->codecpar->codec_id) : NULL;
	AVCodec * videoCodec = videoStream ? avcodec_find_decoder(videoStream->codecpar->codec_id) : NULL;
	if (!audioCodec) {
		audioStreamIndex = INDEX_NO_STREAM;
		audioStream = NULL;
	}
	if (!videoCodec) {
		player->meta.errorCode = ERROR_FIND_CODEC;
		return;
	}
	if (audioCodec) {
		AVCodecContext * audioContext = avcodec_alloc_context3(audioCodec);
		if (avcodec_parameters_to_context(audioContext, audioStream->codecpar) ||
				avcodec_open2(audioContext, audioCodec, NULL) < 0) {
			avcodec_free_context(&audioContext);
			player->meta.errorCode = ERROR_OPEN_CODEC;
			return;
		}
		player->av.audioStreamIndex = audioStreamIndex;
		player->av.audioContext = audioContext;
	}
	if (videoCodec) {
		AVCodecContext * videoContext = avcodec_alloc_context3(videoCodec);
		if (avcodec_parameters_to_context(videoContext, videoStream->codecpar) ||
				avcodec_open2(videoContext, videoCodec, NULL) < 0) {
			avcodec_free_context(&videoContext);
			player->meta.errorCode = ERROR_OPEN_CODEC;
			return;
		}
		player->av.videoStreamIndex = videoStreamIndex;
		player->av.videoContext = videoContext;
	}
	if (audioStream) {
		SLresult result;
		int success = 0;
		int channels = player->av.audioContext->channels;
		if (channels != 1 && channels != 2) {
			channels = 2;
			player->audio.resampleChannels = AV_CH_FRONT_LEFT | AV_CH_FRONT_RIGHT;
		}
		int channelMask = channels == 2 ? SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT : SL_SPEAKER_FRONT_CENTER;
		const SLInterfaceID volumeIds[] = {SL_IID_VOLUME};
		const SLboolean volumeRequired[] = {SL_BOOLEAN_FALSE};
		result = (*slEngine)->CreateOutputMix(slEngine, &player->audio.sl.outputMix, 1, volumeIds, volumeRequired);
		if (result != SL_RESULT_SUCCESS) {
			LOGP("SLES CreateOutputMix: result=%d", (int) result);
			goto HANDLE_SL_INIT_ERROR;
		}
		result = (*player->audio.sl.outputMix)->Realize(player->audio.sl.outputMix, SL_BOOLEAN_FALSE);
		if (result != SL_RESULT_SUCCESS) {
			LOGP("SLES outputMix.Realize: result=%d", (int) result);
			goto HANDLE_SL_INIT_ERROR;
		}
		SLDataLocator_AndroidSimpleBufferQueue locatorQueue = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
		SLDataFormat_PCM formatPCM = {SL_DATAFORMAT_PCM, channels, 0, SL_PCMSAMPLEFORMAT_FIXED_16,
				SL_PCMSAMPLEFORMAT_FIXED_16, channelMask, SL_BYTEORDER_LITTLEENDIAN};
		SLDataSource dataSource = {&locatorQueue, &formatPCM};
		SLDataLocator_OutputMix locatorOutputMix = {SL_DATALOCATOR_OUTPUTMIX, player->audio.sl.outputMix};
		SLDataSink dataSink = {&locatorOutputMix, NULL};
		const SLInterfaceID queueIds[] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
		const SLboolean queueRequired[] = {SL_BOOLEAN_TRUE};
		int needResampleSR = NEED_RESAMPLE_NO;
		int slSampleRate = 0;
		int sampleRate = player->av.audioContext->sample_rate;
		switch (sampleRate) {
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
		while (1) {
			int mayRepeat = 1;
			if (needResampleSR == NEED_RESAMPLE_MAY_48000 && sampleRate % 48000 == 0) {
				slSampleRate = SL_SAMPLINGRATE_48;
				player->audio.resampleSampleRate = 48000;
			} else if (needResampleSR == NEED_RESAMPLE_MAY_48000 || needResampleSR == NEED_RESAMPLE_FORCE_44100) {
				slSampleRate = SL_SAMPLINGRATE_44_1;
				player->audio.resampleSampleRate = 44100;
				mayRepeat = 0;
			}
			formatPCM.samplesPerSec = slSampleRate;
			result = (*slEngine)->CreateAudioPlayer(slEngine, &player->audio.sl.player,
					&dataSource, &dataSink, 1, queueIds, queueRequired);
			LOGP("SLES CreateAudioPlayer: result=%d, resampleSampleRate=%d",
					(int) result, (int) player->audio.resampleSampleRate);
			if (result == SL_RESULT_CONTENT_UNSUPPORTED && mayRepeat) {
				if (needResampleSR == NEED_RESAMPLE_NO) {
					needResampleSR = NEED_RESAMPLE_MAY_48000;
				} else if (needResampleSR == NEED_RESAMPLE_MAY_48000) {
					needResampleSR = NEED_RESAMPLE_FORCE_44100;
				}
			} else {
				break;
			}
		}
		if (result != SL_RESULT_SUCCESS) {
			goto HANDLE_SL_INIT_ERROR;
		}
		result = (*player->audio.sl.player)->Realize(player->audio.sl.player, SL_BOOLEAN_FALSE);
		if (result != SL_RESULT_SUCCESS) {
			LOGP("SLES player.Realize: result=%d", (int) result);
			goto HANDLE_SL_INIT_ERROR;
		}
		result = (*player->audio.sl.player)->GetInterface(player->audio.sl.player,
				SL_IID_BUFFERQUEUE, &player->audio.sl.queue);
		if (result != SL_RESULT_SUCCESS) {
			LOGP("SLES player.GetInterface(SL_IID_BUFFERQUEUE): result=%d", (int) result);
			goto HANDLE_SL_INIT_ERROR;
		}
		result = (*player->audio.sl.player)->GetInterface(player->audio.sl.player,
				SL_IID_PLAY, &player->audio.sl.play);
		if (result != SL_RESULT_SUCCESS) {
			LOGP("SLES player.GetInterface(SL_IID_PLAY): result=%d", (int) result);
			goto HANDLE_SL_INIT_ERROR;
		}
		result = (*player->audio.sl.queue)->RegisterCallback(player->audio.sl.queue, audioPlayerCallback, player);
		if (result != SL_RESULT_SUCCESS) {
			LOGP("SLES player.RegisterCallback: result=%d", (int) result);
			goto HANDLE_SL_INIT_ERROR;
		}
		result = (*player->audio.sl.play)->SetPlayState(player->audio.sl.play, SL_PLAYSTATE_PLAYING);
		if (result != SL_RESULT_SUCCESS) {
			LOGP("SLES player.SetPlayState: result=%d", (int) result);
			goto HANDLE_SL_INIT_ERROR;
		}
		success = 1;
		HANDLE_SL_INIT_ERROR:
		if (!success) {
			avcodec_close(player->av.audioContext);
			avcodec_free_context(&player->av.audioContext);
			player->av.audioContext = NULL;
			audioStreamIndex = INDEX_NO_STREAM;
			player->av.audioStreamIndex = INDEX_NO_STREAM;
			audioStream = NULL;
			audioCodec = NULL;
		}
	}
	if (videoStream && pthread_create(&player->video.drawThread, NULL, &performDraw, player) != 0) {
		player->meta.errorCode = ERROR_START_THREAD;
		return;
	}
	if (audioStream && pthread_create(&player->decode.audio.thread, NULL, &performDecodeAudio, player) != 0) {
		player->meta.errorCode = ERROR_START_THREAD;
		return;
	}
	if (videoStream && pthread_create(&player->decode.video.thread, NULL, &performDecodeVideo, player) != 0) {
		player->meta.errorCode = ERROR_START_THREAD;
		return;
	}
	if (pthread_create(&player->decode.packets.thread, NULL, &performDecodePackets, player) != 0) {
		player->meta.errorCode = ERROR_START_THREAD;
		return;
	}
}

void destroy(JNIEnv * env, jlong pointer, jboolean initOnly) {
	Player * player = POINTER_CAST(pointer);
	player->meta.interrupt = 1;
	condBroadcastLocked(&player->file.controlCond, &player->file.controlMutex);
	if (!!initOnly) {
		return;
	}

	blockingQueueInterrupt(&player->audio.packetQueue);
	blockingQueueInterrupt(&player->video.packetQueue);
	blockingQueueInterrupt(&player->audio.bufferQueue);

	condBroadcastLocked(&player->audio.sleepCond, &player->audio.sleepBufferMutex);
	condBroadcastLocked(&player->audio.bufferCond, &player->audio.sleepBufferMutex);
	condBroadcastLocked(&player->video.sleepCond, &player->video.sleepDrawMutex);
	condBroadcastLocked(&player->video.queueCond, &player->video.queueMutex);
	condBroadcastLocked(&player->play.finishCond, &player->play.finishMutex);
	condBroadcastLocked(&player->decode.packets.flowCond, &player->decode.packets.flowMutex);

	pthread_join(player->decode.packets.thread, NULL);
	pthread_mutex_destroy(&player->decode.packets.readMutex);
	pthread_mutex_destroy(&player->decode.packets.flowMutex);
	pthread_mutex_destroy(&player->decode.audio.frameMutex);
	pthread_mutex_destroy(&player->decode.video.frameMutex);
	pthread_mutex_destroy(&player->play.finishMutex);
	pthread_mutex_destroy(&player->audio.sleepBufferMutex);
	pthread_mutex_destroy(&player->video.sleepDrawMutex);
	pthread_mutex_destroy(&player->video.queueMutex);
	pthread_mutex_destroy(&player->file.controlMutex);
	pthread_cond_destroy(&player->decode.packets.flowCond);
	pthread_cond_destroy(&player->play.finishCond);
	pthread_cond_destroy(&player->audio.sleepCond);
	pthread_cond_destroy(&player->audio.bufferCond);
	pthread_cond_destroy(&player->video.sleepCond);
	pthread_cond_destroy(&player->video.queueCond);
	pthread_cond_destroy(&player->file.controlCond);

	blockingQueueDestroy(&player->audio.packetQueue, packetQueueFreeCallback);
	blockingQueueDestroy(&player->video.packetQueue, packetQueueFreeCallback);
	blockingQueueDestroy(&player->audio.bufferQueue, audioBufferQueueFreeCallback);
	if (player->video.bufferQueue) {
		bufferQueueDestroy(player->video.bufferQueue, videoBufferQueueFreeCallback);
		free(player->video.bufferQueue);
		free(player->video.lastBuffer.data);
	}

	if (player->audio.sl.player) {
		(*player->audio.sl.player)->Destroy(player->audio.sl.player);
	}
	if (player->audio.sl.outputMix) {
		(*player->audio.sl.outputMix)->Destroy(player->audio.sl.outputMix);
	}
	if (HAS_STREAM(player, audio)) {
		AVCodecContext * audioContext = GET_CONTEXT(player, audio);
		avcodec_close(audioContext);
		avcodec_free_context(&audioContext);
	}
	if (HAS_STREAM(player, video)) {
		AVCodecContext * videoContext = GET_CONTEXT(player, video);
		avcodec_close(videoContext);
		avcodec_free_context(&videoContext);
	}
	if (player->av.format) {
		AVIOContext * ioContext = player->av.format->pb;
		avformat_close_input(&player->av.format);
		av_free(ioContext->buffer);
		av_free(ioContext);
	}
	if (player->audio.buffer) {
		free(player->audio.buffer);
	}
	releasePlayerSurface(player);
	sparseArrayDestroy(&player->bridge.array, free);
	if (player->bridge.native) {
		(*env)->DeleteGlobalRef(env, player->bridge.native);
	}
	if (player->file.fd > 0) {
		close(player->file.fd);
	}
	free(player);
}

jint getErrorCode(jlong pointer) {
	Player * player = POINTER_CAST(pointer);
	return player->meta.errorCode;
}

void getSummary(JNIEnv * env, jlong pointer, jintArray output) {
	Player * player = POINTER_CAST(pointer);
	jint result[3];
	AVCodecContext * context = GET_CONTEXT(player, video);
	result[0] = context->width;
	result[1] = context->height;
	result[2] = HAS_STREAM(player, audio);
	(*env)->SetIntArrayRegion(env, output, 0, 3, result);
}

jlong getDuration(jlong pointer) {
	Player * player = POINTER_CAST(pointer);
	return max64(player->av.format->duration / 1000, 0);
}

jlong getPosition(jlong pointer) {
	Player * player = POINTER_CAST(pointer);
	return max64(calculatePosition(player, 0), 0);
}

void setPosition(JNIEnv * env, jlong pointer, jlong position) {
	Player * player = POINTER_CAST(pointer);
	if (position >= 0) {
		// Leave the call below even without variable declaration to init a bridge here
		Bridge * bridge = obtainBridge(player, env);
		pthread_mutex_lock(&player->play.finishMutex);
		pthread_mutex_lock(&player->decode.packets.readMutex);
		pthread_mutex_lock(&player->decode.packets.flowMutex);
		pthread_mutex_lock(&player->decode.audio.frameMutex);
		pthread_mutex_lock(&player->decode.video.frameMutex);
		pthread_mutex_lock(&player->audio.sleepBufferMutex);
		pthread_mutex_lock(&player->video.sleepDrawMutex);
		pthread_mutex_lock(&player->video.queueMutex);
		blockingQueueClear(&player->audio.packetQueue, packetQueueFreeCallback);
		blockingQueueClear(&player->video.packetQueue, packetQueueFreeCallback);
		blockingQueueClear(&player->audio.bufferQueue, audioBufferQueueFreeCallback);
		audioBufferQueueFreeCallback(player->audio.buffer);
		if (player->audio.sl.queue) {
			(*player->audio.sl.queue)->Clear(player->audio.sl.queue);
			player->audio.bufferNeedEnqueueAfterDecode = 1;
		}
		player->audio.buffer = NULL;
		if (player->video.bufferQueue) {
			bufferQueueClear(player->video.bufferQueue, videoBufferQueueFreeCallback);
		}
		if (HAS_STREAM(player, audio)) {
			avcodec_flush_buffers(GET_CONTEXT(player, audio));
		}
		if (HAS_STREAM(player, video)) {
			avcodec_flush_buffers(GET_CONTEXT(player, video));
		}
		if (player->meta.seekAnyFrame) {
			int64_t audioPosition = HAS_STREAM(player, audio) ? -1 : position;
			int64_t videoPosition = HAS_STREAM(player, video) ? -1 : position;
			AVPacket packet;
			for (int i = 1; audioPosition == -1 || videoPosition == -1; i++) {
				int64_t seekPosition = max64(position - i * i * 1000, 0);
				int64_t maxPosition = max64(position - (i - 1) * (i - 1) * 1000, 0);
				av_seek_frame(player->av.format, -1, seekPosition * 1000, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);
				while (1) {
					if (av_read_frame(player->av.format, &packet) < 0) {
						break;
					}
					if (packet.pts != AV_NOPTS_VALUE) {
						int64_t * outPosition = NULL;
						if (packet.stream_index == player->av.audioStreamIndex) {
							outPosition = &audioPosition;
						} else if (packet.stream_index == player->av.videoStreamIndex) {
							outPosition = &videoPosition;
						}
						if (outPosition) {
							AVRational timeBase = player->av.format->streams[packet.stream_index]->time_base;
							int64_t timestamp = packet.pts * 1000 * timeBase.num / timeBase.den;
							if (timestamp > maxPosition) {
								av_packet_unref(&packet);
								break;
							}
							if (timestamp > *outPosition) {
								*outPosition = timestamp;
							}
						}
					}
					av_packet_unref(&packet);
				}
				if (seekPosition <= 0) {
					break;
				}
			}
			if (audioPosition == -1) {
				audioPosition = position;
			}
			if (videoPosition == -1) {
				videoPosition = position;
			}
			position = min64(audioPosition, videoPosition);
		}
		av_seek_frame(player->av.format, -1, position * 1000, AVSEEK_FLAG_BACKWARD);
		player->decode.packets.finished = 0;
		player->audio.finished = 0;
		player->video.finished = 0;
		updateAudioPositionSurrogate(player, position, 1);
		player->sync.audioPosition = position;
		player->sync.videoPosition = position;
		player->sync.pausedPosition = position;
		player->sync.audioPositionNotSync = 1;
		player->sync.videoPositionNotSync = 1;
		player->sync.skip.readFrame = 1;
		player->sync.skip.audioWorkFrame = 1;
		player->sync.skip.videoWorkFrame = 1;
		player->sync.skip.drawWorkFrame = 1;
		player->sync.lastDrawTimes[0] = 0;
		player->sync.lastDrawTimes[1] = 0;
		if (HAS_STREAM(player, video)) {
			SEND_MESSAGE(env, player, bridge, BRIDGE_MESSAGE_START_SEEKING);
		}
		pthread_cond_broadcast(&player->play.finishCond);
		pthread_cond_broadcast(&player->decode.packets.flowCond);
		pthread_cond_broadcast(&player->audio.sleepCond);
		pthread_cond_broadcast(&player->audio.bufferCond);
		pthread_cond_broadcast(&player->video.sleepCond);
		pthread_cond_broadcast(&player->video.queueCond);
		pthread_mutex_unlock(&player->video.queueMutex);
		pthread_mutex_unlock(&player->video.sleepDrawMutex);
		pthread_mutex_unlock(&player->audio.sleepBufferMutex);
		pthread_mutex_unlock(&player->decode.video.frameMutex);
		pthread_mutex_unlock(&player->decode.audio.frameMutex);
		pthread_mutex_unlock(&player->decode.packets.flowMutex);
		pthread_mutex_unlock(&player->decode.packets.readMutex);
		pthread_mutex_unlock(&player->play.finishMutex);
	}
}

void setRange(jlong pointer, jlong start, jlong end, jlong total) {
	Player * player = POINTER_CAST(pointer);
	pthread_mutex_lock(&player->file.controlMutex);
	player->file.start = start;
	player->file.end = end;
	player->file.total = total;
	LOG("set range range=[%ld-%ld/%ld]", player->file.start, player->file.end, player->file.total);
	pthread_cond_broadcast(&player->file.controlCond);
	pthread_mutex_unlock(&player->file.controlMutex);
}

void setCancelSeek(jlong pointer, jboolean cancelSeek) {
	Player * player = POINTER_CAST(pointer);
	pthread_mutex_lock(&player->file.controlMutex);
	player->file.cancelSeek = !!cancelSeek;
	pthread_cond_broadcast(&player->file.controlCond);
	pthread_mutex_unlock(&player->file.controlMutex);
}

void setPlaying(jlong pointer, jboolean playing) {
	Player * player = POINTER_CAST(pointer);
	playing = !!playing;
	if (player->play.playing != playing) {
		LOG("switch playing %d", playing);
		pthread_mutex_lock(&player->play.finishMutex);
		if (playing) {
			updateAudioPositionSurrogate(player, player->sync.pausedPosition, 1);
		} else {
			player->sync.pausedPosition = calculatePosition(player, 1);
		}
		player->play.playing = playing;
		pthread_cond_broadcast(&player->play.finishCond);
		pthread_mutex_unlock(&player->play.finishMutex);
		if (HAS_STREAM(player, audio)) {
			pthread_mutex_lock(&player->audio.sleepBufferMutex);
			(*player->audio.sl.play)->SetPlayState(player->audio.sl.play,
					playing ? SL_PLAYSTATE_PLAYING : SL_PLAYSTATE_PAUSED);
			if (playing && player->audio.bufferNeedEnqueueAfterDecode
					&& blockingQueueCount(&player->audio.bufferQueue) > 0) {
				// Queue count checked to free from obligation to handle audio finish flag
				enqueueAudioBuffer(player);
			}
			pthread_mutex_unlock(&player->audio.sleepBufferMutex);
		}
	}
}

void setSurface(JNIEnv * env, jlong pointer, jobject surface) {
	Player * player = POINTER_CAST(pointer);
	pthread_mutex_lock(&player->video.sleepDrawMutex);
	releasePlayerSurface(player);
	setPlayerSurfaceLocked(env, player, surface);
	pthread_mutex_unlock(&player->video.sleepDrawMutex);
}

jintArray getCurrentFrame(JNIEnv * env, jlong pointer, jintArray dimensions) {
	Player * player = POINTER_CAST(pointer);
	pthread_mutex_lock(&player->video.sleepDrawMutex);
	uint8_t * buffer = player->video.lastBuffer.data;
	int sourceWidth = player->video.lastBuffer.width;
	int sourceHeight = player->video.lastBuffer.height;
	int destWidth = sourceWidth;
	int destHeight = sourceHeight;
	int maxDimension = 1000;
	if (destWidth > maxDimension || destHeight > maxDimension) {
		int sampleHorizontal = (destWidth + maxDimension - 1) / maxDimension;
		int sampleVertical = (destHeight + maxDimension - 1) / maxDimension;
		int sample = sampleHorizontal > sampleVertical ? sampleHorizontal : sampleVertical;
		if (sample >= 2) {
			destWidth = (destWidth + sample - 1) / sample;
			destHeight = (destHeight + sample - 1) / sample;
		}
	}
	(*env)->SetIntArrayRegion(env, dimensions, 0, 1, &destWidth);
	(*env)->SetIntArrayRegion(env, dimensions, 1, 1, &destHeight);
	jintArray result = 0;
	int success = 0;
	if (buffer != 0 && destWidth > 0 && destHeight > 0) {
		if (player->video.format != AV_PIX_FMT_RGB565LE && player->video.format != AV_PIX_FMT_YUV420P
				&& player->video.format != AV_PIX_FMT_RGBA) {
			goto RESULT;
		}
		result = (*env)->NewIntArray(env, destWidth * destHeight);
		if (!result) {
			goto RESULT;
		}
		struct SwsContext * scaleContext = sws_getContext(sourceWidth, sourceHeight, player->video.format,
				destWidth, destHeight, AV_PIX_FMT_BGRA, SWS_FAST_BILINEAR, NULL, NULL, NULL);
		if (!scaleContext) {
			goto RESULT;
		}
		uint8_t * newBuffer = (*env)->GetPrimitiveArrayCritical(env, result, NULL);
		if (!newBuffer) {
			goto SWS_FREE_CONTEXT;
		}
		uint8_t * newData[4] = {newBuffer, 0, 0, 0};
		int newLinesize[4] = {4 * destWidth, 0, 0, 0};
		if (player->video.format == AV_PIX_FMT_RGBA) {
			if (player->video.lastBuffer.size < 4 * sourceWidth * sourceHeight) {
				goto RELEASE_PRIMITIVE_ARRAY;
			}
			const uint8_t * const oldData[4] = {buffer, 0, 0, 0};
			int oldLinesize[4] = {4 * sourceWidth, 0, 0, 0};
			sws_scale(scaleContext, oldData, oldLinesize, 0, sourceHeight, newData, newLinesize);
		} else if (player->video.format == AV_PIX_FMT_RGB565LE) {
			if (player->video.lastBuffer.size < 2 * sourceWidth * sourceHeight) {
				goto RELEASE_PRIMITIVE_ARRAY;
			}
			const uint8_t * const oldData[4] = {buffer, 0, 0, 0};
			int oldLinesize[4] = {2 * sourceWidth, 0, 0, 0};
			sws_scale(scaleContext, oldData, oldLinesize, 0, sourceHeight, newData, newLinesize);
		} else if (player->video.format == AV_PIX_FMT_YUV420P) {
			if (player->video.lastBuffer.size < sourceWidth * sourceHeight * 3 / 2) {
				goto RELEASE_PRIMITIVE_ARRAY;
			}
			const uint8_t * const oldData[4] = {buffer, buffer + sourceWidth * sourceHeight +
					sourceWidth * sourceHeight / 4, buffer + sourceWidth * sourceHeight, 0};
			int oldLinesize[4] = {sourceWidth, sourceWidth / 2, sourceWidth / 2, 0};
			sws_scale(scaleContext, oldData, oldLinesize, 0, sourceHeight, newData, newLinesize);
		}
		success = 1;
		RELEASE_PRIMITIVE_ARRAY:
		(*env)->ReleasePrimitiveArrayCritical(env, result, newBuffer, 0);
		SWS_FREE_CONTEXT:
		sws_freeContext(scaleContext);
	}
	RESULT:
	pthread_mutex_unlock(&player->video.sleepDrawMutex);
	if (!success && result) {
		(*env)->DeleteLocalRef(env, result);
		result = 0;
	}
	return result;
}

static jstring newUtfStringSafe(JNIEnv * env, char * string) {
    // Fixes "input is not valid Modified UTF-8" error
    if (string) {
        int length = strlen(string);
        jbyteArray array = (*env)->NewByteArray(env, length);
        (*env)->SetByteArrayRegion(env, array, 0, length, (void *) string);
        jclass class = (*env)->FindClass(env, "java/lang/String");
        jmethodID constructor = (*env)->GetMethodID(env, class, "<init>", "([B)V");
        jstring result = (*env)->NewObject(env, class, constructor, array);
        (*env)->DeleteLocalRef(env, array);
        return result;
    }
    return 0;
}

jobjectArray getMetadata(JNIEnv * env, jlong pointer) {
	char buffer[24];
	Player * player = POINTER_CAST(pointer);
	int entries = av_dict_count(player->av.format->metadata);
	if (HAS_STREAM(player, video)) {
		// Format, width, height, frame rate, pixel format, canvas format, libyuv
		entries += 7;
	}
	if (HAS_STREAM(player, audio)) {
		// Format, channels, sample rate
		entries += 3;
	}
	jobjectArray result = (*env)->NewObjectArray(env, 2 * entries, (*env)->FindClass(env, "java/lang/String"), NULL);
	int index = 0;
	if (HAS_STREAM(player, video)) {
		AVStream * videoStream = GET_STREAM(player, video);
		AVCodecContext * videoContext = GET_CONTEXT(player, video);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "video_format"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env,
				videoContext->codec->long_name));
		sprintf(buffer, "%d", videoContext->width);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "width"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
		sprintf(buffer, "%d", videoContext->height);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "height"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
		sprintf(buffer, "%.3lf", av_q2d(videoStream->r_frame_rate));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "frame_rate"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
		const AVPixFmtDescriptor * pixFmtDesctiptor = av_pix_fmt_desc_get(videoContext->pix_fmt);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "pixel_format"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env,
				pixFmtDesctiptor ? pixFmtDesctiptor->name : "Unknown"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "surface_format"));
		int format = player->video.window ? ANativeWindow_getFormat(player->video.window) : -1;
		switch (format) {
			case WINDOW_FORMAT_RGBA_8888: {
				(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "RGBA 8888"));
				break;
			}
			case WINDOW_FORMAT_RGBX_8888: {
				(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "RGBX 8888"));
				break;
			}
			case WINDOW_FORMAT_RGB_565: {
				(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "RGB 565"));
				break;
			}
			case WINDOW_FORMAT_YV12: {
				(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "YV12"));
				break;
			}
			default: {
				(*env)->SetObjectArrayElement(env, result, index++, NULL);
				break;
			}
		}
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "frame_conversion"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env,
				player->video.useLibyuv == 1 ? "libyuv" : player->video.useLibyuv == 0 ? "libswscale" : "Unknown"));
	}
	if (HAS_STREAM(player, audio)) {
		AVCodecContext * audioContext = GET_CONTEXT(player, audio);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "audio_format"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env,
				audioContext->codec->long_name));
		sprintf(buffer, "%d", audioContext->channels);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "channels"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
		sprintf(buffer, "%d", audioContext->sample_rate);
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, "sample_rate"));
		(*env)->SetObjectArrayElement(env, result, index++, (*env)->NewStringUTF(env, buffer));
	}
	AVDictionaryEntry * entry = NULL;
	while ((entry = av_dict_get(player->av.format->metadata, "", entry, AV_DICT_IGNORE_SUFFIX))) {
		(*env)->SetObjectArrayElement(env, result, index++, newUtfStringSafe(env, entry->key));
		(*env)->SetObjectArrayElement(env, result, index++, newUtfStringSafe(env, entry->value));
	}
	return result;
}

void initLibs(JavaVM * javaVM) {
	loadJavaVM = javaVM;
	SLObjectItf engineObject;
	slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
	(*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
	(*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &slEngine);
}
