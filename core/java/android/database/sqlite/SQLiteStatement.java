/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.database.sqlite;

import android.os.SystemClock;

import dalvik.system.BlockGuard;

/**
 * A pre-compiled statement against a {@link SQLiteDatabase} that can be reused.
 * The statement cannot return multiple rows, but 1x1 result sets are allowed.
 * Don't use SQLiteStatement constructor directly, please use
 * {@link SQLiteDatabase#compileStatement(String)}
 *<p>
 * SQLiteStatement is not internally synchronized so code using a SQLiteStatement from multiple
 * threads should perform its own synchronization when using the SQLiteStatement.
 */
public class SQLiteStatement extends SQLiteProgram
{
    private static final boolean READ = true;
    private static final boolean WRITE = false;

    /**
     * Don't use SQLiteStatement constructor directly, please use
     * {@link SQLiteDatabase#compileStatement(String)}
     * @param db
     * @param sql
     */
    /* package */ SQLiteStatement(SQLiteDatabase db, String sql) {
        super(db, sql);
    }

    /**
     * Execute this SQL statement, if it is not a query. For example,
     * CREATE TABLE, DELTE, INSERT, etc.
     *
     * @throws android.database.SQLException If the SQL string is invalid for
     *         some reason
     */
    public void execute() {
        long timeStart = acquireAndLock(WRITE);
        try {
            native_execute();
            mDatabase.logTimeStat(mSql, timeStart);
        } finally {
            releaseAndUnlock();
        }
    }

    /**
     * Execute this SQL statement and return the ID of the row inserted due to this call.
     * The SQL statement should be an INSERT for this to be a useful call.
     *
     * @return the row ID of the last row inserted, if this insert is successful. -1 otherwise.
     *
     * @throws android.database.SQLException If the SQL string is invalid for
     *         some reason
     */
    public long executeInsert() {
        long timeStart = acquireAndLock(WRITE);
        try {
            native_execute();
            mDatabase.logTimeStat(mSql, timeStart);
            return (mDatabase.lastChangeCount() > 0) ? mDatabase.lastInsertRow() : -1;
        } finally {
            releaseAndUnlock();
        }
    }

    /**
     * Execute a statement that returns a 1 by 1 table with a numeric value.
     * For example, SELECT COUNT(*) FROM table;
     *
     * @return The result of the query.
     *
     * @throws android.database.sqlite.SQLiteDoneException if the query returns zero rows
     */
    public long simpleQueryForLong() {
        long timeStart = acquireAndLock(READ);
        try {
            long retValue = native_1x1_long();
            mDatabase.logTimeStat(mSql, timeStart);
            return retValue;
        } finally {
            releaseAndUnlock();
        }
    }

    /**
     * Execute a statement that returns a 1 by 1 table with a text value.
     * For example, SELECT COUNT(*) FROM table;
     *
     * @return The result of the query.
     *
     * @throws android.database.sqlite.SQLiteDoneException if the query returns zero rows
     */
    public String simpleQueryForString() {
        long timeStart = acquireAndLock(READ);
        try {
            String retValue = native_1x1_string();
            mDatabase.logTimeStat(mSql, timeStart);
            return retValue;
        } finally {
            releaseAndUnlock();
        }
    }

    /**
     * Called before every method in this class before executing a SQL statement,
     * this method does the following:
     * <ul>
     *   <li>make sure the database is open</li>
     *   <li>notifies {@link BlockGuard} of read/write</li>
     *   <li>get lock on the database</li>
     *   <li>acquire reference on this object</li>
     *   <li>and then return the current time _before_ the database lock was acquired</li>
     * </ul>
     * <p>
     * This method removes the duplcate code from the other public
     * methods in this class.
     */
    private long acquireAndLock(boolean rwFlag) {
        mDatabase.verifyDbIsOpen();
        if (rwFlag == WRITE) {
            BlockGuard.getThreadPolicy().onWriteToDisk();
        } else {
            BlockGuard.getThreadPolicy().onReadFromDisk();
        }
        long startTime = SystemClock.uptimeMillis();
        mDatabase.lock();
        acquireReference();
        mDatabase.closePendingStatements();
        return startTime;
    }

    /**
     * this method releases locks and references acquired in {@link #acquireAndLock(boolean)}.
     */
    private void releaseAndUnlock() {
        releaseReference();
        mDatabase.unlock();
    }

    private final native void native_execute();
    private final native long native_1x1_long();
    private final native String native_1x1_string();
}
