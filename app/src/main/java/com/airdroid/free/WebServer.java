package com.airdroid.free;

import android.content.Context;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.ResponseException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The embedded HTTP server. Serves a static web dashboard from assets/www
 * and exposes a REST API under /api/* that the dashboard JavaScript calls.
 *
 * Everything is plain HTTP on the local Wi-Fi only — this is intentionally
 * the same model used for local LAN transfer features.
 */
public class WebServer extends NanoHTTPD {

    private static final String TAG = "WebServer";
    public static final File UPLOAD_DIR = new File(
            android.os.Environment.getExternalStorageDirectory(), "USync");

    private final Context ctx;
    private final ApiRouter api;

    public WebServer(Context context, int port) {
        super(port);
        this.ctx = context.getApplicationContext();
        this.api = new ApiRouter(this.ctx);
        if (!UPLOAD_DIR.exists()) UPLOAD_DIR.mkdirs();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        try {
            // ---- REST API ----
            if (uri.startsWith("/api/")) {
                return api.handle(session, method, uri);
            }

            // ---- File download (raw bytes) ----
            if (uri.startsWith("/download")) {
                return serveDownload(session);
            }

            // ---- File upload (multipart POST) ----
            if (uri.equals("/upload") && method == Method.POST) {
                return handleUpload(session);
            }

            // ---- Static dashboard ----
            return serveStatic(uri);
        } catch (Exception e) {
            Log.e(TAG, "serve() error for " + uri, e);
            return json(500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    // ---------- Static dashboard files ----------

    private Response serveStatic(String uri) {
        String path = "/www/index.html".equals(uri) || uri.equals("/") ? "/www/index.html" : uri;
        if (uri.equals("/") || uri.isEmpty()) path = "/www/index.html";
        else path = "/www" + uri;

        try {
            InputStream is = ctx.getAssets().open(path.substring(1));
            String mime = guessMime(path);
            return newChunkedResponse(Response.Status.OK, mime, is);
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }
    }

    private String guessMime(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    // ---------- File download ----------

    private Response serveDownload(IHTTPSession session) {
        String pathParam = getParam(session, "path");
        if (pathParam == null || pathParam.isEmpty()) {
            return json(400, "{\"error\":\"missing path\"}");
        }
        File file = new File(pathParam);
        if (!file.exists() || file.isDirectory()) {
            return json(404, "{\"error\":\"file not found\"}");
        }
        try {
            InputStream fis = new FileInputStream(file);
            String fileName = file.getName();
            Response res = newChunkedResponse(Response.Status.OK, "application/octet-stream", fis);
            res.addHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            return res;
        } catch (FileNotFoundException e) {
            return json(404, "{\"error\":\"file not found\"}");
        }
    }

    // ---------- File upload ----------

    private Response handleUpload(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);

        String targetDirStr = getParam(session, "dir");
        File targetDir = (targetDirStr != null && !targetDirStr.isEmpty())
                ? new File(targetDirStr) : UPLOAD_DIR;

        if (!targetDir.exists()) {
            boolean created = targetDir.mkdirs();
            if (!created) {
                return json(500, "{\"error\":\"Could not create directory " + escape(targetDir.getAbsolutePath()) + ". Please check storage permissions on the phone.\"}");
            }
        }

        boolean ok = false;
        StringBuilder errorLog = new StringBuilder();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String tmpPath = entry.getValue();
            String targetName = getParam(session, "filename");
            if (targetName == null || targetName.isEmpty()) targetName = "uploaded_" + System.currentTimeMillis();
            File target = new File(targetDir, targetName);
            File src = new File(tmpPath);
            try {
                if (src.exists()) {
                    copyFile(src, target);
                    ok = true;
                } else {
                    errorLog.append("Source file not found. ");
                }
            } catch (IOException e) {
                errorLog.append(e.getMessage()).append(" ");
            }
        }
        if (ok) return json(200, "{\"status\":\"Uploaded to " + escape(targetDir.getName()) + "\"}");
        return json(400, "{\"error\":\"" + escape(errorLog.toString()) + "\"}");
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    // ---------- helpers ----------

    static String getParam(IHTTPSession session, String name) {
        Map<String, List<String>> params = session.getParameters();
        List<String> v = params.get(name);
        return (v == null || v.isEmpty()) ? null : v.get(0);
    }

    static Response json(int status, String body) {
        return newFixedLengthResponse(Response.Status.lookup(status), "application/json", body);
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
