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

import android.content.ComponentCallbacks;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

final class FragmentState implements Parcelable {
    static final String VIEW_STATE_TAG = "android:view_state";
    
    final String mClassName;
    final int mIndex;
    final boolean mFromLayout;
    final int mFragmentId;
    final int mContainerId;
    final String mTag;
    final boolean mRetainInstance;
    
    Bundle mSavedFragmentState;
    
    Fragment mInstance;
    
    public FragmentState(Fragment frag) {
        mClassName = frag.getClass().getName();
        mIndex = frag.mIndex;
        mFromLayout = frag.mFromLayout;
        mFragmentId = frag.mFragmentId;
        mContainerId = frag.mContainerId;
        mTag = frag.mTag;
        mRetainInstance = frag.mRetainInstance;
    }
    
    public FragmentState(Parcel in) {
        mClassName = in.readString();
        mIndex = in.readInt();
        mFromLayout = in.readInt() != 0;
        mFragmentId = in.readInt();
        mContainerId = in.readInt();
        mTag = in.readString();
        mRetainInstance = in.readInt() != 0;
        mSavedFragmentState = in.readBundle();
    }
    
    public Fragment instantiate(Activity activity) {
        if (mInstance != null) {
            return mInstance;
        }
        
        try {
            mInstance = Fragment.instantiate(activity, mClassName);
        } catch (Exception e) {
            throw new RuntimeException("Unable to restore fragment " + mClassName, e);
        }
        
        if (mSavedFragmentState != null) {
            mSavedFragmentState.setClassLoader(activity.getClassLoader());
            mInstance.mSavedFragmentState = mSavedFragmentState;
            mInstance.mSavedViewState
                    = mSavedFragmentState.getSparseParcelableArray(VIEW_STATE_TAG);
        }
        mInstance.setIndex(mIndex);
        mInstance.mFromLayout = mFromLayout;
        mInstance.mFragmentId = mFragmentId;
        mInstance.mContainerId = mContainerId;
        mInstance.mTag = mTag;
        mInstance.mRetainInstance = mRetainInstance;
        
        return mInstance;
    }
    
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mClassName);
        dest.writeInt(mIndex);
        dest.writeInt(mFromLayout ? 1 : 0);
        dest.writeInt(mFragmentId);
        dest.writeInt(mContainerId);
        dest.writeString(mTag);
        dest.writeInt(mRetainInstance ? 1 : 0);
        dest.writeBundle(mSavedFragmentState);
    }
    
    public static final Parcelable.Creator<FragmentState> CREATOR
            = new Parcelable.Creator<FragmentState>() {
        public FragmentState createFromParcel(Parcel in) {
            return new FragmentState(in);
        }
        
        public FragmentState[] newArray(int size) {
            return new FragmentState[size];
        }
    };
}

/**
 * A Fragment is a piece of an application's user interface or behavior
 * that can be placed in an {@link Activity}.
 */
public class Fragment implements ComponentCallbacks {
    private static final HashMap<String, Class> sClassMap =
            new HashMap<String, Class>();
    
    static final int INITIALIZING = 0;  // Not yet created.
    static final int CREATED = 1;       // Created.
    static final int CONTENT = 2;       // View hierarchy content available.
    static final int STARTED = 3;       // Created and started, not resumed.
    static final int RESUMED = 4;       // Created started and resumed.
    
    int mState = INITIALIZING;
    
    // When instantiated from saved state, this is the saved state.
    Bundle mSavedFragmentState;
    SparseArray<Parcelable> mSavedViewState;
    
    // Index into active fragment array.
    int mIndex = -1;
    
    // Internal unique name for this fragment;
    String mWho;
    
    // True if the fragment is in the list of added fragments.
    boolean mAdded;
    
    // Set to true if this fragment was instantiated from a layout file.
    boolean mFromLayout;
    
    // Number of active back stack entries this fragment is in.
    int mBackStackNesting;
    
    // Activity this fragment is attached to.
    Activity mActivity;
    
    // The optional identifier for this fragment -- either the container ID if it
    // was dynamically added to the view hierarchy, or the ID supplied in
    // layout.
    int mFragmentId;
    
    // When a fragment is being dynamically added to the view hierarchy, this
    // is the identifier of the parent container it is being added to.
    int mContainerId;
    
    // The optional named tag for this fragment -- usually used to find
    // fragments that are not part of the layout.
    String mTag;
    
    // Set to true when the app has requested that this fragment be hidden
    // from the user.
    boolean mHidden;
    
    // If set this fragment would like its instance retained across
    // configuration changes.
    boolean mRetainInstance;
    
