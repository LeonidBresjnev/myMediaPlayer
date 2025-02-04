#pragma once

#include <android/log.h>

#ifndef NODEBUG
#define LOGD(args...) \
__android_log_print(android_LogPriority::ANDROID_LOG_DEBUG, "MyMediaPlayer", args)
#else
#define LOGD(args...)
#endif