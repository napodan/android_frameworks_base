//
// Copyright 2010 The Android Open Source Project
//
// A looper implementation based on epoll().
//
#define LOG_TAG "Looper"

//#define LOG_NDEBUG 0

// Debugs poll and wake interactions.
#define DEBUG_POLL_AND_WAKE 0

// Debugs callback registration and invocation.
#define DEBUG_CALLBACKS 0

#include <cutils/log.h>
#include <utils/Looper.h>
#include <utils/Timers.h>

#include <unistd.h>
#include <fcntl.h>


namespace android {

#ifdef LOOPER_USES_EPOLL
// Hint for number of file descriptors to be associated with the epoll instance.
static const int EPOLL_SIZE_HINT = 8;

// Maximum number of file descriptors for which to retrieve poll events each iteration.
static const int EPOLL_MAX_EVENTS = 16;
#endif

static pthread_once_t gTLSOnce = PTHREAD_ONCE_INIT;
static pthread_key_t gTLSKey = 0;

Looper::Looper(bool allowNonCallbacks) :
        mAllowNonCallbacks(allowNonCallbacks),
        mResponseIndex(0) {
    int wakeFds[2];
    int result = pipe(wakeFds);
    LOG_ALWAYS_FATAL_IF(result != 0, "Could not create wake pipe.  errno=%d", errno);

    mWakeReadPipeFd = wakeFds[0];
    mWakeWritePipeFd = wakeFds[1];

    result = fcntl(mWakeReadPipeFd, F_SETFL, O_NONBLOCK);
    LOG_ALWAYS_FATAL_IF(result != 0, "Could not make wake read pipe non-blocking.  errno=%d",
            errno);

    result = fcntl(mWakeWritePipeFd, F_SETFL, O_NONBLOCK);
    LOG_ALWAYS_FATAL_IF(result != 0, "Could not make wake write pipe non-blocking.  errno=%d",
            errno);

#ifdef LOOPER_USES_EPOLL
    // Allocate the epoll instance and register the wake pipe.
    mEpollFd = epoll_create(EPOLL_SIZE_HINT);
    LOG_ALWAYS_FATAL_IF(mEpollFd < 0, "Could not create epoll instance.  errno=%d", errno);

    struct epoll_event eventItem;
    memset(& eventItem, 0, sizeof(epoll_event)); // zero out unused members of data field union
    eventItem.events = EPOLLIN;
    eventItem.data.fd = mWakeReadPipeFd;
    result = epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mWakeReadPipeFd, & eventItem);
    LOG_ALWAYS_FATAL_IF(result != 0, "Could not add wake read pipe to epoll instance.  errno=%d",
            errno);
#else
    // Add the wake pipe to the head of the request list with a null callback.
    struct pollfd requestedFd;
    requestedFd.fd = mWakeReadPipeFd;
    requestedFd.events = POLLIN;
    mRequestedFds.push(requestedFd);

    Request request;
    request.fd = mWakeReadPipeFd;
    request.callback = NULL;
    request.ident = 0;
    request.data = NULL;
    mRequests.push(request);

    mPolling = false;
    mWaiters = 0;
#endif

#ifdef LOOPER_STATISTICS
    mPendingWakeTime = -1;
    mPendingWakeCount = 0;
    mSampledWakeCycles = 0;
    mSampledWakeCountSum = 0;
    mSampledWakeLatencySum = 0;

    mSampledPolls = 0;
    mSampledZeroPollCount = 0;
    mSampledZeroPollLatencySum = 0;
    mSampledTimeoutPollCount = 0;
    mSampledTimeoutPollLatencySum = 0;
#endif
}

Looper::~Looper() {
    close(mWakeReadPipeFd);
    close(mWakeWritePipeFd);
#ifdef LOOPER_USES_EPOLL
    close(mEpollFd);
#endif
}

void Looper::initTLSKey() {
    int result = pthread_key_create(& gTLSKey, threadDestructor);
    LOG_ALWAYS_FATAL_IF(result != 0, "Could not allocate TLS key.");
}

void Looper::threadDestructor(void *st) {
    Looper* const self = static_cast<Looper*>(st);
    if (self != NULL) {
        self->decStrong((void*)threadDestructor);
    }
}

