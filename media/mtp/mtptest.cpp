/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "mtp_usb"
#include "cutils/log.h"

#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <fcntl.h>
#include <sys/ioctl.h>

#include "MtpServer.h"
#include "MtpStorage.h"
#include "f_mtp.h"

using namespace android;

static void enable_usb_function(const char* name, bool enable) {
    char    path[PATH_MAX];

    snprintf(path, sizeof(path), "/sys/class/usb_composite/%s/enable", name);
    int fd = open(path, O_RDWR);
    if (fd < 0) {
        fprintf(stderr, "could not open %s in enable_usb_function\n", path);
        exit(1);
    }
    write(fd, enable ? "1" : "0", 2);
    close(fd);
}

int main(int argc, char* argv[]) {
    bool usePTP = false;

    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "-p"))
            usePTP = true;
    }

    int fd = open("/dev/mtp_usb", O_RDWR);
    printf("open returned %d\n", fd);
    if (fd < 0) {
        fprintf(stderr, "could not open MTP driver\n");
        return -1;
    }

    if (usePTP) {
        // set driver mode to PTP
        int ret = ioctl(fd, MTP_SET_INTERFACE_MODE, MTP_INTERFACE_MODE_PTP);
        if (ret) {
            fprintf(stderr, "MTP_SET_INTERFACE_MODE failed\n");
            return -1;
        }
    }

    // disable UMS and enable MTP USB functions
    enable_usb_function("usb_mass_storage", false);
    enable_usb_function("mtp", true);

    MtpServer   server(fd, "/data/data/mtp/mtp.db");
    server.addStorage("/sdcard");
    server.scanStorage();
    server.run();

    close(fd);
    return 0;
}

