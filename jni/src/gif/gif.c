#include "gif.h"

#include <android/bitmap.h>
#include <gif_lib.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <unistd.h>

#define POINTER_CAST(addr) (void *) (long) addr

typedef struct Decoder Decoder;
typedef struct ImageData ImageData;

struct Decoder {
	int errorCode;
	int64_t startTime;
	GifFileType * file;
	ImageData * datas;
	int duration;
	int lastIndex;

	int hasPrevious;
	int * previousColors;
};

struct ImageData {
	int startTime;
	int transparentIndex;
	int disposalMethod;
};

#define D_GIF_ERR_CUSTOM 1000

jlong init(JNIEnv * env, jstring fileName) {
	Decoder * decoder = malloc(sizeof(Decoder));
	memset(decoder, 0, sizeof(Decoder));
	decoder->lastIndex = -1;
	int status = D_GIF_SUCCEEDED;
	const char * fileNameString = (*env)->GetStringUTFChars(env, fileName, 0);
	GifFileType * file = DGifOpenFileName(fileNameString, &status);
	(*env)->ReleaseStringUTFChars(env, fileName, fileNameString);
	decoder->file = file;
	if (status == D_GIF_SUCCEEDED) {
		status = DGifSlurp(file);
		if (status != GIF_OK) {
			status = file->Error;
			if (status == D_GIF_SUCCEEDED) {
				status = D_GIF_ERR_CUSTOM;
			}
		} else if (file->ImageCount == 0) {
			status = D_GIF_ERR_CUSTOM;
		} else {
			status = D_GIF_SUCCEEDED;
		}
	}
	if (status == D_GIF_SUCCEEDED) {
		int when = 0;
		decoder->datas = malloc(sizeof(ImageData) * file->ImageCount);
		for (int i = 0; i < file->ImageCount; i++) {
			SavedImage * image = &file->SavedImages[i];
			ImageData * data = &decoder->datas[i];
			data->transparentIndex = -1;
			data->startTime = when;
			data->disposalMethod = 0;
			for (int j = 0; j < image->ExtensionBlockCount; j++) {
				ExtensionBlock * extension = &image->ExtensionBlocks[j];
				if (extension->Function == GRAPHICS_EXT_FUNC_CODE && extension->ByteCount == 4) {
					int flags = extension->Bytes[0] & 0xff;
					data->disposalMethod = (flags >> 2) & 7;
					if (flags & 1) {
						data->transparentIndex = extension->Bytes[3] & 0xff;
					}
					int delay = *(uint16_t *) (extension->Bytes + 1);
					delay = delay >= 2 ? delay * 10 : 100; // Like in Firefox or Chrome
					when += delay;
					break;
				}
			}
		}
		decoder->duration = when;
		if (!file->SColorMap) {
			file->SColorMap = GifMakeMapObject(256, NULL);
			for (int i = 0; i < 256; i++) {
				file->SColorMap->Colors[i].Red = i;
				file->SColorMap->Colors[i].Green = i;
				file->SColorMap->Colors[i].Blue = i;
			}
		}
	}
	decoder->errorCode = status;
	return (jlong) (long) decoder;
}

void destroy(jlong pointer) {
	Decoder * decoder = POINTER_CAST(pointer);
	int error;
	if (decoder->file) {
		DGifCloseFile(decoder->file, &error);
	}
	if (decoder->datas) {
		free(decoder->datas);
	}
	if (decoder->previousColors) {
		free(decoder->previousColors);
	}
	free(decoder);
}

jint getErrorCode(jlong pointer) {
	Decoder * decoder = POINTER_CAST(pointer);
	return decoder->errorCode;
}

void getSummary(JNIEnv * env, jlong pointer, jintArray output) {
	Decoder * decoder = POINTER_CAST(pointer);
	jint result[2];
	result[0] = decoder->file->SWidth;
	result[1] = decoder->file->SHeight;
	(*env)->SetIntArrayRegion(env, output, 0, 2, result);
}

static int64_t getTime() {
	struct timeval now;
	gettimeofday(&now, NULL);
	return (int64_t) now.tv_sec * 1000 + now.tv_usec / 1000;
}

