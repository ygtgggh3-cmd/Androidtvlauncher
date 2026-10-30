package com.perrinho.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

/** Detección de memorias (USB, SD, interna) y tipos de archivo multimedia. */
final class Storage {
    static final int NONE = 0;
    static final int VIDEO = 1;
    static final int AUDIO = 2;
    static final int IMAGE = 3;

    static final class Vol {
        String label;
        File dir;
        boolean usb;
    }

    private static final HashSet<String> VIDEOS = new HashSet<String>(Arrays.asList(
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "mpg", "mpeg", "ts", "flv", "wmv", "vob"));
    private static final HashSet<String> AUDIOS = new HashSet<String>(Arrays.asList(
            "mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "wma", "mid"));
    private static final HashSet<String> IMAGES = new HashSet<String>(Arrays.asList(
            "jpg", "jpeg", "png", "webp", "bmp", "gif"));

    private Storage() {
    }

    static int kind(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        if (i < 0 || i == n.length() - 1) {
            return NONE;
        }
        String ext = n.substring(i + 1).toLowerCase(Locale.ROOT);
        if (VIDEOS.contains(ext)) {
            return VIDEO;
        }
        if (AUDIOS.contains(ext)) {
            return AUDIO;
        }
        if (IMAGES.contains(ext)) {
            return IMAGE;
        }
        return NONE;
    }

    static boolean hasPermission(Context c) {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /** Memorias USB/SD primero y, al final, el almacenamiento interno. */
    static ArrayList<Vol> volumes(Context c) {
        ArrayList<Vol> out = new ArrayList<Vol>();
        HashSet<String> seen = new HashSet<String>();
        String[] bases = {
                "/storage", "/mnt/media_rw", "/mnt/usb_storage", "/mnt/usb", "/mnt/usbhost",
                "/mnt/sda", "/mnt/sdb", "/mnt/external_sd", "/mnt/extsd", "/mnt/sdcard2"
        };
        String usbLabel = c.getString(R.string.usb);
        for (String base : bases) {
            File[] kids = new File(base).listFiles();
            if (kids == null) {
                continue;
            }
            for (File k : kids) {
                String name = k.getName();
                if (!k.isDirectory() || name.equals("emulated") || name.equals("self")
                        || name.equals("sdcard0") || name.equals("sdcard")
                        || name.equals("knox-emulated") || name.equals("container")) {
                    continue;
                }
                if (!seen.add(name) || k.listFiles() == null) {
                    continue;
                }
                Vol v = new Vol();
                v.label = usbLabel + " · " + name;
                v.dir = k;
                v.usb = true;
                out.add(v);
            }
        }
        File internal = Environment.getExternalStorageDirectory();
        if (internal != null && internal.listFiles() != null) {
            Vol v = new Vol();
            v.label = c.getString(R.string.internal_storage);
            v.dir = internal;
            v.usb = false;
            out.add(v);
        }
        return out;
    }
}