    // If set this fragment is being retained across the current config change.
    boolean mRetaining;
    
    // Used to verify that subclasses call through to super class.
    boolean mCalled;
    
    // If app has requested a specific animation, this is the one to use.
    int mNextAnim;
    
    // The parent container of the fragment after dynamically added to UI.
    ViewGroup mContainer;
    
    // The View generated for this fragment.
    View mView;
    
    public Fragment() {
    }

    static Fragment instantiate(Activity activity, String fname)
            throws NoSuchMethodException, ClassNotFoundException,
            IllegalArgumentException, InstantiationException,
            IllegalAccessException, InvocationTargetException {
        Class clazz = sClassMap.get(fname);

        if (clazz == null) {
            // Class not found in the cache, see if it's real, and try to add it
            clazz = activity.getClassLoader().loadClass(fname);
            sClassMap.put(fname, clazz);
        }
        return (Fragment)clazz.newInstance();
    }
    
    void restoreViewState() {
        if (mSavedViewState != null) {
            mView.restoreHierarchyState(mSavedViewState);
            mSavedViewState = null;
        }
    }
    
    void setIndex(int index) {
        mIndex = index;
        mWho = "android:fragment:" + mIndex;
   }
    
    void clearIndex() {
        mIndex = -1;
        mWho = null;
    }
    
    /**
     * Subclasses can not override equals().
     */
    @Override final public boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * Subclasses can not override hashCode().
     */
    @Override final public int hashCode() {
        return super.hashCode();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("Fragment{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        if (mIndex >= 0) {
            sb.append(" #");
            sb.append(mIndex);
        }
        if (mFragmentId != 0) {
            sb.append(" id=0x");
            sb.append(Integer.toHexString(mFragmentId));
        }
        if (mTag != null) {
            sb.append(" ");
            sb.append(mTag);
        }
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * Return the identifier this fragment is known by.  This is either
     * the android:id value supplied in a layout or the container view ID
     * supplied when adding the fragment.
     */
    public int getId() {
        return mFragmentId;
    }
    
    /**
     * Get the tag name of the fragment, if specified.
     */
    public String getTag() {
        return mTag;
    }
    
    /**
     * Return the Activity this fragment is currently associated with.
     */
    public Activity getActivity() {
        return mActivity;
    }
    
    /**
     * Return true if the fragment is currently added to its activity.
     */
    public boolean isAdded() {
        return mActivity != null && mActivity.mFragments.mAdded.contains(this);
    }
    
    /**
     * Return true if the fragment is currently visible to the user.  This means
     * it: (1) has been added, (2) has its view attached to the window, and 
     * (3) is not hidden.
     */
    public boolean isVisible() {
        return isAdded() && !isHidden() && mView != null
                && mView.getWindowToken() != null && mView.getVisibility() == View.VISIBLE;
    }
    
    /**
     * Return true if the fragment has been hidden.  By default fragments
     * are shown.  You can find out about changes to this state with
     * {@link #onHiddenChanged}.  Note that the hidden state is orthogonal
     * to other states -- that is, to be visible to the user, a fragment
     * must be both started and not hidden.
     */
    public boolean isHidden() {
        return mHidden;
    }
    
    /**
     * Called when the hidden state (as returned by {@link #isHidden()} of
     * the fragment has changed.  Fragments start out not hidden; this will
     * be called whenever the fragment changes state from that.
     * @param hidden True if the fragment is now hidden, false if it is not
     * visible.
     */
    public void onHiddenChanged(boolean hidden) {
    }
    
    /**
     * Control whether a fragment instance is retained across Activity
     * re-creation (such as from a configuration change).  This can only
     * be used with fragments not in the back stack.  If set, the fragment
     * lifecycle will be slightly different when an activity is recreated:
     * <ul>
     * <li> {@link #onDestroy()} will not be called (but {@link #onDetach()} still
     * will be, because the fragment is being detached from its current activity).
     * <li> {@link #onCreate(Bundle)} will not be called since the fragment
     * is not being re-created.
     * <li> {@link #onAttach(Activity)} and {@link #onReady(Bundle)} <b>will</b>
     * still be called.
     * </ul>
     */
    public void setRetainInstance(boolean retain) {
        mRetainInstance = retain;
    }
    
    public boolean getRetainInstance() {
        return mRetainInstance;
    }
    
    /**
     * Call {@link Activity#startActivity(Intent)} on the fragment's
     * containing Activity.
     */
    public void startActivity(Intent intent) {
        mActivity.startActivityFromFragment(this, intent, -1);
    }
    
    /**
     * Call {@link Activity#startActivityForResult(Intent, int)} on the fragment's
     * containing Activity.
     */
    public void startActivityForResult(Intent intent, int requestCode) {
        mActivity.startActivityFromFragment(this, intent, requestCode);
    }
    
    /**
     * Receive the result from a previous call to
     * {@link #startActivityForResult(Intent, int)}.  This follows the
     * related Activity API as described there in
     * {@link Activity#onActivityResult(int, int, Intent)}.
     * 
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode The integer result code returned by the child activity
     *                   through its setResult().
     * @param data An Intent, which can return result data to the caller
     *               (various data can be attached to Intent "extras").
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }
    
    /**
     * Called when a fragment is being created as part of a view layout
     * inflation, typically from setting the content view of an activity.  This
     * will be called both the first time the fragment is created, as well
     * later when it is being re-created from its saved state (which is also
     * given here).
     * 
     * XXX This is kind-of yucky...  maybe we could just supply the
     * AttributeSet to onCreate()?
     * 
     * @param activity The Activity that is inflating the fragment.
     * @param attrs The attributes at the tag where the fragment is
     * being created.
     * @param savedInstanceState If the fragment is being re-created from
     * a previous saved state, this is the state.
     */
    public void onInflate(Activity activity, AttributeSet attrs,
            Bundle savedInstanceState) {
        mCalled = true;
    }
    
    /**
     * Called when a fragment is first attached to its activity.
     * {@link #onCreate(Bundle)} will be called after this.
     */
    public void onAttach(Activity activity) {
        mCalled = true;
    }
    
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        return null;
    }
    