static void drawImage(Decoder * decoder, int index, int * colors) {
	int width = decoder->file->SWidth;
	int height = decoder->file->SHeight;
	if (index > 0) {
		ImageData * data = &decoder->datas[index - 1];
		if (data->disposalMethod == 2) {
			SavedImage * image = &decoder->file->SavedImages[index - 1];
			int iwidth = image->ImageDesc.Width;
			int iheight = image->ImageDesc.Height;
			int ileft = image->ImageDesc.Left;
			int itop = image->ImageDesc.Top;
			for (int y = 0; y < iheight && itop + y < height; y++) {
				memset(colors + width * (itop + y) + ileft, 0, 4 * (ileft + iwidth <= width ? iwidth : width - ileft));
			}
		} else if (data->disposalMethod == 3) {
			memcpy(colors, decoder->previousColors, 4 * width * height);
		}
	}
	ImageData * data = &decoder->datas[index];
	SavedImage * image = &decoder->file->SavedImages[index];
	if (data->disposalMethod == 3) {
		if (!decoder->hasPrevious) {
			if (!decoder->previousColors) {
				decoder->previousColors = malloc(4 * width * height);
			}
			memcpy(decoder->previousColors, colors, 4 * width * height);
			decoder->hasPrevious = 1;
		}
	} else {
		decoder->hasPrevious = 0;
	}
	ColorMapObject * colorMap = image->ImageDesc.ColorMap;
	if (!colorMap) {
		colorMap = decoder->file->SColorMap;
	}
	int colorCount = colorMap->ColorCount;
	GifColorType * colorTypes = colorMap->Colors;
	uint8_t * source = image->RasterBits;
	int iwidth = image->ImageDesc.Width;
	int iheight = image->ImageDesc.Height;
	int ileft = image->ImageDesc.Left;
	int itop = image->ImageDesc.Top;
	for (int y = 0; y < iheight && itop + y < height; y++) {
		for (int x = 0; x < iwidth && ileft + x < width; x++) {
			int colorIndex = source[y * iwidth + x];
			if (colorIndex >= 0 && colorIndex < colorCount && colorIndex != data->transparentIndex) {
				int red = colorTypes[colorIndex].Red & 0xff;
				int green = colorTypes[colorIndex].Green & 0xff;
				int blue = colorTypes[colorIndex].Blue & 0xff;
				colors[(itop + y) * width + ileft + x] = 0xff000000 | (blue << 16) | (green << 8) | red;
			}
		}
	}
}

jint draw(JNIEnv * env, jlong pointer, jobject bitmap) {
	Decoder * decoder = POINTER_CAST(pointer);
	int position = 0;
	if (decoder->duration > 0) {
		int64_t time = getTime();
		if (decoder->startTime == 0) {
			decoder->startTime = time;
		}
		position = (int) ((time - decoder->startTime) % decoder->duration);
	}
	int index;
	int delay = -1;
	int count = decoder->file->ImageCount;
	for (int i = 0; i < count; i++) {
		ImageData * nextData = &decoder->datas[i];
		if (position >= nextData->startTime) {
			index = i;
		} else {
			break;
		}
	}
	if (count > 1) {
		delay = (index + 1 < count ? decoder->datas[index + 1].startTime : decoder->duration) - position;
	}
	if (decoder->lastIndex != index) {
		int * colors = 0;
		AndroidBitmap_lockPixels(env, bitmap, (void **) &colors);
		if (colors) {
			if (index > decoder->lastIndex) {
				for (int i = decoder->lastIndex + 1; i <= index; i++) {
					drawImage(decoder, i, colors);
				}
			} else {
				decoder->hasPrevious = 0;
				memset(colors, 0, 4 * decoder->file->SWidth * decoder->file->SHeight);
				for (int i = 0; i <= index; i++) {
					drawImage(decoder, i, colors);
				}
			}
			AndroidBitmap_unlockPixels(env, bitmap);
			decoder->lastIndex = index;
		}
	}
	return delay;
}
