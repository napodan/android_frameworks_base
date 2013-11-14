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

package android.media;

import android.util.Log;

/**
 * {@hide}
 */
public class MtpClient {

    private static final String TAG = "MtpClient";

    static {
        System.loadLibrary("media_jni");
    }

    public MtpClient() {
        native_setup();
    }

    @Override
    protected void finalize() {
        native_finalize();
    }

    public void start() {
        mEventThread = new MtpEventThread();
        mEventThread.start();
    }

    private class MtpEventThread extends Thread {

        private boolean mDone;

        public MtpEventThread() {
            super("MtpEventThread");
        }

        public void run() {
            Log.d(TAG, "MtpEventThread starting");
            while (!mDone) {
                // this will wait for an event from an MTP device
                native_wait_for_event();
            }
            Log.d(TAG, "MtpEventThread exiting");
        }
    }

    private void deviceAdded(int id) {
        Log.d(TAG, "deviceAdded " + id);
    }

    private void deviceRemoved(int id) {
        Log.d(TAG, "deviceRemoved " + id);
    }

    private MtpEventThread mEventThread;

    // used by the JNI code
    private int mNativeContext;

    private native final void native_setup();
    private native final void native_finalize();
    private native void native_wait_for_event();
}