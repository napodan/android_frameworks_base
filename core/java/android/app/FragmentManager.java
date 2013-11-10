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

package android.app;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.util.ArrayList;

final class FragmentManagerState implements Parcelable {
    FragmentState[] mActive;
    int[] mAdded;
    BackStackState[] mBackStack;
    
    public FragmentManagerState() {
    }
    
    public FragmentManagerState(Parcel in) {
        mActive = in.createTypedArray(FragmentState.CREATOR);
        mAdded = in.createIntArray();
        mBackStack = in.createTypedArray(BackStackState.CREATOR);
    }
    
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedArray(mActive, flags);
        dest.writeIntArray(mAdded);
        dest.writeTypedArray(mBackStack, flags);
    }
    
    public static final Parcelable.Creator<FragmentManagerState> CREATOR
            = new Parcelable.Creator<FragmentManagerState>() {
        public FragmentManagerState createFromParcel(Parcel in) {
            return new FragmentManagerState(in);
        }
        
        public FragmentManagerState[] newArray(int size) {
            return new FragmentManagerState[size];
        }
    };
}

/**
 * @hide
 * Container for fragments associated with an activity.
 */
public class FragmentManager {
    static final boolean DEBUG = true;
    static final String TAG = "FragmentManager";
    
    ArrayList<Fragment> mActive;
    ArrayList<Fragment> mAdded;
    ArrayList<Integer> mAvailIndices;
    ArrayList<BackStackEntry> mBackStack;
    
    int mCurState = Fragment.INITIALIZING;
    Activity mActivity;
    
    // Temporary vars for state save and restore.
    Bundle mStateBundle = null;
    SparseArray<Parcelable> mStateArray = null;
    
    Animation loadAnimation(Fragment fragment, int transit, boolean enter,
            int transitionStyle) {
        Animation animObj = fragment.onCreateAnimation(transitionStyle, enter,
                fragment.mNextAnim);
        if (animObj != null) {
            return animObj;
        }
        
        if (fragment.mNextAnim != 0) {
            Animation anim = AnimationUtils.loadAnimation(mActivity, fragment.mNextAnim);
            if (anim != null) {
                return anim;
            }
        }
        
        if (transit == 0) {
            return null;
        }
        
        int styleIndex = transitToStyleIndex(transit, enter);
        if (styleIndex < 0) {
            return null;
        }
        
        if (transitionStyle == 0 && mActivity.getWindow() != null) {
            transitionStyle = mActivity.getWindow().getAttributes().windowAnimations;
        }
        if (transitionStyle == 0) {
            return null;
        }
        
        TypedArray attrs = mActivity.obtainStyledAttributes(transitionStyle,
                com.android.internal.R.styleable.WindowAnimation);
        int anim = attrs.getResourceId(styleIndex, 0);
        attrs.recycle();
        
        if (anim == 0) {
            return null;
        }
        
        return AnimationUtils.loadAnimation(mActivity, anim);
    }
    
