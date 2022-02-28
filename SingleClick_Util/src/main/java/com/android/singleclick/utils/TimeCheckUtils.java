package com.android.singleclick.utils;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

public class TimeCheckUtils {

    public static long FROZEN_WINDOW_MILLIS = 600L;

    public static final String TAG = TimeCheckUtils.class.getSimpleName();

    public static final Map<View, FrozenView> viewWeakHashMap = new WeakHashMap<>();

    public static boolean checkTime(View targetView) {

        FrozenView frozenView = viewWeakHashMap.get(targetView);
        final long now = now();

        if (frozenView == null) {
            frozenView = new FrozenView(targetView);
            frozenView.setFrozenWindow(now + FROZEN_WINDOW_MILLIS);
            viewWeakHashMap.put(targetView, frozenView);
            return true;
        }

        if (now >= frozenView.getFrozenWindowTime()) {
            frozenView.setFrozenWindow(now + FROZEN_WINDOW_MILLIS);
            return true;
        }

        return false;
    }

    private static long now() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private static class FrozenView extends WeakReference<View> {
        private long FrozenWindowTime;

        FrozenView(View referent) {
            super(referent);
        }

        long getFrozenWindowTime() {
            return FrozenWindowTime;
        }

        void setFrozenWindow(long expirationTime) {
            this.FrozenWindowTime = expirationTime;
        }
    }

}
