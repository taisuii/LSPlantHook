#pragma once

#include <android/log.h>

// 去特征：默认 log tag 由 "LSPlant" 改为中性，避免 logcat / .so rodata 暴露引擎指纹。
#ifndef LOG_TAG
#define LOG_TAG "nativert"
#endif

#ifdef LOG_DISABLED
#define LOGD(...)
#define LOGV(...)
#define LOGI(...)
#define LOGW(...)
#define LOGE(...)
#else
#ifndef NDEBUG
#define LOGD(fmt, ...)                                                                             \
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG,                                                \
                        "%s:%d"                                                                    \
                        ": " fmt,                                                                  \
                        __FILE_NAME__, __LINE__ __VA_OPT__(, ) __VA_ARGS__)
#define LOGV(fmt, ...)                                                                             \
    __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG,                                              \
                        "%s:%d"                                                                    \
                        ": " fmt,                                                                  \
                        __FILE_NAME__, __LINE__ __VA_OPT__(, ) __VA_ARGS__)
#else
#define LOGD(...)
#define LOGV(...)
#endif
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, __VA_ARGS__)
#define PLOGE(fmt, args...) LOGE(fmt " failed with %d: %s", ##args, errno, strerror(errno))
#endif
