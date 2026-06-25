package com.airdroid.free;

import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** Lists files & directories so the dashboard can browse the phone's storage. */
class FileBrowserProvider {

    String list(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            path = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        File dir = new File(path);
        
        // Ensure path is valid
        if (!dir.exists()) {
             // Fallback to SD card root if path disappears
             dir = Environment.getExternalStorageDirectory();
        }
        
        JSONArray arr = new JSONArray();
        if (!dir.isDirectory()) {
            return "{\"error\":\"Not a directory\"}";
        }
        File[] children = dir.listFiles();
        if (children != null) {
            // directories first, then files, alpha-sorted
            java.util.Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : children) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("name", f.getName());
                    o.put("path", f.getAbsolutePath());
                    o.put("dir", f.isDirectory());
                    if (!f.isDirectory()) o.put("size", f.length());
                    o.put("hidden", f.isHidden());
                    arr.put(o);
                } catch (Exception ignored) {
                }
            }
        }
        JSONObject res = new JSONObject();
        try {
            res.put("path", dir.getAbsolutePath());
            res.put("parent", dir.getParent());
            res.put("files", arr);
        } catch (Exception ignored) {
        }
        return res.toString();
    }
}
