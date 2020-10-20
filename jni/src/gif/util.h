#ifndef GIF_UTIL_H
#define GIF_UTIL_H

#define UNUSED __attribute__((unused))

#ifdef DEBUG
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_DEBUG, "Dashchan", __VA_ARGS__)
#else
#define LOG(...)
#endif

#endif // GIF_UTIL_H