void Looper::setForThread(const sp<Looper>& looper) {
    sp<Looper> old = getForThread(); // also has side-effect of initializing TLS

    if (looper != NULL) {
        looper->incStrong((void*)threadDestructor);
    }

    pthread_setspecific(gTLSKey, looper.get());

    if (old != NULL) {
        old->decStrong((void*)threadDestructor);
    }
}

sp<Looper> Looper::getForThread() {
    int result = pthread_once(& gTLSOnce, initTLSKey);
    LOG_ALWAYS_FATAL_IF(result != 0, "pthread_once failed");

    return (Looper*)pthread_getspecific(gTLSKey);
}

sp<Looper> Looper::prepare(int opts) {
    bool allowNonCallbacks = opts & ALOOPER_PREPARE_ALLOW_NON_CALLBACKS;
    sp<Looper> looper = Looper::getForThread();
    if (looper == NULL) {
        looper = new Looper(allowNonCallbacks);
        Looper::setForThread(looper);
    }
    if (looper->getAllowNonCallbacks() != allowNonCallbacks) {
        ALOGW("Looper already prepared for this thread with a different value for the "
                "ALOOPER_PREPARE_ALLOW_NON_CALLBACKS option.");
    }
    return looper;
}

bool Looper::getAllowNonCallbacks() const {
    return mAllowNonCallbacks;
}

int Looper::pollOnce(int timeoutMillis, int* outFd, int* outEvents, void** outData) {
    int result = 0;
    for (;;) {
        while (mResponseIndex < mResponses.size()) {
            const Response& response = mResponses.itemAt(mResponseIndex++);
            if (! response.request.callback) {
#if DEBUG_POLL_AND_WAKE
                ALOGD("%p ~ pollOnce - returning signalled identifier %d: "
                        "fd=%d, events=0x%x, data=%p", this,
                        response.request.ident, response.request.fd,
                        response.events, response.request.data);
#endif
                if (outFd != NULL) *outFd = response.request.fd;
                if (outEvents != NULL) *outEvents = response.events;
                if (outData != NULL) *outData = response.request.data;
                return response.request.ident;
            }
        }

        if (result != 0) {
#if DEBUG_POLL_AND_WAKE
            ALOGD("%p ~ pollOnce - returning result %d", this, result);
#endif
            if (outFd != NULL) *outFd = 0;
            if (outEvents != NULL) *outEvents = NULL;
            if (outData != NULL) *outData = NULL;
            return result;
        }

        result = pollInner(timeoutMillis);
    }
}

