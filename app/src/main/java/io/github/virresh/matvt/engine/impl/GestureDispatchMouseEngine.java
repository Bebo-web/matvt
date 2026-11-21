package io.github.virresh.matvt.engine.impl;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

import io.github.virresh.matvt.helper.AppPreferences;
import io.github.virresh.matvt.services.MouseEventService;
import io.github.virresh.matvt.view.MouseCursorView;
import io.github.virresh.matvt.view.OverlayView;

public class GestureDispatchMouseEngine extends BaseEngine {


    private static String LOG_TAG = "GESTURE_MOUSE_EMULATION";
    private GestureDescription.StrokeDescription dragStroke;
    private boolean dragActive = false;
    private boolean dragPending = false;
    private PointF dragTarget;
    private PointF dragStrokeEnd;

    public GestureDispatchMouseEngine(AccessibilityService service, OverlayView ov, AppPreferences appPreferences, MouseCursorView mouseCursorView) {
        super(appPreferences, ov, mouseCursorView);
        this.mService = service;
    }

    @Override
    protected int scroll(KeyEvent ke) {
        if (mService == null || !scrollCodeMap.containsKey(ke.getKeyCode())) {
            return 0;
        }

        // Use the root input channel for scrolling, just like the Shell engine.
        // dispatchGesture can throw when a swipe endpoint crosses the display edge
        // (for example, DPAD_DOWN near the top of the screen). A root input swipe
        // produces a complete DOWN/MOVE/UP sequence and avoids that crash.
        int direction = scrollCodeMap.get(ke.getKeyCode());
        PointF pointer = mPointerControl.getPointerScreenLocation();
        android.util.DisplayMetrics metrics = mService.getResources().getDisplayMetrics();
        float maxX = Math.max(1, metrics.widthPixels - 1);
        float maxY = Math.max(1, metrics.heightPixels - 1);

        int distance = Math.max(120, Math.min(360, 220 + (scrollSpeed * 12)));
        float startX = Math.max(0, Math.min(maxX, pointer.x));
        float startY = Math.max(0, Math.min(maxY, pointer.y));
        float endX = startX;
        float endY = startY;

        switch (direction) {
            case PointerControl.UP:
                startY = Math.max(distance + 1, Math.min(maxY, startY));
                endY = startY - distance;
                break;
            case PointerControl.DOWN:
                startY = Math.min(maxY - distance, Math.max(0, startY));
                endY = startY + distance;
                break;
            case PointerControl.LEFT:
                startX = Math.max(distance + 1, Math.min(maxX, startX));
                endX = startX - distance;
                break;
            case PointerControl.RIGHT:
                startX = Math.min(maxX - distance, Math.max(0, startX));
                endX = startX + distance;
                break;
            default:
                return 0;
        }

        int duration = Math.max(80, Math.min(420, 360 - scrollSpeed * 35));
        PointF start = new PointF(startX, startY);
        PointF end = new PointF(endX, endY);
        Log.i(LOG_TAG, "Root scroll swipe " + start.x + "," + start.y
                + " -> " + end.x + "," + end.y + " duration=" + duration);
        dispatchRootShellSwipe(start, end, duration);
        return 1;
    }

    @Override
    protected int click(KeyEvent ke) {
        PointF pointer = mPointerControl.getPointerScreenLocation();
        if (!scrollCodeMap.containsKey(ke.getKeyCode())) {
            dispatchConfiguredClick();
        } else {
            int direction = scrollCodeMap.get(ke.getKeyCode());
            mService.dispatchGesture(createSwipe(pointer, direction, 100, scrollSpeed), gestureResultCallback, null);
        }
        return 1;
    }

