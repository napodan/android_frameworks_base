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

package com.android.server;

import com.android.internal.content.PackageMonitor;
import com.android.internal.os.storage.ExternalStorageFormatter;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.JournaledFile;
import com.android.internal.util.XmlUtils;
import com.android.internal.widget.LockPatternUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.app.Activity;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.app.admin.IDevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.RecoverySystem;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Slog;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.util.Xml;
import android.view.WindowManagerPolicy;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Implementation of the device policy APIs.
 */
public class DevicePolicyManagerService extends IDevicePolicyManager.Stub {
    static final String TAG = "DevicePolicyManagerService";

    final Context mContext;
    final MyPackageMonitor mMonitor;
    final PowerManager.WakeLock mWakeLock;

    IPowerManager mIPowerManager;

    int mActivePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
    int mActivePasswordLength = 0;
    int mActivePasswordUpperCase = 0;
    int mActivePasswordLowerCase = 0;
    int mActivePasswordLetters = 0;
    int mActivePasswordNumeric = 0;
    int mActivePasswordSymbols = 0;
    int mActivePasswordNonLetter = 0;
    int mFailedPasswordAttempts = 0;

    int mPasswordOwner = -1;

    final HashMap<ComponentName, ActiveAdmin> mAdminMap
            = new HashMap<ComponentName, ActiveAdmin>();
    final ArrayList<ActiveAdmin> mAdminList
            = new ArrayList<ActiveAdmin>();

    static class ActiveAdmin {
        final DeviceAdminInfo info;

        int passwordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
        int minimumPasswordLength = 0;
        int passwordHistoryLength = 0;
        int minimumPasswordUpperCase = 0;
        int minimumPasswordLowerCase = 0;
        int minimumPasswordLetters = 1;
        int minimumPasswordNumeric = 1;
        int minimumPasswordSymbols = 1;
        int minimumPasswordNonLetter = 0;
        long maximumTimeToUnlock = 0;
        int maximumFailedPasswordsForWipe = 0;

        ActiveAdmin(DeviceAdminInfo _info) {
            info = _info;
        }

        int getUid() { return info.getActivityInfo().applicationInfo.uid; }

        void writeToXml(XmlSerializer out)
                throws IllegalArgumentException, IllegalStateException, IOException {
            out.startTag(null, "policies");
            info.writePoliciesToXml(out);
            out.endTag(null, "policies");
            if (passwordQuality != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                out.startTag(null, "password-quality");
                out.attribute(null, "value", Integer.toString(passwordQuality));
                out.endTag(null, "password-quality");
                if (minimumPasswordLength > 0) {
                    out.startTag(null, "min-password-length");
                    out.attribute(null, "value", Integer.toString(minimumPasswordLength));
                    out.endTag(null, "min-password-length");
                }
                if(passwordHistoryLength > 0) {
                    out.startTag(null, "password-history-length");
                    out.attribute(null, "value", Integer.toString(passwordHistoryLength));
                    out.endTag(null, "password-history-length");
                }
                if (minimumPasswordUpperCase > 0) {
                    out.startTag(null, "min-password-uppercase");
                    out.attribute(null, "value", Integer.toString(minimumPasswordUpperCase));
                    out.endTag(null, "min-password-uppercase");
                }
                if (minimumPasswordLowerCase > 0) {
                    out.startTag(null, "min-password-lowercase");
                    out.attribute(null, "value", Integer.toString(minimumPasswordLowerCase));
                    out.endTag(null, "min-password-lowercase");
                }
                if (minimumPasswordLetters > 0) {
                    out.startTag(null, "min-password-letters");
                    out.attribute(null, "value", Integer.toString(minimumPasswordLetters));
                    out.endTag(null, "min-password-letters");
                }
                if (minimumPasswordNumeric > 0) {
                    out.startTag(null, "min-password-numeric");
                    out.attribute(null, "value", Integer.toString(minimumPasswordNumeric));
                    out.endTag(null, "min-password-numeric");
                }
                if (minimumPasswordSymbols > 0) {
                    out.startTag(null, "min-password-symbols");
                    out.attribute(null, "value", Integer.toString(minimumPasswordSymbols));
                    out.endTag(null, "min-password-symbols");
                }
                if (minimumPasswordNonLetter > 0) {
                    out.startTag(null, "min-password-nonletter");
                    out.attribute(null, "value", Integer.toString(minimumPasswordNonLetter));
                    out.endTag(null, "min-password-nonletter");
                }
            }
            if (maximumTimeToUnlock != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                out.startTag(null, "max-time-to-unlock");
                out.attribute(null, "value", Long.toString(maximumTimeToUnlock));
                out.endTag(null, "max-time-to-unlock");
            }
            if (maximumFailedPasswordsForWipe != 0) {
                out.startTag(null, "max-failed-password-wipe");
                out.attribute(null, "value", Integer.toString(maximumFailedPasswordsForWipe));
                out.endTag(null, "max-failed-password-wipe");
            }
        }

