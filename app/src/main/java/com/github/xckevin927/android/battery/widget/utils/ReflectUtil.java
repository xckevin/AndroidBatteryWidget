package com.github.xckevin927.android.battery.widget.utils;

import android.os.Build;

import androidx.annotation.RequiresApi;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Method;

public class ReflectUtil {

    private static volatile boolean sHiddenApiAccessEnabled = false;
    private static volatile boolean sBypassClassAvailable = false;
    private static boolean sInitialized = false;

    public static synchronized void init() {
        if (sInitialized) return;
        sInitialized = true;
        // HiddenApiBypass requires Android 9; older releases permit ordinary reflection.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            sHiddenApiAccessEnabled = true;
            return;
        }
        try {
            sHiddenApiAccessEnabled = Api28.enableHiddenApiAccess();
            // Setting global exemptions may return false while exact method lookup still works.
            sBypassClassAvailable = true;
        } catch (RuntimeException | LinkageError error) {
            sHiddenApiAccessEnabled = false;
            sBypassClassAvailable = false;
            error.printStackTrace();
        }
    }

    public static boolean isHiddenApiAccessEnabled() {
        return sHiddenApiAccessEnabled;
    }

    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object obj, String method, Class<?>[] parameters, Object...args) {
        try {
            Method target;
            try {
                // Available methods keep working even if global exemptions could not be set.
                target = obj.getClass().getDeclaredMethod(method, parameters);
            } catch (NoSuchMethodException unavailable) {
                // Do not repeatedly enter an unavailable/failed library class for every device.
                if (!sBypassClassAvailable || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    return null;
                }
                target = Api28.getDeclaredMethod(obj.getClass(), method, parameters);
            }
            // Invoke only once. An exception thrown by the target must not cause a retry.
            return (T) target.invoke(obj, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            error.printStackTrace();
            return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private static final class Api28 {
        static boolean enableHiddenApiAccess() {
            // Preserve the app-wide access previously enabled during attachBaseContext.
            return HiddenApiBypass.setHiddenApiExemptions("");
        }

        static Method getDeclaredMethod(Class<?> type, String name, Class<?>[] parameters)
                throws NoSuchMethodException {
            // Exact parameter types preserve overload selection, including null arguments.
            return HiddenApiBypass.getDeclaredMethod(type, name, parameters);
        }
    }
}
