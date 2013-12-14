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

#ifndef _MTP_DATABASE_H
#define _MTP_DATABASE_H

#include "MtpTypes.h"
#include "SqliteDatabase.h"

namespace android {

class MtpDataPacket;

class MtpDatabase {
public:
    virtual ~MtpDatabase();

    static uint32_t                 getTableForFile(MtpObjectFormat format);

    virtual MtpObjectHandle         getObjectHandle(const char* path) = 0;
    virtual MtpObjectHandle         addFile(const char* path,
                                            MtpObjectFormat format,
                                            MtpObjectHandle parent,
                                            MtpStorageID storage,
                                            uint64_t size,
                                            time_t modified) = 0;

    virtual MtpObjectHandle         addAudioFile(MtpObjectHandle id) = 0;

    virtual MtpObjectHandle         addAudioFile(MtpObjectHandle id,
                                            const char* title,
                                            const char* artist,
                                            const char* album,
                                            const char* albumArtist,
                                            const char* genre,
                                            const char* composer,
                                            const char* mimeType,
                                            int track,
                                            int year,
                                            int duration) = 0;

    virtual MtpObjectHandleList*    getObjectList(MtpStorageID storageID,
                                    MtpObjectFormat format,
                                    MtpObjectHandle parent) = 0;

    virtual MtpResponseCode         getObjectProperty(MtpObjectHandle handle,
                                            MtpObjectProperty property,
                                            MtpDataPacket& packet) = 0;

    virtual MtpResponseCode         getObjectInfo(MtpObjectHandle handle,
                                            MtpDataPacket& packet) = 0;

    virtual bool                    getObjectFilePath(MtpObjectHandle handle,
                                            MtpString& filePath,
                                            int64_t& fileLength) = 0;
    virtual bool                    deleteFile(MtpObjectHandle handle) = 0;

    // helper for media scanner
    virtual MtpObjectHandle*        getFileList(int& outCount) = 0;

    virtual void                    beginTransaction() = 0;
    virtual void                    commitTransaction() = 0;
    virtual void                    rollbackTransaction() = 0;
};

}; // namespace android

#endif // _MTP_DATABASE_H