int Looper::pollInner(int timeoutMillis) {
#if DEBUG_POLL_AND_WAKE
    ALOGD("%p ~ pollOnce - waiting: timeoutMillis=%d", this, timeoutMillis);
#endif

    int result = ALOOPER_POLL_WAKE;
    mResponses.clear();
    mResponseIndex = 0;

#ifdef LOOPER_STATISTICS
    nsecs_t pollStartTime = systemTime(SYSTEM_TIME_MONOTONIC);
#endif

#ifdef LOOPER_USES_EPOLL
    struct epoll_event eventItems[EPOLL_MAX_EVENTS];
    int eventCount = epoll_wait(mEpollFd, eventItems, EPOLL_MAX_EVENTS, timeoutMillis);
    bool acquiredLock = false;
#else
    // Wait for wakeAndLock() waiters to run then set mPolling to true.
    mLock.lock();
    while (mWaiters != 0) {
        mResume.wait(mLock);
    }
    mPolling = true;
    mLock.unlock();

    size_t requestedCount = mRequestedFds.size();
    int eventCount = poll(mRequestedFds.editArray(), requestedCount, timeoutMillis);
#endif

    if (eventCount < 0) {
        if (errno == EINTR) {
            goto Done;
        }

        ALOGW("Poll failed with an unexpected error, errno=%d", errno);
        result = ALOOPER_POLL_ERROR;
        goto Done;
    }

    if (eventCount == 0) {
#if DEBUG_POLL_AND_WAKE
        ALOGD("%p ~ pollOnce - timeout", this);
#endif
        result = ALOOPER_POLL_TIMEOUT;
        goto Done;
    }

#if DEBUG_POLL_AND_WAKE
    ALOGD("%p ~ pollOnce - handling events from %d fds", this, eventCount);
#endif

#ifdef LOOPER_USES_EPOLL
    for (int i = 0; i < eventCount; i++) {
        int fd = eventItems[i].data.fd;
        uint32_t epollEvents = eventItems[i].events;
        if (fd == mWakeReadPipeFd) {
            if (epollEvents & EPOLLIN) {
                awoken();
            } else {
                ALOGW("Ignoring unexpected epoll events 0x%x on wake read pipe.", epollEvents);
            }
        } else {
            if (! acquiredLock) {
                mLock.lock();
                acquiredLock = true;
            }

            ssize_t requestIndex = mRequests.indexOfKey(fd);
            if (requestIndex >= 0) {
                int events = 0;
                if (epollEvents & EPOLLIN) events |= ALOOPER_EVENT_INPUT;
                if (epollEvents & EPOLLOUT) events |= ALOOPER_EVENT_OUTPUT;
                if (epollEvents & EPOLLERR) events |= ALOOPER_EVENT_ERROR;
                if (epollEvents & EPOLLHUP) events |= ALOOPER_EVENT_HANGUP;
                pushResponse(events, mRequests.valueAt(requestIndex));
            } else {
                ALOGW("Ignoring unexpected epoll events 0x%x on fd %d that is "
                        "no longer registered.", epollEvents, fd);
            }
        }
    }
    if (acquiredLock) {
        mLock.unlock();
    }
Done: ;
#else
    for (size_t i = 0; i < requestedCount; i++) {
        const struct pollfd& requestedFd = mRequestedFds.itemAt(i);

        short pollEvents = requestedFd.revents;
        if (pollEvents) {
            if (requestedFd.fd == mWakeReadPipeFd) {
                if (pollEvents & POLLIN) {
                    awoken();
                } else {
                    ALOGW("Ignoring unexpected poll events 0x%x on wake read pipe.", pollEvents);
                }
            } else {
                int events = 0;
                if (pollEvents & POLLIN) events |= ALOOPER_EVENT_INPUT;
                if (pollEvents & POLLOUT) events |= ALOOPER_EVENT_OUTPUT;
                if (pollEvents & POLLERR) events |= ALOOPER_EVENT_ERROR;
                if (pollEvents & POLLHUP) events |= ALOOPER_EVENT_HANGUP;
                if (pollEvents & POLLNVAL) events |= ALOOPER_EVENT_INVALID;
                pushResponse(events, mRequests.itemAt(i));
            }
            if (--eventCount == 0) {
                break;
            }
        }
    }

Done:
    // Set mPolling to false and wake up the wakeAndLock() waiters.
    mLock.lock();
    mPolling = false;
    if (mWaiters != 0) {
        mAwake.broadcast();
    }
    mLock.unlock();
#endif

#ifdef LOOPER_STATISTICS
    nsecs_t pollEndTime = systemTime(SYSTEM_TIME_MONOTONIC);
    mSampledPolls += 1;
    if (timeoutMillis == 0) {
        mSampledZeroPollCount += 1;
        mSampledZeroPollLatencySum += pollEndTime - pollStartTime;
    } else if (timeoutMillis > 0 && result == ALOOPER_POLL_TIMEOUT) {
        mSampledTimeoutPollCount += 1;
        mSampledTimeoutPollLatencySum += pollEndTime - pollStartTime
                - milliseconds_to_nanoseconds(timeoutMillis);
    }
    if (mSampledPolls == SAMPLED_POLLS_TO_AGGREGATE) {
        ALOGD("%p ~ poll latency statistics: %0.3fms zero timeout, %0.3fms non-zero timeout", this,
                0.000001f * float(mSampledZeroPollLatencySum) / mSampledZeroPollCount,
                0.000001f * float(mSampledTimeoutPollLatencySum) / mSampledTimeoutPollCount);
        mSampledPolls = 0;
        mSampledZeroPollCount = 0;
        mSampledZeroPollLatencySum = 0;
        mSampledTimeoutPollCount = 0;
        mSampledTimeoutPollLatencySum = 0;
    }
#endif

    for (size_t i = 0; i < mResponses.size(); i++) {
        const Response& response = mResponses.itemAt(i);
        if (response.request.callback) {
#if DEBUG_POLL_AND_WAKE || DEBUG_CALLBACKS
            ALOGD("%p ~ pollOnce - invoking callback: fd=%d, events=0x%x, data=%p", this,
                    response.request.fd, response.events, response.request.data);
#endif
            int callbackResult = response.request.callback(
                    response.request.fd, response.events, response.request.data);
            if (callbackResult == 0) {
                removeFd(response.request.fd);
            }

            result = ALOOPER_POLL_CALLBACK;
        }
    }
    return result;
}

