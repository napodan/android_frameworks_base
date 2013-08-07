LOCAL_PATH:= $(call my-dir)

#
# libcameraservice
#

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=               \
    CameraService.cpp

LOCAL_SHARED_LIBRARIES:= \
    libui \
    liblog \
    libutils \
    libbinder \
    libcutils \
    libmedia \
    libcamera_client \
    libsurfaceflinger_client

LOCAL_CFLAGS += -Wall -Wextra

LOCAL_LDFLAGS += -lcamera

LOCAL_MODULE:= libcameraservice

include $(BUILD_SHARED_LIBRARY)
