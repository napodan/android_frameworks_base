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
package android.webkit;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.ZoomButtonsController;

class ZoomControlEmbedded implements ZoomControlBase {

    private final ZoomManager mZoomManager;
    private final WebView mWebView;

    // The controller is lazily initialized in getControls() for performance.
    private ZoomButtonsController mZoomButtonsController;

    public ZoomControlEmbedded(ZoomManager zoomManager, WebView webView) {
        mZoomManager = zoomManager;
        mWebView = webView;
    }

    public void show() {
        if (!getControls().isVisible()
                && mZoomManager.mMinZoomScale < mZoomManager.mMaxZoomScale) {

            mZoomButtonsController.setVisible(true);

            WebSettings settings = mWebView.getSettings();
            int count = settings.getDoubleTapToastCount();
            if (mZoomManager.mInZoomOverview && count > 0) {
                settings.setDoubleTapToastCount(--count);
                Toast.makeText(mWebView.getContext(),
                        com.android.internal.R.string.double_tap_toast,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    public void hide() {
        if (mZoomButtonsController != null) {
            mZoomButtonsController.setVisible(false);
        }
    }

    public boolean isVisible() {
        return mZoomButtonsController != null && mZoomButtonsController.isVisible();
    }

    public void update() {
        if (mZoomButtonsController == null) {
            return;
        }

        boolean canZoomIn = mWebView.getScale() < mZoomManager.mMaxZoomScale;
        boolean canZoomOut = mWebView.getScale() > mZoomManager.mMinZoomScale &&
                                                  !mZoomManager.mInZoomOverview;
        if (!canZoomIn && !canZoomOut) {
            // Hide the zoom in and out buttons if the page cannot zoom
            mZoomButtonsController.getZoomControls().setVisibility(View.GONE);
        } else {
            // Set each one individually, as a page may be able to zoom in or out
            mZoomButtonsController.setZoomInEnabled(canZoomIn);
            mZoomButtonsController.setZoomOutEnabled(canZoomOut);
        }
    }

    private ZoomButtonsController getControls() {
        if (mZoomButtonsController == null) {
            mZoomButtonsController = new ZoomButtonsController(mWebView);
            mZoomButtonsController.setOnZoomListener(new ZoomListener());
            // ZoomButtonsController positions the buttons at the bottom, but in
            // the middle. Change their layout parameters so they appear on the
            // right.
            View controls = mZoomButtonsController.getZoomControls();
            ViewGroup.LayoutParams params = controls.getLayoutParams();
            if (params instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) params).gravity = Gravity.RIGHT;
            }
        }
        return mZoomButtonsController;
    }

    private class ZoomListener implements ZoomButtonsController.OnZoomListener {

        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                mWebView.switchOutDrawHistory();
                // Bring back the hidden zoom controls.
                mZoomButtonsController.getZoomControls().setVisibility(View.VISIBLE);
                update();
            }
        }

        public void onZoom(boolean zoomIn) {
            if (zoomIn) {
                mWebView.zoomIn();
            } else {
                mWebView.zoomOut();
            }
            update();
        }
    }
}