    void moveToState(Fragment f, int newState, int transit, int transitionStyle) {
        // Fragments that are not currently added will sit in the onCreate() state.
        if (!f.mAdded && newState > Fragment.CREATED) {
            newState = Fragment.CREATED;
        }
        
        if (f.mState < newState) {
            switch (f.mState) {
                case Fragment.INITIALIZING:
                    if (DEBUG) Log.v(TAG, "moveto CREATED: " + f);
                    f.mActivity = mActivity;
                    f.mCalled = false;
                    f.onAttach(mActivity);
                    if (!f.mCalled) {
                        throw new SuperNotCalledException("Fragment " + f
                                + " did not call through to super.onAttach()");
                    }
                    
                    if (!f.mRetaining) {
                        f.mCalled = false;
                        f.onCreate(f.mSavedFragmentState);
                        if (!f.mCalled) {
                            throw new SuperNotCalledException("Fragment " + f
                                    + " did not call through to super.onCreate()");
                        }
                    }
                    f.mRetaining = false;
                    if (f.mFromLayout) {
                        // For fragments that are part of the content view
                        // layout, we need to instantiate the view immediately
                        // and the inflater will take care of adding it.
                        f.mView = f.onCreateView(mActivity.getLayoutInflater(),
                                null, f.mSavedFragmentState);
                        if (f.mView != null) {
                            f.mView.setSaveFromParentEnabled(false);
                            f.restoreViewState();
                            if (f.mHidden) f.mView.setVisibility(View.GONE); 
                        }
                    }
                case Fragment.CREATED:
                    if (newState > Fragment.CREATED) {
                        if (DEBUG) Log.v(TAG, "moveto CONTENT: " + f);
                        if (!f.mFromLayout) {
                            ViewGroup container = null;
                            if (f.mContainerId != 0) {
                                container = (ViewGroup)mActivity.findViewById(f.mContainerId);
                                if (container == null) {
                                    throw new IllegalArgumentException("New view found for id 0x"
                                            + Integer.toHexString(f.mContainerId)
                                            + " for fragment " + f);
                                }
                            }
                            f.mContainer = container;
                            f.mView = f.onCreateView(mActivity.getLayoutInflater(),
                                    container, f.mSavedFragmentState);
                            if (f.mView != null) {
                                f.mView.setSaveFromParentEnabled(false);
                                if (container != null) {
                                    Animation anim = loadAnimation(f, transit, true,
                                            transitionStyle);
                                    if (anim != null) {
                                        f.mView.setAnimation(anim);
                                    }
                                    container.addView(f.mView);
                                    f.restoreViewState();
                                }
                                if (f.mHidden) f.mView.setVisibility(View.GONE); 
                            }
                        }
                        
                        f.mCalled = false;
                        f.onReady(f.mSavedFragmentState);
                        if (!f.mCalled) {
                            throw new SuperNotCalledException("Fragment " + f
                                    + " did not call through to super.onReady()");
                        }
                        f.mSavedFragmentState = null;
                    }
                case Fragment.CONTENT:
                    if (newState > Fragment.CONTENT) {
                        if (DEBUG) Log.v(TAG, "moveto STARTED: " + f);
                        f.mCalled = false;
                        f.onStart();
                        if (!f.mCalled) {
                            throw new SuperNotCalledException("Fragment " + f
                                    + " did not call through to super.onStart()");
                        }
                    }
                case Fragment.STARTED:
                    if (newState > Fragment.STARTED) {
                        if (DEBUG) Log.v(TAG, "moveto RESUMED: " + f);
                        f.mCalled = false;
                        f.onResume();
                        if (!f.mCalled) {
                            throw new SuperNotCalledException("Fragment " + f
                                    + " did not call through to super.onResume()");
                        }
                    }
            }
        } else if (f.mState > newState) {
            switch (f.mState) {
                case Fragment.RESUMED:
                    if (newState < Fragment.RESUMED) {
                        if (DEBUG) Log.v(TAG, "movefrom RESUMED: " + f);
                        f.mCalled = false;
                        f.onPause();
                        if (!f.mCalled) {
                            throw new SuperNotCalledException("Fragment " + f
                                    + " did not call through to super.onPause()");
                        }
                    }
                case Fragment.STARTED:
                    if (newState < Fragment.STARTED) {
                        if (DEBUG) Log.v(TAG, "movefrom STARTED: " + f);
                        f.mCalled = false;
                        f.onStop();
                        if (!f.mCalled) {
                            throw new SuperNotCalledException("Fragment " + f
                                    + " did not call through to super.onStop()");
                        }
                    }
                case Fragment.CONTENT:
                    if (newState < Fragment.CONTENT) {
                        if (DEBUG) Log.v(TAG, "movefrom CONTENT: " + f);
                        if (f.mView != null) {
                            // Need to save the current view state if not
                            // done already.
                            if (!mActivity.isFinishing() && f.mSavedFragmentState == null) {
                                saveFragmentViewState(f);
                            }
                            if (f.mContainer != null) {
                                if (mCurState > Fragment.INITIALIZING) {
                                    Animation anim = loadAnimation(f, transit, false,
                                            transitionStyle);
                                    if (anim != null) {
                                        f.mView.setAnimation(anim);
                                    }
                                }
                                f.mContainer.removeView(f.mView);
                            }
                        }
                        f.mContainer = null;
                        f.mView = null;
                    }
                case Fragment.CREATED:
                    if (newState < Fragment.CREATED) {
                        if (DEBUG) Log.v(TAG, "movefrom CREATED: " + f);
                        if (!f.mRetaining) {
                            f.mCalled = false;
                            f.onDestroy();
                            if (!f.mCalled) {
                                throw new SuperNotCalledException("Fragment " + f
                                        + " did not call through to super.onDestroy()");
                            }
                        }
                        
                        f.mCalled = false;
                        f.onDetach();
                        if (!f.mCalled) {
                            throw new SuperNotCalledException("Fragment " + f
                                    + " did not call through to super.onDetach()");
                        }
                        f.mActivity = null;
                    }
            }
        }
        
        f.mState = newState;
    }
    