    private AccessibilityService.GestureResultCallback gestureResultCallback = new AccessibilityService.GestureResultCallback() {
        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            super.onCompleted(gestureDescription);
            Log.i(LOG_TAG, "Dispatch Gesture Completed Succesfully! -- " + gestureDescription.getStrokeCount());
            if (dragPending) {
                dragPending = false;
                continueDrag();
            }
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            super.onCancelled(gestureDescription);
            Log.i(LOG_TAG, "Dispatch Gesture was cancelled! -- " + gestureDescription.getStrokeCount());
            dragActive = false;
            dragPending = false;
            dragStroke = null;
        }
    };

    @Override
    protected void onDragStart(PointF point) {
        if (mService == null || dragActive) return;
        dragActive = true;
        PointF screenPoint = mPointerControl.getPointerScreenLocation();
        dragTarget = new PointF(screenPoint.x, screenPoint.y);
        Path path = new Path();
        path.moveTo(screenPoint.x, screenPoint.y);
        path.lineTo(screenPoint.x + 1, screenPoint.y + 1);
        dragStrokeEnd = new PointF(screenPoint.x + 1, screenPoint.y + 1);
        dragStroke = new GestureDescription.StrokeDescription(path, 0, 100, true);
        dragPending = true;
        mService.dispatchGesture(new GestureDescription.Builder().addStroke(dragStroke).build(), gestureResultCallback, null);
    }

    @Override
    protected void onDragMove(PointF point) {
        if (dragActive) {
            PointF screenPoint = mPointerControl.getPointerScreenLocation();
            dragTarget = new PointF(screenPoint.x, screenPoint.y);
        }
    }

    @Override
    protected void onDragEnd(PointF point) {
        if (!dragActive) return;
        PointF screenPoint = mPointerControl.getPointerScreenLocation();
        dragTarget = new PointF(screenPoint.x, screenPoint.y);
        dragActive = false;
        if (!dragPending) continueDrag();
    }

    private void continueDrag() {
        if (mService == null || dragStroke == null || dragTarget == null) return;
        PointF target = new PointF(dragTarget.x, dragTarget.y);
        Path path = new Path();
        path.moveTo(dragStrokeEnd.x, dragStrokeEnd.y);
        if (Math.abs(target.x - dragStrokeEnd.x) < 1 && Math.abs(target.y - dragStrokeEnd.y) < 1) {
            target = new PointF(dragStrokeEnd.x + 1, dragStrokeEnd.y);
        }
        path.lineTo(target.x, target.y);
        try {
            GestureDescription.StrokeDescription next = dragStroke.continueStroke(path, 0, 100, dragActive);
            dragStroke = next;
            dragStrokeEnd = new PointF(target.x, target.y);
            dragPending = true;
            mService.dispatchGesture(new GestureDescription.Builder().addStroke(next).build(), gestureResultCallback, null);
        } catch (IllegalStateException exception) {
            Log.w(LOG_TAG, "Could not continue drag gesture", exception);
            dragStroke = null;
            dragPending = false;
        }
    }

    private static GestureDescription createClick (PointF clickPoint, long duration) {
        final int DURATION = 1 + (int) duration;
        Path clickPath = new Path();
        clickPath.moveTo(clickPoint.x, clickPoint.y);
        // A short explicit segment is more reliable than a moveTo-only path on some ROMs.
        clickPath.lineTo(clickPoint.x + 1f, clickPoint.y + 1f);
        GestureDescription.StrokeDescription clickStroke =
                new GestureDescription.StrokeDescription(clickPath, 0, Math.max(40, DURATION));
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }

    private static GestureDescription createSwipe (PointF originPoint, int direction, int momentum, int scrollSpeed) {
        final int DURATION = scrollSpeed + 8;
        Path clickPath = new Path();
        PointF lineDirection = new PointF(originPoint.x + momentum * PointerControl.dirX[direction], originPoint.y + momentum * PointerControl.dirY[direction]);
        clickPath.moveTo(originPoint.x, originPoint.y);
        clickPath.lineTo(lineDirection.x, lineDirection.y);
        GestureDescription.StrokeDescription clickStroke =
                new GestureDescription.StrokeDescription(clickPath, 0, DURATION);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }

    /**
     * Send input via Android's gestureAPI
     * Only sends swipes
     * see {@link #createClick(PointF, long)} for clicking at a point
     * @param originPoint
     * @param direction
     */
    private void attachGesture (final PointF originPoint, final int direction) {
        if (previousRunnable != null) {
            detachPreviousTimer();
        }
        previousRunnable = new Runnable() {
            @Override
            public void run() {
                mPointerControl.reappear();
                mService.dispatchGesture(createSwipe(originPoint, direction, 20 + momentumStack, scrollSpeed), gestureResultCallback, null);
                momentumStack += 1;
                timerHandler.postDelayed(this, 30);
            }
        };
        timerHandler.postDelayed(previousRunnable, 0);
    }

    private List<AccessibilityNodeInfo> findNode (AccessibilityNodeInfo node, int action, Point pInt) {
        if (node == null) {
            node = mService.getRootInActiveWindow();
        }
        List<AccessibilityNodeInfo> nodeInfos = new ArrayList<>();
        if (node != null) {
            findNodeHelper(node, action, pInt, nodeInfos);
        }
        return nodeInfos;
    }

    private AccessibilityNodeInfo findNodeHelper (AccessibilityNodeInfo node, int action, Point pInt, List<AccessibilityNodeInfo> nodeList) {
        if (node == null) {
            return null;
        }
        Rect tmp = new Rect();
        node.getBoundsInScreen(tmp);
        if (!tmp.contains(pInt.x, pInt.y)) {
            // node doesn't contain cursor
            return null;
        }
        // node contains cursor, add to node hierarchy
        nodeList.add(node);
        AccessibilityNodeInfo result = node;
        int childCount = node.getChildCount();
        for (int i=0; i<childCount; i++) {
            AccessibilityNodeInfo child = findNodeHelper(node.getChild(i), action, pInt, nodeList);
            if (child != null) {
                // always picks the last innermost clickable child
                result = child;
            }
        }
        return result;
    }
}