    /**
     * Called to do initial creation of a fragment.  This is called after
     * {@link #onAttach(Activity)} and before {@link #onReady(Bundle)}.
     * @param savedInstanceState If the fragment is being re-created from
     * a previous saved state, this is the state.
     */
    public void onCreate(Bundle savedInstanceState) {
        mCalled = true;
    }
    
    /**
     * Called to have the fragment instantiate its user interface view.
     * This is optional, and non-graphical fragments can return null (which
     * is the default implementation).  This will be called between
     * {@link #onCreate(Bundle)} and {@link #onReady(Bundle)}.
     * 
     * @param inflater The LayoutInflater object that can be used to inflate
     * any views in the fragment,
     * @param container If non-null, this is the parent view that the fragment's
     * UI should be attached to.  The fragment should not add the view itself,
     * but this can be used to generate the LayoutParams of the view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     * 
     * @return Return the View for the fragment's UI, or null.
     */
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return null;
    }
    
    public View getView() {
        return mView;
    }
    
    /**
     * Called when the activity is ready for the fragment to run.  This is
     * most useful for fragments that use {@link #setRetainInstance(boolean)}
     * instance, as this tells the fragment when it is fully associated with
     * the new activity instance.  This is called after {@link #onCreate(Bundle)}
     * and before {@link #onStart()}.
     * 
     * @param savedInstanceState If the fragment is being re-created from
     * a previous saved state, this is the state.
     */
    public void onReady(Bundle savedInstanceState) {
        mCalled = true;
    }
    
    /**
     * Called when the Fragment is visible to the user.  This is generally
     * tied to {@link Activity#onStart() Activity.onStart} of the containing
     * Activity's lifecycle.
     */
    public void onStart() {
        mCalled = true;
    }
    
    /**
     * Called when the fragment is visible to the user and actively running.
     * This is generally
     * tied to {@link Activity#onResume() Activity.onResume} of the containing
     * Activity's lifecycle.
     */
    public void onResume() {
        mCalled = true;
    }
    
    public void onSaveInstanceState(Bundle outState) {
    }
    
    public void onConfigurationChanged(Configuration newConfig) {
        mCalled = true;
    }
    
    /**
     * Called when the Fragment is no longer resumed.  This is generally
     * tied to {@link Activity#onPause() Activity.onPause} of the containing
     * Activity's lifecycle.
     */
    public void onPause() {
        mCalled = true;
    }
    
    /**
     * Called when the Fragment is no longer started.  This is generally
     * tied to {@link Activity#onStop() Activity.onStop} of the containing
     * Activity's lifecycle.
     */
    public void onStop() {
        mCalled = true;
    }
    
    public void onLowMemory() {
        mCalled = true;
    }
    
    /**
     * Called when the fragment is no longer in use.  This is called
     * after {@link #onStop()} and before {@link #onDetach()}.
     */
    public void onDestroy() {
        mCalled = true;
    }

    /**
     * Called when the fragment is no longer attached to its activity.  This
     * is called after {@link #onDestroy()}.
     */
    public void onDetach() {
        mCalled = true;
    }
}
