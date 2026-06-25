package com.airdroid.free;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

/** Provides the last known GPS location as JSON (used by the phone-finder map). */
class LocationProvider {

    private final Context ctx;
    private final FusedLocationProviderClient fused;

    LocationProvider(Context ctx) {
        this.ctx = ctx;
        this.fused = LocationServices.getFusedLocationProviderClient(ctx);
    }

    @SuppressLint("MissingPermission")
    String getLastLocationJson() {
        if (!hasLocationPermission()) {
            return "{\"error\":\"location permission not granted\"}";
        }
        try {
            // Try fused first
            Location loc = com.google.android.gms.tasks.Tasks.await(fused.getLastLocation());
            if (loc == null) {
                loc = fallbackToGps();
            }
            if (loc == null) {
                return "{\"error\":\"no location available yet\"}";
            }
            JSONObject o = new JSONObject();
            o.put("lat", loc.getLatitude());
            o.put("lng", loc.getLongitude());
            o.put("accuracy", Math.round(loc.getAccuracy()));
            o.put("time", loc.getTime());
            return o.toString();
        } catch (SecurityException e) {
            return "{\"error\":\"permission denied\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + WebServer.escape(e.getMessage()) + "\"}";
        }
    }

    private Location fallbackToGps() {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                return lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
