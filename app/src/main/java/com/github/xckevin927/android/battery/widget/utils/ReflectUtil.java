package com.github.xckevin927.android.battery.widget.utils;

import android.content.Context;

import java.lang.reflect.Method;

import me.weishu.reflection.Reflection;

public class ReflectUtil {

    private static boolean sInjectSuccess = false;

    public static void init(Context context) {
        try {
            int result = Reflection.unseal(context);
            sInjectSuccess = result == 0;
        } catch (Throwable t) {
            sInjectSuccess = false;
        }
    }

    public static <T> T invoke(Object obj, String method, Class<?>[] parameters, Object...args) {
        if (sInjectSuccess) {
            try {
                return (T) obj.getClass().getDeclaredMethod(method, parameters).invoke(obj, args);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            try {
                Method metaGetDeclaredMethod = Class.class.getDeclaredMethod("getDeclardMethod");
                Method hiddenMethod = (Method) metaGetDeclaredMethod.invoke(obj.getClass(), method, parameters);
                return (T) hiddenMethod.invoke(obj, args);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