        void readFromXml(XmlPullParser parser)
                throws XmlPullParserException, IOException {
            int outerDepth = parser.getDepth();
            int type;
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                   && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                String tag = parser.getName();
                if ("policies".equals(tag)) {
                    info.readPoliciesFromXml(parser);
                } else if ("password-quality".equals(tag)) {
                    passwordQuality = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else if ("min-password-length".equals(tag)) {
                    minimumPasswordLength = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else if ("password-history-length".equals(tag)) {
                    passwordHistoryLength = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else if ("min-password-uppercase".equals(tag)) {
                    minimumPasswordUpperCase = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else if ("min-password-lowercase".equals(tag)) {
                    minimumPasswordLowerCase = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else if ("min-password-letters".equals(tag)) {
                    minimumPasswordLetters = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else if ("min-password-numeric".equals(tag)) {
                    minimumPasswordNumeric = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else if ("min-password-symbols".equals(tag)) {
                    minimumPasswordSymbols = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else if ("min-password-nonletter".equals(tag)) {
                    minimumPasswordNonLetter = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else if ("max-time-to-unlock".equals(tag)) {
                    maximumTimeToUnlock = Long.parseLong(
                            parser.getAttributeValue(null, "value"));
                } else if ("max-failed-password-wipe".equals(tag)) {
                    maximumFailedPasswordsForWipe = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                } else {
                    Slog.w(TAG, "Unknown admin tag: " + tag);
                }
                XmlUtils.skipCurrentTag(parser);
            }
        }

        void dump(String prefix, PrintWriter pw) {
            pw.print(prefix); pw.print("uid="); pw.println(getUid());
            pw.print(prefix); pw.println("policies:");
            ArrayList<DeviceAdminInfo.PolicyInfo> pols = info.getUsedPolicies();
            if (pols != null) {
                for (int i=0; i<pols.size(); i++) {
                    pw.print(prefix); pw.print("  "); pw.println(pols.get(i).tag);
                }
            }
            pw.print(prefix); pw.print("passwordQuality=0x");
                    pw.println(Integer.toHexString(passwordQuality));
            pw.print(prefix); pw.print("minimumPasswordLength=");
                    pw.println(minimumPasswordLength);
            pw.print(prefix); pw.print("passwordHistoryLength=");
                    pw.println(passwordHistoryLength);
            pw.print(prefix); pw.print("minimumPasswordUpperCase=");
                    pw.println(minimumPasswordUpperCase);
            pw.print(prefix); pw.print("minimumPasswordLowerCase=");
                    pw.println(minimumPasswordLowerCase);
            pw.print(prefix); pw.print("minimumPasswordLetters=");
                    pw.println(minimumPasswordLetters);
            pw.print(prefix); pw.print("minimumPasswordNumeric=");
                    pw.println(minimumPasswordNumeric);
            pw.print(prefix); pw.print("minimumPasswordSymbols=");
                    pw.println(minimumPasswordSymbols);
            pw.print(prefix); pw.print("minimumPasswordNonLetter=");
                    pw.println(minimumPasswordNonLetter);
            pw.print(prefix); pw.print("maximumTimeToUnlock=");
                    pw.println(maximumTimeToUnlock);
            pw.print(prefix); pw.print("maximumFailedPasswordsForWipe=");
                    pw.println(maximumFailedPasswordsForWipe);
        }
    }

    class MyPackageMonitor extends PackageMonitor {
        public void onSomePackagesChanged() {
            synchronized (DevicePolicyManagerService.this) {
                boolean removed = false;
                for (int i=mAdminList.size()-1; i>=0; i--) {
                    ActiveAdmin aa = mAdminList.get(i);
                    int change = isPackageDisappearing(aa.info.getPackageName());
                    if (change == PACKAGE_PERMANENT_CHANGE
                            || change == PACKAGE_TEMPORARY_CHANGE) {
                        Slog.w(TAG, "Admin unexpectedly uninstalled: "
                                + aa.info.getComponent());
                        removed = true;
                        mAdminList.remove(i);
                    } else if (isPackageModified(aa.info.getPackageName())) {
                        try {
                            mContext.getPackageManager().getReceiverInfo(
                                    aa.info.getComponent(), 0);
                        } catch (NameNotFoundException e) {
                            Slog.w(TAG, "Admin package change removed component: "
                                    + aa.info.getComponent());
                            removed = true;
                            mAdminList.remove(i);
                        }
                    }
                }
                if (removed) {
                    validatePasswordOwnerLocked();
                }
            }
        }
    }

    /**
     * Instantiates the service.
     */
    public DevicePolicyManagerService(Context context) {
        mContext = context;
        mMonitor = new MyPackageMonitor();
        mMonitor.register(context, true);
        mWakeLock = ((PowerManager)context.getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DPM");
    }

    private IPowerManager getIPowerManager() {
        if (mIPowerManager == null) {
            IBinder b = ServiceManager.getService(Context.POWER_SERVICE);
            mIPowerManager = IPowerManager.Stub.asInterface(b);
        }
        return mIPowerManager;
    }

    ActiveAdmin getActiveAdminUncheckedLocked(ComponentName who) {
        ActiveAdmin admin = mAdminMap.get(who);
        if (admin != null
                && who.getPackageName().equals(admin.info.getActivityInfo().packageName)
                && who.getClassName().equals(admin.info.getActivityInfo().name)) {
            return admin;
        }
        return null;
    }

    ActiveAdmin getActiveAdminForCallerLocked(ComponentName who, int reqPolicy)
            throws SecurityException {
        final int callingUid = Binder.getCallingUid();
        if (who != null) {
            ActiveAdmin admin = mAdminMap.get(who);
            if (admin == null) {
                throw new SecurityException("No active admin " + who);
            }
            if (admin.getUid() != callingUid) {
                throw new SecurityException("Admin " + who + " is not owned by uid "
                        + Binder.getCallingUid());
            }
            if (!admin.info.usesPolicy(reqPolicy)) {
                throw new SecurityException("Admin " + admin.info.getComponent()
                        + " did not specify uses-policy for: "
                        + admin.info.getTagForPolicy(reqPolicy));
            }
            return admin;
        } else {
            final int N = mAdminList.size();
            for (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (admin.getUid() == callingUid && admin.info.usesPolicy(reqPolicy)) {
                    return admin;
                }
            }
            throw new SecurityException("No active admin owned by uid "
                    + Binder.getCallingUid() + " for policy #" + reqPolicy);
        }
    }

    void sendAdminCommandLocked(ActiveAdmin admin, String action) {
        Intent intent = new Intent(action);
        intent.setComponent(admin.info.getComponent());
        mContext.sendBroadcast(intent);
    }

    void sendAdminCommandLocked(String action, int reqPolicy) {
        final int N = mAdminList.size();
        if (N > 0) {
            for (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (admin.info.usesPolicy(reqPolicy)) {
                    sendAdminCommandLocked(admin, action);
                }
            }
        }
    }

    void removeActiveAdminLocked(ComponentName adminReceiver) {
        ActiveAdmin admin = getActiveAdminUncheckedLocked(adminReceiver);
        if (admin != null) {
            sendAdminCommandLocked(admin,
                    DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLED);
            // XXX need to wait for it to complete.
            mAdminList.remove(admin);
            mAdminMap.remove(adminReceiver);
            validatePasswordOwnerLocked();
        }
    }

    public DeviceAdminInfo findAdmin(ComponentName adminName) {
        Intent resolveIntent = new Intent();
        resolveIntent.setComponent(adminName);
        List<ResolveInfo> infos = mContext.getPackageManager().queryBroadcastReceivers(
                resolveIntent, PackageManager.GET_META_DATA);
        if (infos == null || infos.size() <= 0) {
            throw new IllegalArgumentException("Unknown admin: " + adminName);
        }

        try {
            return new DeviceAdminInfo(mContext, infos.get(0));
        } catch (XmlPullParserException e) {
            Slog.w(TAG, "Bad device admin requested: " + adminName, e);
            return null;
        } catch (IOException e) {
            Slog.w(TAG, "Bad device admin requested: " + adminName, e);
            return null;
        }
    }

    private static JournaledFile makeJournaledFile() {
        final String base = "/data/system/device_policies.xml";
        return new JournaledFile(new File(base), new File(base + ".tmp"));
    }

    private void saveSettingsLocked() {
        JournaledFile journal = makeJournaledFile();
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(journal.chooseForWrite(), false);
            XmlSerializer out = new FastXmlSerializer();
            out.setOutput(stream, "utf-8");
            out.startDocument(null, true);

            out.startTag(null, "policies");

            final int N = mAdminList.size();
            for (int i=0; i<N; i++) {
                ActiveAdmin ap = mAdminList.get(i);
                if (ap != null) {
                    out.startTag(null, "admin");
                    out.attribute(null, "name", ap.info.getComponent().flattenToString());
                    ap.writeToXml(out);
                    out.endTag(null, "admin");
                }
            }

            if (mPasswordOwner >= 0) {
                out.startTag(null, "password-owner");
                out.attribute(null, "value", Integer.toString(mPasswordOwner));
                out.endTag(null, "password-owner");
            }

            if (mFailedPasswordAttempts != 0) {
                out.startTag(null, "failed-password-attempts");
                out.attribute(null, "value", Integer.toString(mFailedPasswordAttempts));
                out.endTag(null, "failed-password-attempts");
            }

            if (mActivePasswordQuality != 0 || mActivePasswordLength != 0
                    || mActivePasswordUpperCase != 0 || mActivePasswordLowerCase != 0
                    || mActivePasswordLetters != 0 || mActivePasswordNumeric != 0
                    || mActivePasswordSymbols != 0 || mActivePasswordNonLetter != 0) {
                out.startTag(null, "active-password");
                out.attribute(null, "quality", Integer.toString(mActivePasswordQuality));
                out.attribute(null, "length", Integer.toString(mActivePasswordLength));
                out.attribute(null, "uppercase", Integer.toString(mActivePasswordUpperCase));
                out.attribute(null, "lowercase", Integer.toString(mActivePasswordLowerCase));
                out.attribute(null, "letters", Integer.toString(mActivePasswordLetters));
                out.attribute(null, "numeric", Integer
                        .toString(mActivePasswordNumeric));
                out.attribute(null, "symbols", Integer.toString(mActivePasswordSymbols));
                out.attribute(null, "nonletter", Integer.toString(mActivePasswordNonLetter));
                out.endTag(null, "active-password");
            }

            out.endTag(null, "policies");

            out.endDocument();
            stream.close();
            journal.commit();
            sendChangedNotification();
        } catch (IOException e) {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException ex) {
                // Ignore
            }
            journal.rollback();
        }
    }

    private void sendChangedNotification() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mContext.sendBroadcast(intent);
    }

    private void loadSettingsLocked() {
        JournaledFile journal = makeJournaledFile();
        FileInputStream stream = null;
        File file = journal.chooseForRead();
        try {
            stream = new FileInputStream(file);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);

            int type;
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
            }
            String tag = parser.getName();
            if (!"policies".equals(tag)) {
                throw new XmlPullParserException(
                        "Settings do not start with policies tag: found " + tag);
            }
            type = parser.next();
            int outerDepth = parser.getDepth();
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                   && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                tag = parser.getName();
                if ("admin".equals(tag)) {
                    String name = parser.getAttributeValue(null, "name");
                    try {
                        DeviceAdminInfo dai = findAdmin(
                                ComponentName.unflattenFromString(name));
                        if (dai != null) {
                            ActiveAdmin ap = new ActiveAdmin(dai);
                            ap.readFromXml(parser);
                            mAdminMap.put(ap.info.getComponent(), ap);
                            mAdminList.add(ap);
                        }
                    } catch (RuntimeException e) {
                        Slog.w(TAG, "Failed loading admin " + name, e);
                    }
                } else if ("failed-password-attempts".equals(tag)) {
                    mFailedPasswordAttempts = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                    XmlUtils.skipCurrentTag(parser);
                } else if ("password-owner".equals(tag)) {
                    mPasswordOwner = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                    XmlUtils.skipCurrentTag(parser);
                } else if ("active-password".equals(tag)) {
                    mActivePasswordQuality = Integer.parseInt(
                            parser.getAttributeValue(null, "quality"));
                    mActivePasswordLength = Integer.parseInt(
                            parser.getAttributeValue(null, "length"));
                    mActivePasswordUpperCase = Integer.parseInt(
                            parser.getAttributeValue(null, "uppercase"));
                    mActivePasswordLowerCase = Integer.parseInt(
                            parser.getAttributeValue(null, "lowercase"));
                    mActivePasswordLetters = Integer.parseInt(
                            parser.getAttributeValue(null, "letters"));
                    mActivePasswordNumeric = Integer.parseInt(
                            parser.getAttributeValue(null, "numeric"));
                    mActivePasswordSymbols = Integer.parseInt(
                            parser.getAttributeValue(null, "symbols"));
                    mActivePasswordNonLetter = Integer.parseInt(
                            parser.getAttributeValue(null, "nonletter"));
                    XmlUtils.skipCurrentTag(parser);
                } else {
                    Slog.w(TAG, "Unknown tag: " + tag);
                    XmlUtils.skipCurrentTag(parser);
                }
            }
        } catch (NullPointerException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        } catch (NumberFormatException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        } catch (XmlPullParserException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        } catch (FileNotFoundException e) {
            // Don't be noisy, this is normal if we haven't defined any policies.
        } catch (IOException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        } catch (IndexOutOfBoundsException e) {
            Slog.w(TAG, "failed parsing " + file + " " + e);
        }
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        // Validate that what we stored for the password quality matches
        // sufficiently what is currently set.  Note that this is only
        // a sanity check in case the two get out of sync; this should
        // never normally happen.
        LockPatternUtils utils = new LockPatternUtils(mContext);
        if (utils.getActivePasswordQuality() < mActivePasswordQuality) {
            Slog.w(TAG, "Active password quality 0x"
                    + Integer.toHexString(mActivePasswordQuality)
                    + " does not match actual quality 0x"
                    + Integer.toHexString(utils.getActivePasswordQuality()));
            mActivePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
            mActivePasswordLength = 0;
            mActivePasswordUpperCase = 0;
            mActivePasswordLowerCase = 0;
            mActivePasswordLetters = 0;
            mActivePasswordNumeric = 0;
            mActivePasswordSymbols = 0;
            mActivePasswordNonLetter = 0;
        }

        validatePasswordOwnerLocked();

        long timeMs = getMaximumTimeToLock(null);
        if (timeMs <= 0) {
            timeMs = Integer.MAX_VALUE;
        }
        try {
            getIPowerManager().setMaximumScreenOffTimeount((int)timeMs);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failure talking with power manager", e);
        }
    }

    static void validateQualityConstant(int quality) {
        switch (quality) {
            case DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED:
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                return;
        }
        throw new IllegalArgumentException("Invalid quality constant: 0x"
                + Integer.toHexString(quality));
    }

    void validatePasswordOwnerLocked() {
        if (mPasswordOwner >= 0) {
            boolean haveOwner = false;
            for (int i=mAdminList.size()-1; i>=0; i--) {
                if (mAdminList.get(i).getUid() == mPasswordOwner) {
                    haveOwner = true;
                    break;
                }
            }
            if (!haveOwner) {
                Slog.w(TAG, "Previous password owner " + mPasswordOwner
                        + " no longer active; disabling");
                mPasswordOwner = -1;
            }
        }
    }

    public void systemReady() {
        synchronized (this) {
            loadSettingsLocked();
        }
    }

    public void setActiveAdmin(ComponentName adminReceiver) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_DEVICE_ADMIN, null);

        DeviceAdminInfo info = findAdmin(adminReceiver);
        if (info == null) {
            throw new IllegalArgumentException("Bad admin: " + adminReceiver);
        }
        synchronized (this) {
            long ident = Binder.clearCallingIdentity();
            try {
                if (getActiveAdminUncheckedLocked(adminReceiver) != null) {
                    throw new IllegalArgumentException("Admin is already added");
                }
                ActiveAdmin admin = new ActiveAdmin(info);
                mAdminMap.put(adminReceiver, admin);
                mAdminList.add(admin);
                saveSettingsLocked();
                sendAdminCommandLocked(admin,
                        DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    public boolean isAdminActive(ComponentName adminReceiver) {
        synchronized (this) {
            return getActiveAdminUncheckedLocked(adminReceiver) != null;
        }
    }

    public List<ComponentName> getActiveAdmins() {
        synchronized (this) {
            final int N = mAdminList.size();
            if (N <= 0) {
                return null;
            }
            ArrayList<ComponentName> res = new ArrayList<ComponentName>(N);
            for (int i=0; i<N; i++) {
                res.add(mAdminList.get(i).info.getComponent());
            }
            return res;
        }
    }

    public boolean packageHasActiveAdmins(String packageName) {
        synchronized (this) {
            final int N = mAdminList.size();
            for (int i=0; i<N; i++) {
                if (mAdminList.get(i).info.getPackageName().equals(packageName)) {
                    return true;
                }
            }
            return false;
        }
    }

    public void removeActiveAdmin(ComponentName adminReceiver) {
        synchronized (this) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(adminReceiver);
            if (admin == null) {
                return;
            }
            if (admin.getUid() != Binder.getCallingUid()) {
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.BIND_DEVICE_ADMIN, null);
            }
            long ident = Binder.clearCallingIdentity();
            try {
                removeActiveAdminLocked(adminReceiver);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    public void setPasswordQuality(ComponentName who, int quality) {
        validateQualityConstant(quality);

        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.passwordQuality != quality) {
                ap.passwordQuality = quality;
                saveSettingsLocked();
            }
        }
    }

    public int getPasswordQuality(ComponentName who) {
        synchronized (this) {
            int mode = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.passwordQuality : mode;
            }

            final int N = mAdminList.size();
            for  (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (mode < admin.passwordQuality) {
                    mode = admin.passwordQuality;
                }
            }
            return mode;
        }
    }

    public void setPasswordMinimumLength(ComponentName who, int length) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordLength != length) {
                ap.minimumPasswordLength = length;
                saveSettingsLocked();
            }
        }
    }