    void moveToState(int newState, boolean always) {
        moveToState(newState, 0, 0, always);
    }
    
    void moveToState(int newState, int transit, int transitStyle, boolean always) {
        if (mActivity == null && newState != Fragment.INITIALIZING) {
            throw new IllegalStateException("No activity");
        }
        
        if (!always && mCurState == newState) {
            return;
        }
        
        mCurState = newState;
        if (mActive != null) {
            for (int i=0; i<mActive.size(); i++) {
                Fragment f = mActive.get(i);
                if (f != null) {
                    moveToState(f, newState, transit, transitStyle);
                }
            }
        }
    }
    
    void makeActive(Fragment f) {
        if (f.mIndex >= 0) {
            return;
        }
        
        if (mAvailIndices == null || mAvailIndices.size() <= 0) {
            if (mActive == null) {
                mActive = new ArrayList<Fragment>();
            }
            f.setIndex(mActive.size());
            mActive.add(f);
            
        } else {
            f.setIndex(mAvailIndices.remove(mAvailIndices.size()-1));
            mActive.set(f.mIndex, f);
        }
    }
    
    void makeInactive(Fragment f) {
        if (f.mIndex < 0) {
            return;
        }
        
        mActive.set(f.mIndex, null);
        if (mAvailIndices == null) {
            mAvailIndices = new ArrayList<Integer>();
        }
        mAvailIndices.add(f.mIndex);
        f.clearIndex();
    }
    
    public void addFragment(Fragment fragment, boolean moveToStateNow) {
        if (DEBUG) Log.v(TAG, "add: " + fragment);
        if (mAdded == null) {
            mAdded = new ArrayList<Fragment>();
        }
        mAdded.add(fragment);
        makeActive(fragment);
        fragment.mAdded = true;
        if (moveToStateNow) {
            moveToState(fragment, mCurState, 0, 0);
        }
    }
    
    public void removeFragment(Fragment fragment, int transition, int transitionStyle) {
        if (DEBUG) Log.v(TAG, "remove: " + fragment);
        mAdded.remove(fragment);
        final boolean inactive = fragment.mBackStackNesting <= 0;
        if (inactive) {
            makeInactive(fragment);
        }
        fragment.mAdded = false;
        moveToState(fragment, inactive ? Fragment.INITIALIZING : Fragment.CREATED,
                transition, transitionStyle);
    }
    
    public void hideFragment(Fragment fragment, int transition, int transitionStyle) {
        if (DEBUG) Log.v(TAG, "hide: " + fragment);
        if (!fragment.mHidden) {
            fragment.mHidden = true;
            if (fragment.mView != null) {
                Animation anim = loadAnimation(fragment, transition, false,
                        transitionStyle);
                if (anim != null) {
                    fragment.mView.setAnimation(anim);
                }
                fragment.mView.setVisibility(View.GONE);
            }
            fragment.onHiddenChanged(true);
        }
    }
    
    public void showFragment(Fragment fragment, int transition, int transitionStyle) {
        if (DEBUG) Log.v(TAG, "show: " + fragment);
        if (fragment.mHidden) {
            fragment.mHidden = false;
            if (fragment.mView != null) {
                Animation anim = loadAnimation(fragment, transition, true,
                        transitionStyle);
                if (anim != null) {
                    fragment.mView.setAnimation(anim);
                }
                fragment.mView.setVisibility(View.VISIBLE);
            }
            fragment.onHiddenChanged(false);
        }
    }
    
    public Fragment findFragmentById(int id) {
        if (mActive != null) {
            // First look through added fragments.
            for (int i=mAdded.size()-1; i>=0; i--) {
                Fragment f = mAdded.get(i);
                if (f != null && f.mFragmentId == id) {
                    return f;
                }
            }
            // Now for any known fragment.
            for (int i=mActive.size()-1; i>=0; i--) {
                Fragment f = mActive.get(i);
                if (f != null && f.mFragmentId == id) {
                    return f;
                }
            }
        }
        return null;
    }
    
