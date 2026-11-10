package com.example.core.system;

import android.os.Build;
import androidx.annotation.NonNull;

/**
 * Native Java Hardware Bridge for ALEX AI.
 * Provides low-level Java runtime & Android OS ABI inspection alongside the Kotlin layer.
 */
public final class NativeJavaHardwareBridge {

    private NativeJavaHardwareBridge() {
        // Utility class
    }

    @NonNull
    public static String getPrimaryAbi() {
        String[] abis = Build.SUPPORTED_ABIS;
        return (abis != null && abis.length > 0) ? abis[0] : "arm64-v8a";
    }
}