    public int getPasswordMinimumLength(ComponentName who) {
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.minimumPasswordLength : length;
            }

            final int N = mAdminList.size();
            for  (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (length < admin.minimumPasswordLength) {
                    length = admin.minimumPasswordLength;
                }
            }
            return length;
        }
    }

    public void setPasswordHistoryLength(ComponentName who, int length) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.passwordHistoryLength != length) {
                ap.passwordHistoryLength = length;
                saveSettingsLocked();
            }
        }
    }

    public int getPasswordHistoryLength(ComponentName who) {
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.passwordHistoryLength : length;
            }

            final int N = mAdminList.size();
            for (int i = 0; i < N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (length < admin.passwordHistoryLength) {
                    length = admin.passwordHistoryLength;
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumUpperCase(ComponentName who, int length) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordUpperCase != length) {
                ap.minimumPasswordUpperCase = length;
                saveSettingsLocked();
            }
        }
    }

    public int getPasswordMinimumUpperCase(ComponentName who) {
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.minimumPasswordUpperCase : length;
            }

            final int N = mAdminList.size();
            for (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (length < admin.minimumPasswordUpperCase) {
                    length = admin.minimumPasswordUpperCase;
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumLowerCase(ComponentName who, int length) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordLowerCase != length) {
                ap.minimumPasswordLowerCase = length;
                saveSettingsLocked();
            }
        }
    }

    public int getPasswordMinimumLowerCase(ComponentName who) {
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.minimumPasswordLowerCase : length;
            }

            final int N = mAdminList.size();
            for (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (length < admin.minimumPasswordLowerCase) {
                    length = admin.minimumPasswordLowerCase;
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumLetters(ComponentName who, int length) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordLetters != length) {
                ap.minimumPasswordLetters = length;
                saveSettingsLocked();
            }
        }
    }

    public int getPasswordMinimumLetters(ComponentName who) {
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.minimumPasswordLetters : length;
            }

            final int N = mAdminList.size();
            for (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (length < admin.minimumPasswordLetters) {
                    length = admin.minimumPasswordLetters;
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumNumeric(ComponentName who, int length) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordNumeric != length) {
                ap.minimumPasswordNumeric = length;
                saveSettingsLocked();
            }
        }
    }

    public int getPasswordMinimumNumeric(ComponentName who) {
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.minimumPasswordNumeric : length;
            }

            final int N = mAdminList.size();
            for (int i = 0; i < N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (length < admin.minimumPasswordNumeric) {
                    length = admin.minimumPasswordNumeric;
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumSymbols(ComponentName who, int length) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordSymbols != length) {
                ap.minimumPasswordSymbols = length;
                saveSettingsLocked();
            }
        }
    }

    public int getPasswordMinimumSymbols(ComponentName who) {
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.minimumPasswordSymbols : length;
            }

            final int N = mAdminList.size();
            for  (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (length < admin.minimumPasswordSymbols) {
                    length = admin.minimumPasswordSymbols;
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumNonLetter(ComponentName who, int length) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordNonLetter != length) {
                ap.minimumPasswordNonLetter = length;
                saveSettingsLocked();
            }
        }
    }

    public int getPasswordMinimumNonLetter(ComponentName who) {
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.minimumPasswordNonLetter : length;
            }

            final int N = mAdminList.size();
            for (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (length < admin.minimumPasswordNonLetter) {
                    length = admin.minimumPasswordNonLetter;
                }
            }
            return length;
        }
    }

    public boolean isActivePasswordSufficient() {
        synchronized (this) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (mActivePasswordQuality < getPasswordQuality(null)
                    || mActivePasswordLength < getPasswordMinimumLength(null)) {
                return false;
            }
            if(mActivePasswordQuality != DevicePolicyManager.PASSWORD_QUALITY_COMPLEX) {
                return true;
            }
            return mActivePasswordUpperCase >= getPasswordMinimumUpperCase(null)
                    && mActivePasswordLowerCase >= getPasswordMinimumLowerCase(null)
                    && mActivePasswordLetters >= getPasswordMinimumLetters(null)
                    && mActivePasswordNumeric >= getPasswordMinimumNumeric(null)
                    && mActivePasswordSymbols >= getPasswordMinimumSymbols(null)
                    && mActivePasswordNonLetter >= getPasswordMinimumNonLetter(null);
        }
    }

    public int getCurrentFailedPasswordAttempts() {
        synchronized (this) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_WATCH_LOGIN);
            return mFailedPasswordAttempts;
        }
    }

    public void setMaximumFailedPasswordsForWipe(ComponentName who, int num) {
        synchronized (this) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_WIPE_DATA);
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_WATCH_LOGIN);
            if (ap.maximumFailedPasswordsForWipe != num) {
                ap.maximumFailedPasswordsForWipe = num;
                saveSettingsLocked();
            }
        }
    }

    public int getMaximumFailedPasswordsForWipe(ComponentName who) {
        synchronized (this) {
            int count = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.maximumFailedPasswordsForWipe : count;
            }

            final int N = mAdminList.size();
            for  (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (count == 0) {
                    count = admin.maximumFailedPasswordsForWipe;
                } else if (admin.maximumFailedPasswordsForWipe != 0
                        && count > admin.maximumFailedPasswordsForWipe) {
                    count = admin.maximumFailedPasswordsForWipe;
                }
            }
            return count;
        }
    }

    public boolean resetPassword(String password, int flags) {
        int quality;
        synchronized (this) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_RESET_PASSWORD);
            quality = getPasswordQuality(null);
            if (quality != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                int realQuality = LockPatternUtils.computePasswordQuality(password);
                if (realQuality < quality
                        && quality != DevicePolicyManager.PASSWORD_QUALITY_COMPLEX) {
                    Slog.w(TAG, "resetPassword: password quality 0x"
                            + Integer.toHexString(quality)
                            + " does not meet required quality 0x"
                            + Integer.toHexString(quality));
                    return false;
                }
                quality = Math.max(realQuality, quality);
            }
            int length = getPasswordMinimumLength(null);
            if (password.length() < length) {
                Slog.w(TAG, "resetPassword: password length " + password.length()
                        + " does not meet required length " + length);
                return false;
            }
            if (quality == DevicePolicyManager.PASSWORD_QUALITY_COMPLEX) {
                int letters = 0;
                int uppercase = 0;
                int lowercase = 0;
                int numbers = 0;
                int symbols = 0;
                int nonletter = 0;
                for (int i = 0; i < password.length(); i++) {
                    char c = password.charAt(i);
                    if (c >= 'A' && c <= 'Z') {
                        letters++;
                        uppercase++;
                    } else if (c >= 'a' && c <= 'z') {
                        letters++;
                        lowercase++;
                    } else if (c >= '0' && c <= '9') {
                        numbers++;
                        nonletter++;
                    } else {
                        symbols++;
                        nonletter++;
                    }
                }
                int neededLetters = getPasswordMinimumLetters(null);
                if(letters < neededLetters) {
                    Slog.w(TAG, "resetPassword: number of letters " + letters
                            + " does not meet required number of letters " + neededLetters);
                    return false;
                }
                int neededNumbers = getPasswordMinimumNumeric(null);
                if (numbers < neededNumbers) {
                    Slog
                            .w(TAG, "resetPassword: number of numerical digits " + numbers
                                    + " does not meet required number of numerical digits "
                                    + neededNumbers);
                    return false;
                }
                int neededLowerCase = getPasswordMinimumLowerCase(null);
                if (lowercase < neededLowerCase) {
                    Slog.w(TAG, "resetPassword: number of lowercase letters " + lowercase
                            + " does not meet required number of lowercase letters "
                            + neededLowerCase);
                    return false;
                }
                int neededUpperCase = getPasswordMinimumUpperCase(null);
                if (uppercase < neededUpperCase) {
                    Slog.w(TAG, "resetPassword: number of uppercase letters " + uppercase
                            + " does not meet required number of uppercase letters "
                            + neededUpperCase);
                    return false;
                }
                int neededSymbols = getPasswordMinimumSymbols(null);
                if (symbols < neededSymbols) {
                    Slog.w(TAG, "resetPassword: number of special symbols " + symbols
                            + " does not meet required number of special symbols " + neededSymbols);
                    return false;
                }
                int neededNonLetter = getPasswordMinimumNonLetter(null);
                if (nonletter < neededNonLetter) {
                    Slog.w(TAG, "resetPassword: number of non-letter characters " + nonletter
                            + " does not meet required number of non-letter characters "
                            + neededNonLetter);
                    return false;
                }
            }

            LockPatternUtils utils = new LockPatternUtils(mContext);
            if(utils.checkPasswordHistory(password)) {
                Slog.w(TAG, "resetPassword: password is the same as one of the last "
                        + getPasswordHistoryLength(null) + " passwords");
                return false;
            }
        }

        int callingUid = Binder.getCallingUid();
        if (mPasswordOwner >= 0 && mPasswordOwner != callingUid) {
            Slog.w(TAG, "resetPassword: already set by another uid and not entered by user");
            return false;
        }

        // Don't do this with the lock held, because it is going to call
        // back in to the service.
        long ident = Binder.clearCallingIdentity();
        try {
            LockPatternUtils utils = new LockPatternUtils(mContext);
            utils.saveLockPassword(password, quality);
            synchronized (this) {
                int newOwner = (flags&DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)
                        != 0 ? callingUid : -1;
                if (mPasswordOwner != newOwner) {
                    mPasswordOwner = newOwner;
                    saveSettingsLocked();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        return true;
    }

    public void setMaximumTimeToLock(ComponentName who, long timeMs) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_FORCE_LOCK);
            if (ap.maximumTimeToUnlock != timeMs) {
                ap.maximumTimeToUnlock = timeMs;

                long ident = Binder.clearCallingIdentity();
                try {
                    saveSettingsLocked();

                    timeMs = getMaximumTimeToLock(null);
                    if (timeMs <= 0) {
                        timeMs = Integer.MAX_VALUE;
                    }

                    try {
                        getIPowerManager().setMaximumScreenOffTimeount((int)timeMs);
                    } catch (RemoteException e) {
                        Slog.w(TAG, "Failure talking with power manager", e);
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    }

    public long getMaximumTimeToLock(ComponentName who) {
        synchronized (this) {
            long time = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who);
                return admin != null ? admin.maximumTimeToUnlock : time;
            }

            final int N = mAdminList.size();
            for  (int i=0; i<N; i++) {
                ActiveAdmin admin = mAdminList.get(i);
                if (time == 0) {
                    time = admin.maximumTimeToUnlock;
                } else if (admin.maximumTimeToUnlock != 0
                        && time > admin.maximumTimeToUnlock) {
                    time = admin.maximumTimeToUnlock;
                }
            }
            return time;
        }
    }

    public void lockNow() {
        synchronized (this) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_FORCE_LOCK);
            long ident = Binder.clearCallingIdentity();
            try {
                mIPowerManager.goToSleepWithReason(SystemClock.uptimeMillis(),
                        WindowManagerPolicy.OFF_BECAUSE_OF_ADMIN);
            } catch (RemoteException e) {
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    void wipeDataLocked(int flags) {
        if ((flags&DevicePolicyManager.WIPE_EXTERNAL_STORAGE) != 0) {
            Intent intent = new Intent(ExternalStorageFormatter.FORMAT_AND_FACTORY_RESET);
            intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
            mWakeLock.acquire(10000);
            mContext.startService(intent);
        } else {
            try {
                RecoverySystem.rebootWipeUserData(mContext);
            } catch (IOException e) {
                Slog.w(TAG, "Failed requesting data wipe", e);
            }
        }
    }

    public void wipeData(int flags) {
        synchronized (this) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_WIPE_DATA);
            long ident = Binder.clearCallingIdentity();
            try {
                wipeDataLocked(flags);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    public void getRemoveWarning(ComponentName comp, final RemoteCallback result) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_DEVICE_ADMIN, null);

        synchronized (this) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(comp);
            if (admin == null) {
                try {
                    result.sendResult(null);
                } catch (RemoteException e) {
                }
                return;
            }
            Intent intent = new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLE_REQUESTED);
            intent.setComponent(admin.info.getComponent());
            mContext.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        result.sendResult(getResultExtras(false));
                    } catch (RemoteException e) {
                    }
                }
            }, null, Activity.RESULT_OK, null, null);
        }
    }

    public void setActivePasswordState(int quality, int length, int letters, int uppercase,
            int lowercase, int numbers, int symbols, int nonletter) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_DEVICE_ADMIN, null);

        validateQualityConstant(quality);

        synchronized (this) {
            if (mActivePasswordQuality != quality || mActivePasswordLength != length
                    || mFailedPasswordAttempts != 0 || mActivePasswordLetters != letters
                    || mActivePasswordUpperCase != uppercase
                    || mActivePasswordLowerCase != lowercase || mActivePasswordNumeric != numbers
                    || mActivePasswordSymbols != symbols || mActivePasswordNonLetter != nonletter) {
                long ident = Binder.clearCallingIdentity();
                try {
                    mActivePasswordQuality = quality;
                    mActivePasswordLength = length;
                    mActivePasswordLetters = letters;
                    mActivePasswordLowerCase = lowercase;
                    mActivePasswordUpperCase = uppercase;
                    mActivePasswordNumeric = numbers;
                    mActivePasswordSymbols = symbols;
                    mActivePasswordNonLetter = nonletter;
                    mFailedPasswordAttempts = 0;
                    saveSettingsLocked();
                    sendAdminCommandLocked(DeviceAdminReceiver.ACTION_PASSWORD_CHANGED,
                            DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    }

    public void reportFailedPasswordAttempt() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_DEVICE_ADMIN, null);

        synchronized (this) {
            long ident = Binder.clearCallingIdentity();
            try {
                mFailedPasswordAttempts++;
                saveSettingsLocked();
                int max = getMaximumFailedPasswordsForWipe(null);
                if (max > 0 && mFailedPasswordAttempts >= max) {
                    wipeDataLocked(0);
                }
                sendAdminCommandLocked(DeviceAdminReceiver.ACTION_PASSWORD_FAILED,
                        DeviceAdminInfo.USES_POLICY_WATCH_LOGIN);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    public void reportSuccessfulPasswordAttempt() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_DEVICE_ADMIN, null);

        synchronized (this) {
            if (mFailedPasswordAttempts != 0 || mPasswordOwner >= 0) {
                long ident = Binder.clearCallingIdentity();
                try {
                    mFailedPasswordAttempts = 0;
                    mPasswordOwner = -1;
                    saveSettingsLocked();
                    sendAdminCommandLocked(DeviceAdminReceiver.ACTION_PASSWORD_SUCCEEDED,
                            DeviceAdminInfo.USES_POLICY_WATCH_LOGIN);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {

            pw.println("Permission Denial: can't dump DevicePolicyManagerService from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        final Printer p = new PrintWriterPrinter(pw);

        synchronized (this) {
            p.println("Current Device Policy Manager state:");

            p.println("  Enabled Device Admins:");
            final int N = mAdminList.size();
            for (int i=0; i<N; i++) {
                ActiveAdmin ap = mAdminList.get(i);
                if (ap != null) {
                    pw.print("  "); pw.print(ap.info.getComponent().flattenToShortString());
                            pw.println(":");
                    ap.dump("    ", pw);
                }
            }

            pw.println(" ");
            pw.print("  mActivePasswordQuality=0x");
                    pw.println(Integer.toHexString(mActivePasswordQuality));
            pw.print("  mActivePasswordLength="); pw.println(mActivePasswordLength);
            pw.print("  mActivePasswordUpperCase="); pw.println(mActivePasswordUpperCase);
            pw.print("  mActivePasswordLowerCase="); pw.println(mActivePasswordLowerCase);
            pw.print("  mActivePasswordLetters="); pw.println(mActivePasswordLetters);
            pw.print("  mActivePasswordNumeric="); pw.println(mActivePasswordNumeric);
            pw.print("  mActivePasswordSymbols="); pw.println(mActivePasswordSymbols);
            pw.print("  mActivePasswordNonLetter="); pw.println(mActivePasswordNonLetter);
            pw.print("  mFailedPasswordAttempts="); pw.println(mFailedPasswordAttempts);
            pw.print("  mPasswordOwner="); pw.println(mPasswordOwner);
        }
    }
}
