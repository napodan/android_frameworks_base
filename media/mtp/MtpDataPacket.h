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

#ifndef _MTP_DATA_PACKET_H
#define _MTP_DATA_PACKET_H

#include "MtpPacket.h"
#include "mtp.h"

class MtpDataPacket : public MtpPacket {
private:
    // current offset for get/put methods
    int                 mOffset;

public:
                        MtpDataPacket();
    virtual             ~MtpDataPacket();

    virtual void        reset();

    void                setOperationCode(MtpOperationCode code);
    void                setTransactionID(MtpTransactionID id);

    inline uint8_t      getUInt8() { return (uint8_t)mBuffer[mOffset++]; }
    inline int8_t       getInt8() { return (int8_t)mBuffer[mOffset++]; }
    uint16_t            getUInt16();
    inline int16_t      getInt16() { return (int16_t)getUInt16(); }
    uint32_t            getUInt32();
    inline int32_t      getInt32() { return (int32_t)getUInt32(); }
    uint64_t            getUInt64();
    inline int64_t      getInt64() { return (int64_t)getUInt64(); }
    void                getString(MtpStringBuffer& string);

    void                putInt8(int8_t value);
    void                putUInt8(uint8_t value);
    void                putInt16(int16_t value);
    void                putUInt16(uint16_t value);
    void                putInt32(int32_t value);
    void                putUInt32(uint32_t value);
    void                putInt64(int64_t value);
    void                putUInt64(uint64_t value);

    void                putAInt8(const int8_t* values, int count);
    void                putAUInt8(const uint8_t* values, int count);
    void                putAInt16(const int16_t* values, int count);
    void                putAUInt16(const uint16_t* values, int count);
    void                putAInt32(const int32_t* values, int count);
    void                putAUInt32(const uint32_t* values, int count);
    void                putAUInt32(const UInt32List* list);
    void                putAInt64(const int64_t* values, int count);
    void                putAUInt64(const uint64_t* values, int count);
    void                putString(const MtpStringBuffer& string);
    void                putString(const char* string);
    inline void         putEmptyString() { putUInt16(0); }
    inline void         putEmptyArray() { putUInt32(0); }


#ifdef MTP_DEVICE
    // fill our buffer with data from the given file descriptor
    int                 read(int fd);
    int                 readDataHeader(int fd);

    // write our data to the given file descriptor
    int                 write(int fd);
    int                 writeDataHeader(int fd, uint32_t length);
#endif

#ifdef MTP_HOST
    int                 read(struct usb_endpoint *ep);
    int                 write(struct usb_endpoint *ep);
#endif

    inline bool         hasData() const { return mPacketSize > MTP_CONTAINER_HEADER_SIZE; }
};

#endif // _MTP_DATA_PACKET_H