    public Fragment findFragmentByTag(String tag) {
        if (mActive != null && tag != null) {
            // First look through added fragments.
            for (int i=mAdded.size()-1; i>=0; i--) {
                Fragment f = mAdded.get(i);
                if (f != null && tag.equals(f.mTag)) {
                    return f;
                }
            }
            // Now for any known fragment.
            for (int i=mActive.size()-1; i>=0; i--) {
                Fragment f = mActive.get(i);
                if (f != null && tag.equals(f.mTag)) {
                    return f;
                }
            }
        }
        return null;
    }
    
    public Fragment findFragmentByWho(String who) {
        if (mActive != null && who != null) {
            for (int i=mActive.size()-1; i>=0; i--) {
                Fragment f = mActive.get(i);
                if (f != null && who.equals(f.mWho)) {
                    return f;
                }
            }
        }
        return null;
    }
    
    public void addBackStackState(BackStackEntry state) {
        if (mBackStack == null) {
            mBackStack = new ArrayList<BackStackEntry>();
        }
        mBackStack.add(state);
    }
    
    public boolean popBackStackState(Handler handler, String name) {
        if (mBackStack == null) {
            return false;
        }
        if (name == null) {
            int last = mBackStack.size()-1;
            if (last < 0) {
                return false;
            }
            final BackStackEntry bss = mBackStack.remove(last);
            handler.post(new Runnable() {
                public void run() {
                    bss.popFromBackStack();
                    moveToState(mCurState, reverseTransit(bss.getTransition()),
                            bss.getTransitionStyle(), true);
                }
            });
        } else {
            int index = mBackStack.size()-1;
            while (index >= 0) {
                BackStackEntry bss = mBackStack.get(index);
                if (name.equals(bss.getName())) {
                    break;
                }
            }
            if (index < 0 || index == mBackStack.size()-1) {
                return false;
            }
            final ArrayList<BackStackEntry> states
                    = new ArrayList<BackStackEntry>();
            for (int i=mBackStack.size()-1; i>index; i--) {
                states.add(mBackStack.remove(i));
            }
            handler.post(new Runnable() {
                public void run() {
                    for (int i=0; i<states.size(); i++) {
                        states.get(i).popFromBackStack();
                    }
                    moveToState(mCurState, true);
                }
            });
        }
        return true;
    }
    
    ArrayList<Fragment> retainNonConfig() {
        ArrayList<Fragment> fragments = null;
        if (mActive != null) {
            for (int i=0; i<mActive.size(); i++) {
                Fragment f = mActive.get(i);
                if (f != null && f.mRetainInstance) {
                    if (fragments == null) {
                        fragments = new ArrayList<Fragment>();
                    }
                    fragments.add(f);
                    f.mRetaining = true;
                }
            }
        }
        return fragments;
    }
    
    void saveFragmentViewState(Fragment f) {
        if (f.mView == null) {
            return;
        }
        if (mStateArray == null) {
            mStateArray = new SparseArray<Parcelable>();
        }
        f.mView.saveHierarchyState(mStateArray);
        if (mStateArray.size() > 0) {
            f.mSavedViewState = mStateArray;
            mStateArray = null;
        }
    }
    
