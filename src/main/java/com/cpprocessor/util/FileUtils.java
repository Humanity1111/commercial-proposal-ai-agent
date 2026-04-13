package com.cpprocessor.util;

public final class FileUtils {

    private FileUtils() {}

    public static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
