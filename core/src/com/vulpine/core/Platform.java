package com.vulpine.core;

public final class Platform {
    private static Boolean android;

    public static boolean isAndroid() {
        if (android == null)
            android = String.valueOf(System.getProperty("java.vm.vendor")).contains("Android") || String.valueOf(System.getProperty("java.vendor")).contains("Android");
        return android;
    }
}
