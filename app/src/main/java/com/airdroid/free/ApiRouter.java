package com.airdroid.free;

import android.content.Context;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Routes /api/* requests to the right provider and returns JSON.
 * This is the boundary between the web dashboard and the device capabilities.
 */
class ApiRouter {

    private final DeviceInfoProvider deviceInfo;
    private final LocationProvider location;
    private final FileBrowserProvider files;
    private final SmsProvider sms;
    private final ClipboardProvider clipboard;
    private final NotificationStore notifications;

    ApiRouter(Context ctx) {
        this.deviceInfo = new DeviceInfoProvider(ctx);
        this.location = new LocationProvider(ctx);
        this.files = new FileBrowserProvider();
        this.sms = new SmsProvider(ctx);
        this.clipboard = new ClipboardProvider(ctx);
        this.notifications = NotificationStore.get();
    }

    NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, NanoHTTPD.Method method, String uri) {
        // strip /api prefix and trailing slash
        String path = uri.substring(4); // remove "/api"
        if (path.startsWith("/")) path = path.substring(1);

        try {
            switch (path) {
                case "info":
                    return WebServer.json(200, deviceInfo.getDeviceInfo());
                case "location":
                    return WebServer.json(200, location.getLastLocationJson());
                case "files":
                    return WebServer.json(200, files.list(WebServer.getParam(session, "path")));
                case "sms":
                    return WebServer.json(200, sms.listSms(50));
                case "clipboard":
                    return WebServer.json(200, clipboard.get());
                case "notifications":
                    return WebServer.json(200, notifications.toJson(50));
                case "ping":
                    return WebServer.json(200, "{\"pong\":true}");
                default:
                    return WebServer.json(404, "{\"error\":\"unknown endpoint /api/" + path + "\"}");
            }
        } catch (SecurityException se) {
            return WebServer.json(403, "{\"error\":\"permission not granted: "
                    + WebServer.escape(se.getMessage()) + "\"}");
        } catch (Exception e) {
            return WebServer.json(500, "{\"error\":\"" + WebServer.escape(e.getMessage()) + "\"}");
        }
    }
}
