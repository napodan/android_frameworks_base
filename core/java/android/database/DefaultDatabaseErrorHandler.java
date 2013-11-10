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
package android.database;

import java.io.File;
import java.util.ArrayList;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.util.Pair;

/**
 * Default class used defining the actions to take when the following errors are detected
 *   database corruption
 */
public final class DefaultDatabaseErrorHandler implements DatabaseErrorHandler {

    private static final String TAG = "DefaultDatabaseErrorHandler";

    /**
     * defines the default method to be invoked when database corruption is detected.
     * @param dbObj the {@link SQLiteDatabase} object representing the database on which corruption
     * is detected.
     */
    public void onCorruption(SQLiteDatabase dbObj) {
        Log.e(TAG, "Corruption reported by sqlite on database: " + dbObj.getPath());

        // is the corruption detected even before database could be 'opened'?
        if (!dbObj.isOpen()) {
            // database files are not even openable. delete this database file.
            // NOTE if the database has attached databases, then any of them could be corrupt.
            // and not deleting all of them could cause corrupted database file to remain and 
            // make the application crash on database open operation. To avoid this problem,
            // the application should provide its own {@link DatabaseErrorHandler} impl class
            // to delete ALL files of the database (including the attached databases).
            if (!dbObj.getPath().equalsIgnoreCase(":memory:")) {
                // not memory database.
                try {
                    new File(dbObj.getPath()).delete();
                } catch (Exception e) {
                    /* ignore */
                }
            }
            return;
        }

        ArrayList<Pair<String, String>> attachedDbs = null;
        try {
            // Close the database, which will cause subsequent operations to fail.
            // before that, get the attached database list first.
            attachedDbs = dbObj.getAttachedDbs();
            try {
                dbObj.close();
            } catch (SQLiteException e) {
                /* ignore */
            }
        } finally {
            // Delete all files of this corrupt database and/or attached databases
            if (attachedDbs != null) {
                for (Pair<String, String> p : attachedDbs) {
                    // delete file if it is a non-memory database file
                    if (p.second.equalsIgnoreCase(":memory:") || p.second.trim().length() == 0) {
                        continue;
                    }
                    Log.e(TAG, "deleting the database file: " + p.second);
                    try {
                        new File(p.second).delete();
                    } catch (Exception e) {
                        /* ignore */
                    }
                }
            }
        }
    }
}