    Parcelable saveAllState() {
        if (mActive == null || mActive.size() <= 0) {
            return null;
        }
        
        // First collect all active fragments.
        int N = mActive.size();
        FragmentState[] active = new FragmentState[N];
        boolean haveFragments = false;
        for (int i=0; i<N; i++) {
            Fragment f = mActive.get(i);
            if (f != null) {
                haveFragments = true;
                
                FragmentState fs = new FragmentState(f);
                active[i] = fs;
                
                if (mStateBundle == null) {
                    mStateBundle = new Bundle();
                }
                f.onSaveInstanceState(mStateBundle);
                if (!mStateBundle.isEmpty()) {
                    fs.mSavedFragmentState = mStateBundle;
                    mStateBundle = null;
                }
                
                if (f.mView != null) {
                    saveFragmentViewState(f);
                    if (f.mSavedViewState != null) {
                        if (fs.mSavedFragmentState == null) {
                            fs.mSavedFragmentState = new Bundle();
                        }
                        fs.mSavedFragmentState.putSparseParcelableArray(
                                FragmentState.VIEW_STATE_TAG, f.mSavedViewState);
                    }
                }
                
            }
        }
        
        if (!haveFragments) {
            return null;
        }
        
        int[] added = null;
        BackStackState[] backStack = null;
        
        // Build list of currently added fragments.
        N = mAdded.size();
        if (N > 0) {
            added = new int[N];
            for (int i=0; i<N; i++) {
                added[i] = mAdded.get(i).mIndex;
            }
        }
        
        // Now save back stack.
        if (mBackStack != null) {
            N = mBackStack.size();
            if (N > 0) {
                backStack = new BackStackState[N];
                for (int i=0; i<N; i++) {
                    backStack[i] = new BackStackState(this, mBackStack.get(i));
                }
            }
        }
        
        FragmentManagerState fms = new FragmentManagerState();
        fms.mActive = active;
        fms.mAdded = added;
        fms.mBackStack = backStack;
        return fms;
    }
    
    void restoreAllState(Parcelable state, ArrayList<Fragment> nonConfig) {
        // If there is no saved state at all, then there can not be
        // any nonConfig fragments either, so that is that.
        if (state == null) return;
        FragmentManagerState fms = (FragmentManagerState)state;
        if (fms.mActive == null) return;
        
        // First re-attach any non-config instances we are retaining back
        // to their saved state, so we don't try to instantiate them again.
        if (nonConfig != null) {
            for (int i=0; i<nonConfig.size(); i++) {
                Fragment f = nonConfig.get(i);
                FragmentState fs = fms.mActive[f.mIndex];
                fs.mInstance = f;
                f.mSavedViewState = null;
                f.mBackStackNesting = 0;
                f.mAdded = false;
                if (fs.mSavedFragmentState != null) {
                    f.mSavedViewState = fs.mSavedFragmentState.getSparseParcelableArray(
                            FragmentState.VIEW_STATE_TAG);
                }
            }
        }
        
        // Build the full list of active fragments, instantiating them from
        // their saved state.
        mActive = new ArrayList<Fragment>(fms.mActive.length);
        if (mAvailIndices != null) {
            mAvailIndices.clear();
        }
        for (int i=0; i<fms.mActive.length; i++) {
            FragmentState fs = fms.mActive[i];
            if (fs != null) {
                mActive.add(fs.instantiate(mActivity));
            } else {
                mActive.add(null);
                if (mAvailIndices == null) {
                    mAvailIndices = new ArrayList<Integer>();
                }
                mAvailIndices.add(i);
            }
        }
        
        // Build the list of currently added fragments.
        if (fms.mAdded != null) {
            mAdded = new ArrayList<Fragment>(fms.mAdded.length);
            for (int i=0; i<fms.mAdded.length; i++) {
                Fragment f = mActive.get(fms.mAdded[i]);
                if (f == null) {
                    throw new IllegalStateException(
                            "No instantiated fragment for index #" + fms.mAdded[i]);
                }
                f.mAdded = true;
                mAdded.add(f);
            }
        } else {
            mAdded = null;
        }
        
        // Build the back stack.
        if (fms.mBackStack != null) {
            mBackStack = new ArrayList<BackStackEntry>(fms.mBackStack.length);
            for (int i=0; i<fms.mBackStack.length; i++) {
                BackStackEntry bse = fms.mBackStack[i].instantiate(this);
                mBackStack.add(bse);
            }
        } else {
            mBackStack = null;
        }
    }
    
    public void attachActivity(Activity activity) {
        if (mActivity != null) throw new IllegalStateException();
        mActivity = activity;
    }
    
    public void dispatchCreate() {
        moveToState(Fragment.CREATED, false);
    }
    
    public void dispatchStart() {
        moveToState(Fragment.STARTED, false);
    }
    
    public void dispatchResume() {
        moveToState(Fragment.RESUMED, false);
    }
    
    public void dispatchPause() {
        moveToState(Fragment.STARTED, false);
    }
    
    public void dispatchStop() {
        moveToState(Fragment.CONTENT, false);
    }
    
    public void dispatchDestroy() {
        moveToState(Fragment.INITIALIZING, false);
        mActivity = null;
    }
    
