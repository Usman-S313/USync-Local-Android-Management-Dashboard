package com.airdroid.free;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tiny in-memory ring buffer that NotificationListener writes into and
 * the /api/notifications endpoint reads from. Keeps the last 100 entries.
 */
public class NotificationStore {

    private static final int MAX = 100;
    private static final NotificationStore INSTANCE = new NotificationStore();
    private final Deque<JSONObject> queue = new ArrayDeque<>();

    private NotificationStore() {}

    public static NotificationStore get() {
        return INSTANCE;
    }

    void add(String json) {
        try {
            JSONObject o = new JSONObject(json);
            synchronized (queue) {
                queue.push(o);
                while (queue.size() > MAX) queue.removeLast();
            }
        } catch (Exception ignored) {
        }
    }

    String toJson(int limit) {
        JSONArray arr = new JSONArray();
        synchronized (queue) {
            int n = 0;
            for (JSONObject o : queue) {
                arr.put(o);
                if (++n >= limit) break;
            }
        }
        JSONObject res = new JSONObject();
        try {
            res.put("count", arr.length());
            res.put("items", arr);
        } catch (Exception ignored) {
        }
        return res.toString();
    }
}
