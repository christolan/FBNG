package com.xiaojiwei.fbng.game;

import android.util.Log;

/**
 * Reads the aspect LibretroDroid 0.13.2 is actually fitting.
 * The value comes from retro AV info, then from SET_GEOMETRY when the core
 * sends a new one. Null means the probe could not read it; callers use 4:3.
 */
public final class CoreGeometry {
    private static final String TAG = "FBNG";
    private static final boolean LOADED;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("libretrodroid");
            System.loadLibrary("fbng_aspect");
            loaded = true;
        } catch (UnsatisfiedLinkError error) {
            Log.w(TAG, "core aspect probe unavailable", error);
        }
        LOADED = loaded;
    }

    private CoreGeometry() {}

    public static Float reportedAspectRatio() {
        if (!LOADED) {
            return null;
        }
        float value = nativeAspectRatio();
        if (!Float.isFinite(value) || value <= 0f) {
            return null;
        }
        return value;
    }

    private static native float nativeAspectRatio();
}