    public static int reverseTransit(int transit) {
        int rev = 0;
        switch (transit) {
            case FragmentTransaction.TRANSIT_ENTER:
                rev = FragmentTransaction.TRANSIT_EXIT;
                break;
            case FragmentTransaction.TRANSIT_EXIT:
                rev = FragmentTransaction.TRANSIT_ENTER;
                break;
            case FragmentTransaction.TRANSIT_SHOW:
                rev = FragmentTransaction.TRANSIT_HIDE;
                break;
            case FragmentTransaction.TRANSIT_HIDE:
                rev = FragmentTransaction.TRANSIT_SHOW;
                break;
            case FragmentTransaction.TRANSIT_ACTIVITY_OPEN:
                rev = FragmentTransaction.TRANSIT_ACTIVITY_CLOSE;
                break;
            case FragmentTransaction.TRANSIT_ACTIVITY_CLOSE:
                rev = FragmentTransaction.TRANSIT_ACTIVITY_OPEN;
                break;
            case FragmentTransaction.TRANSIT_TASK_OPEN:
                rev = FragmentTransaction.TRANSIT_TASK_CLOSE;
                break;
            case FragmentTransaction.TRANSIT_TASK_CLOSE:
                rev = FragmentTransaction.TRANSIT_TASK_OPEN;
                break;
            case FragmentTransaction.TRANSIT_TASK_TO_FRONT:
                rev = FragmentTransaction.TRANSIT_TASK_TO_BACK;
                break;
            case FragmentTransaction.TRANSIT_TASK_TO_BACK:
                rev = FragmentTransaction.TRANSIT_TASK_TO_FRONT;
                break;
            case FragmentTransaction.TRANSIT_WALLPAPER_OPEN:
                rev = FragmentTransaction.TRANSIT_WALLPAPER_CLOSE;
                break;
            case FragmentTransaction.TRANSIT_WALLPAPER_CLOSE:
                rev = FragmentTransaction.TRANSIT_WALLPAPER_OPEN;
                break;
            case FragmentTransaction.TRANSIT_WALLPAPER_INTRA_OPEN:
                rev = FragmentTransaction.TRANSIT_WALLPAPER_INTRA_CLOSE;
                break;
            case FragmentTransaction.TRANSIT_WALLPAPER_INTRA_CLOSE:
                rev = FragmentTransaction.TRANSIT_WALLPAPER_INTRA_OPEN;
                break;
        }
        return rev;
        
    }
    
    public static int transitToStyleIndex(int transit, boolean enter) {
        int animAttr = -1;
        switch (transit) {
            case FragmentTransaction.TRANSIT_ENTER:
                animAttr = com.android.internal.R.styleable.WindowAnimation_windowEnterAnimation;
                break;
            case FragmentTransaction.TRANSIT_EXIT:
                animAttr = com.android.internal.R.styleable.WindowAnimation_windowExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_SHOW:
                animAttr = com.android.internal.R.styleable.WindowAnimation_windowShowAnimation;
                break;
            case FragmentTransaction.TRANSIT_HIDE:
                animAttr = com.android.internal.R.styleable.WindowAnimation_windowHideAnimation;
                break;
            case FragmentTransaction.TRANSIT_ACTIVITY_OPEN:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_activityOpenEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_activityOpenExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_ACTIVITY_CLOSE:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_activityCloseEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_activityCloseExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_TASK_OPEN:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_taskOpenEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_taskOpenExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_TASK_CLOSE:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_taskCloseEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_taskCloseExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_TASK_TO_FRONT:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_taskToFrontEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_taskToFrontExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_TASK_TO_BACK:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_taskToBackEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_taskToBackExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_WALLPAPER_OPEN:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_wallpaperOpenEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_wallpaperOpenExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_WALLPAPER_CLOSE:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_wallpaperCloseEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_wallpaperCloseExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_WALLPAPER_INTRA_OPEN:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_wallpaperIntraOpenEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_wallpaperIntraOpenExitAnimation;
                break;
            case FragmentTransaction.TRANSIT_WALLPAPER_INTRA_CLOSE:
                animAttr = enter
                        ? com.android.internal.R.styleable.WindowAnimation_wallpaperIntraCloseEnterAnimation
                        : com.android.internal.R.styleable.WindowAnimation_wallpaperIntraCloseExitAnimation;
                break;
        }
        return animAttr;
    }
}
