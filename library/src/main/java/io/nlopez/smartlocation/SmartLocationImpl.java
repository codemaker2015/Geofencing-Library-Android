package io.nlopez.smartlocation;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.Geofence;

import java.io.Console;
import java.util.ArrayList;
import java.util.List;

import io.nlopez.smartlocation.geofencing.model.GeofenceModel;
import io.nlopez.smartlocation.geofencing.utils.TransitionGeofence;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesProvider;

/**
 * Created by Vishnu on 15-07-2020.
 */

public class SmartLocationImpl extends Activity implements OnLocationUpdatedListener, OnActivityUpdatedListener, OnGeofencingTransitionListener {

    private LocationGooglePlayServicesProvider provider;

    private static final int LOCATION_PERMISSION_ID = 1001;
    private Location location;
    private SmartLocation smartLocation;
    private SmartLocationImpl smartLocationImpl;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Keep the screen always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        showLast(getApplicationContext());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_ID && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocation(this);
        }
    }

    private String[] showLast(final Context ctx) {
        Location lastLocation = SmartLocation.with(ctx).location().getLastLocation();
        String[] info = new String[3];
        if (lastLocation != null) {
            info[0] = String.format("[From Cache] Latitude %.6f, Longitude %.6f",
                            lastLocation.getLatitude(),
                            lastLocation.getLongitude());
        }

        DetectedActivity detectedActivity = SmartLocation.with(ctx).activity().getLastActivity();
        if (detectedActivity != null) {
            info[1] = String.format("[From Cache] Activity %s with %d%% confidence",
                            getNameFromType(detectedActivity),
                            detectedActivity.getConfidence());
        }
        return info;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (provider != null) {
            provider.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void startLocation(final Context ctx) {

        // Location permission not granted
//        if (ContextCompat.checkSelfPermission(SmartLocationImpl.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(SmartLocationImpl.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_ID);
//            return;
//        }

        provider = new LocationGooglePlayServicesProvider();
        provider.setCheckLocationSettings(true);

        smartLocation = new SmartLocation.Builder(ctx).logging(true).build();

        smartLocation.location(provider).start(this);
        smartLocation.activity().start(this);

        smartLocationImpl = new SmartLocationImpl();
        smartLocationImpl.location = SmartLocation.with(ctx).location().getLastLocation();
    }

    private String setGeofence(String id, int radius){
        // Create some geofences
        try {
            GeofenceModel mestalla = new GeofenceModel.Builder(id).setTransition(Geofence.GEOFENCE_TRANSITION_ENTER).setLatitude(smartLocationImpl.location.getLatitude()).setLongitude(smartLocationImpl.location.getLongitude()).setRadius(radius).build();
            smartLocation.geofencing().add(mestalla).start(this);
        }catch (Exception ex){
            return "Error occurs while creating geofence point. " + ex;
        }
        return "Geofence is created with id " + id;
    }

    private String[] stopLocation(final Context ctx) {
        String[] info = new String[3];
        SmartLocation.with(ctx).location().stop();
        info[0] = "Location stopped!";

        SmartLocation.with(ctx).activity().stop();
        info[1] = "Activity Recognition stopped!";

        SmartLocation.with(ctx).geofencing().stop();
        info[2] = "Geofencing stopped!";
        return info;
    }

    private String showLocation(Location location) {
        String info = new String();
        if (smartLocationImpl.location != null) {
            info = String.format("Latitude %.6f, Longitude %.6f",
                    smartLocationImpl.location.getLatitude(),
                    smartLocationImpl.location.getLongitude());
            final String text = info;
            // We are going to get the address for the current position
            SmartLocation.with(this).geocoding().reverse(smartLocationImpl.location, new OnReverseGeocodingListener() {
                @Override
                public void onAddressResolved(Location original, List<Address> results) {
                    if (results.size() > 0) {
                        Address result = results.get(0);
                        StringBuilder builder = new StringBuilder(text);
                        builder.append("\n[Reverse Geocoding] ");
                        List<String> addressElements = new ArrayList<>();
                        for (int i = 0; i <= result.getMaxAddressLineIndex(); i++) {
                            addressElements.add(result.getAddressLine(i));
                        }
                        builder.append(TextUtils.join(", ", addressElements));
                        Log.i("Reverse Geocoding", builder.toString());
                    }
                }
            });
        } else {
            info = "Null location";
        }
        return info;
    }

    private String showActivity(DetectedActivity detectedActivity) {
        String info;
        if (detectedActivity != null) {
            info = String.format("Activity %s with %d%% confidence",
                            getNameFromType(detectedActivity),
                            detectedActivity.getConfidence());
        } else {
            info = "Null activity";
        }
        return info;
    }

    private String showGeofence(Geofence geofence, int transitionType) {
        String info;
        if (geofence != null) {
           info = "Transition " + getTransitionNameFromType(transitionType) + " for Geofence with id = " + geofence.getRequestId();
        } else {
            info = "Null geofence";
        }
        return info;
    }

    @Override
    public void onLocationUpdated(Location location) {
        showLocation(location);
        smartLocationImpl.location = location;
    }

    @Override
    public void onActivityUpdated(DetectedActivity detectedActivity) {
        showActivity(detectedActivity);
    }

    @Override
    public void onGeofenceTransition(TransitionGeofence geofence) {
        showGeofence(geofence.getGeofenceModel().toGeofence(), geofence.getTransitionType());
    }

    private String getNameFromType(DetectedActivity activityType) {
        switch (activityType.getType()) {
            case DetectedActivity.IN_VEHICLE:
                return "in_vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "on_bicycle";
            case DetectedActivity.ON_FOOT:
                return "on_foot";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.TILTING:
                return "tilting";
            default:
                return "unknown";
        }
    }

    private String getTransitionNameFromType(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return "enter";
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return "exit";
            default:
                return "dwell";
        }
    }
}