int Looper::pollAll(int timeoutMillis, int* outFd, int* outEvents, void** outData) {
    if (timeoutMillis <= 0) {
        int result;
        do {
            result = pollOnce(timeoutMillis, outFd, outEvents, outData);
        } while (result == ALOOPER_POLL_CALLBACK);
        return result;
    } else {
        nsecs_t endTime = systemTime(SYSTEM_TIME_MONOTONIC)
                + milliseconds_to_nanoseconds(timeoutMillis);

        for (;;) {
            int result = pollOnce(timeoutMillis, outFd, outEvents, outData);
            if (result != ALOOPER_POLL_CALLBACK) {
                return result;
            }

            nsecs_t timeoutNanos = endTime - systemTime(SYSTEM_TIME_MONOTONIC);
            if (timeoutNanos <= 0) {
                return ALOOPER_POLL_TIMEOUT;
            }

            timeoutMillis = int(nanoseconds_to_milliseconds(timeoutNanos + 999999LL));
        }
    }
}

void Looper::wake() {
#if DEBUG_POLL_AND_WAKE
    ALOGD("%p ~ wake", this);
#endif

#ifdef LOOPER_STATISTICS
    // FIXME: Possible race with awoken() but this code is for testing only and is rarely enabled.
    if (mPendingWakeCount++ == 0) {
        mPendingWakeTime = systemTime(SYSTEM_TIME_MONOTONIC);
    }
#endif

    ssize_t nWrite;
    do {
        nWrite = write(mWakeWritePipeFd, "W", 1);
    } while (nWrite == -1 && errno == EINTR);

    if (nWrite != 1) {
        if (errno != EAGAIN) {
            ALOGW("Could not write wake signal, errno=%d", errno);
        }
    }
}

void Looper::awoken() {
#if DEBUG_POLL_AND_WAKE
    ALOGD("%p ~ awoken", this);
#endif

#ifdef LOOPER_STATISTICS
    if (mPendingWakeCount == 0) {
        ALOGD("%p ~ awoken: spurious!", this);
    } else {
        mSampledWakeCycles += 1;
        mSampledWakeCountSum += mPendingWakeCount;
        mSampledWakeLatencySum += systemTime(SYSTEM_TIME_MONOTONIC) - mPendingWakeTime;
        mPendingWakeCount = 0;
        mPendingWakeTime = -1;
        if (mSampledWakeCycles == SAMPLED_WAKE_CYCLES_TO_AGGREGATE) {
            ALOGD("%p ~ wake statistics: %0.3fms wake latency, %0.3f wakes per cycle", this,
                    0.000001f * float(mSampledWakeLatencySum) / mSampledWakeCycles,
                    float(mSampledWakeCountSum) / mSampledWakeCycles);
            mSampledWakeCycles = 0;
            mSampledWakeCountSum = 0;
            mSampledWakeLatencySum = 0;
        }
    }
#endif

    char buffer[16];
    ssize_t nRead;
    do {
        nRead = read(mWakeReadPipeFd, buffer, sizeof(buffer));
    } while ((nRead == -1 && errno == EINTR) || nRead == sizeof(buffer));
}

void Looper::pushResponse(int events, const Request& request) {
    Response response;
    response.events = events;
    response.request = request;
    mResponses.push(response);
}

