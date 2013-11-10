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

#ifndef _MTP_CLIENT_H
#define _MTP_CLIENT_H

#include "MtpRequestPacket.h"
#include "MtpDataPacket.h"
#include "MtpResponsePacket.h"
#include "MtpTypes.h"

namespace android {

class MtpDeviceInfo;
class MtpObjectInfo;
class MtpStorageInfo;

class MtpClient {
private:
    struct usb_endpoint*    mEndpointIn;
    struct usb_endpoint*    mEndpointOut;
    struct usb_endpoint*    mEndpointIntr;

    // current session ID
    MtpSessionID            mSessionID;
    // current transaction ID
    MtpTransactionID        mTransactionID;

    MtpRequestPacket        mRequest;
    MtpDataPacket           mData;
    MtpResponsePacket       mResponse;

public:
                            MtpClient(struct usb_endpoint *ep_in, struct usb_endpoint *ep_out,
                                    struct usb_endpoint *ep_intr);
    virtual                 ~MtpClient();

    bool                    openSession();
    bool                    closeSession();

    MtpDeviceInfo*          getDeviceInfo();
    MtpStorageIDList*       getStorageIDs();
    MtpStorageInfo*         getStorageInfo(MtpStorageID storageID);
    MtpObjectHandleList*    getObjectHandles(MtpStorageID storageID, MtpObjectFormat format, MtpObjectHandle parent);
    MtpObjectInfo*          getObjectInfo(MtpObjectHandle handle);

private:
    bool                    sendRequest(MtpOperationCode operation);
    bool                    sendData(MtpOperationCode operation);
    bool                    readData();
    MtpResponseCode         readResponse();

};

}; // namespace android

#endif // _MTP_CLIENT_H