int Looper::addFd(int fd, int ident, int events, ALooper_callbackFunc callback, void* data) {
#if DEBUG_CALLBACKS
    ALOGD("%p ~ addFd - fd=%d, ident=%d, events=0x%x, callback=%p, data=%p", this, fd, ident,
            events, callback, data);
#endif

    if (! callback) {
        if (! mAllowNonCallbacks) {
            ALOGE("Invalid attempt to set NULL callback but not allowed for this looper.");
            return -1;
        }

        if (ident < 0) {
            ALOGE("Invalid attempt to set NULL callback with ident <= 0.");
            return -1;
        }
    }

#ifdef LOOPER_USES_EPOLL
    int epollEvents = 0;
    if (events & ALOOPER_EVENT_INPUT) epollEvents |= EPOLLIN;
    if (events & ALOOPER_EVENT_OUTPUT) epollEvents |= EPOLLOUT;

    { // acquire lock
        AutoMutex _l(mLock);

        Request request;
        request.fd = fd;
        request.ident = ident;
        request.callback = callback;
        request.data = data;

        struct epoll_event eventItem;
        memset(& eventItem, 0, sizeof(epoll_event)); // zero out unused members of data field union
        eventItem.events = epollEvents;
        eventItem.data.fd = fd;

        ssize_t requestIndex = mRequests.indexOfKey(fd);
        if (requestIndex < 0) {
            int epollResult = epoll_ctl(mEpollFd, EPOLL_CTL_ADD, fd, & eventItem);
            if (epollResult < 0) {
                ALOGE("Error adding epoll events for fd %d, errno=%d", fd, errno);
                return -1;
            }
            mRequests.add(fd, request);
        } else {
            int epollResult = epoll_ctl(mEpollFd, EPOLL_CTL_MOD, fd, & eventItem);
            if (epollResult < 0) {
                ALOGE("Error modifying epoll events for fd %d, errno=%d", fd, errno);
                return -1;
            }
            mRequests.replaceValueAt(requestIndex, request);
        }
    } // release lock
#else
    int pollEvents = 0;
    if (events & ALOOPER_EVENT_INPUT) pollEvents |= POLLIN;
    if (events & ALOOPER_EVENT_OUTPUT) pollEvents |= POLLOUT;

    wakeAndLock(); // acquire lock

    struct pollfd requestedFd;
    requestedFd.fd = fd;
    requestedFd.events = pollEvents;

    Request request;
    request.fd = fd;
    request.ident = ident;
    request.callback = callback;
    request.data = data;
    ssize_t index = getRequestIndexLocked(fd);
    if (index < 0) {
        mRequestedFds.push(requestedFd);
        mRequests.push(request);
    } else {
        mRequestedFds.replaceAt(requestedFd, size_t(index));
        mRequests.replaceAt(request, size_t(index));
    }

    mLock.unlock(); // release lock
#endif
    return 1;
}

int Looper::removeFd(int fd) {
#if DEBUG_CALLBACKS
    ALOGD("%p ~ removeFd - fd=%d", this, fd);
#endif

#ifdef LOOPER_USES_EPOLL
    { // acquire lock
        AutoMutex _l(mLock);
        ssize_t requestIndex = mRequests.indexOfKey(fd);
        if (requestIndex < 0) {
            return 0;
        }

        int epollResult = epoll_ctl(mEpollFd, EPOLL_CTL_DEL, fd, NULL);
        if (epollResult < 0) {
            ALOGE("Error removing epoll events for fd %d, errno=%d", fd, errno);
            return -1;
        }

        mRequests.removeItemsAt(requestIndex);
    } // release lock
    return 1;
#else
    wakeAndLock(); // acquire lock

    ssize_t index = getRequestIndexLocked(fd);
    if (index >= 0) {
        mRequestedFds.removeAt(size_t(index));
        mRequests.removeAt(size_t(index));
    }

    mLock.unlock(); // release lock
    return index >= 0;
#endif
}

#ifndef LOOPER_USES_EPOLL
ssize_t Looper::getRequestIndexLocked(int fd) {
    size_t requestCount = mRequestedFds.size();

    for (size_t i = 0; i < requestCount; i++) {
        if (mRequestedFds.itemAt(i).fd == fd) {
            return i;
        }
    }

    return -1;
}

void Looper::wakeAndLock() {
    mLock.lock();

    mWaiters += 1;
    while (mPolling) {
        wake();
        mAwake.wait(mLock);
    }

    mWaiters -= 1;
    if (mWaiters == 0) {
        mResume.signal();
    }
}
#endif

} // namespace android
