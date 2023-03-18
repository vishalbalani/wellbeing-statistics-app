package com.wellbeingstatistics.android;

import android.Manifest;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    UsageStatsManager mUsageStatsManager;
    CardUsageList mUsageListAdapter;
    RecyclerView mRecyclerView;
    RecyclerView.LayoutManager mLayoutManager;
    boolean granted = false;

    private static final String TAG = "MainActivity";
    private static final String SYSTEM_PACKAGE_NAME = "android";

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private double mLatitude;
    private double mLongitude;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        double[] latLng = retrieveLocation();
        mLatitude = latLng[0];
        mLatitude = latLng[1];


        AppOpsManager appOps = (AppOpsManager) this
                .getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), this.getPackageName());

        if (mode == AppOpsManager.MODE_DEFAULT) {
            granted = (this.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED);

            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
            granted = (mode == AppOpsManager.MODE_ALLOWED);

            init();

            StatsUsageInterval statsUsageInterval = StatsUsageInterval
                    .getValue("Monthly");
            if (statsUsageInterval != null) {
                List<UsageStats> usageStatsList =
                        getUsageStatistics(statsUsageInterval.mInterval);
                Collections.sort(usageStatsList, new LastTimeLaunchedComparatorDesc());
                updateAppsList(usageStatsList);
            }
        }
    }

//    -----------------------------Location------------------

    public double[] retrieveLocation() {
        double[] latLng = new double[2];

        // Create a LocationListener to handle location updates
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
//                Log.d("Location", "Latitude: " + latitude + ", Longitude: " + longitude);

                // Save the latitude and longitude values to the latLng array
                latLng[0] = latitude;
                latLng[1] = longitude;

                locationManager.removeUpdates(this);
            }

            @Override
            public void onProviderDisabled(String provider) {
                // Called when the user disables the location provider.
            }

            @Override
            public void onProviderEnabled(String provider) {
                // Called when the user enables the location provider.
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // Called when the status of the location provider changes.
            }
        };

        // Request location updates from the network provider
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return latLng;
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                0, 0, locationListener);

        // Wait for a location update before returning the latitude and longitude values
        Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (lastKnownLocation != null) {
            latLng[0] = lastKnownLocation.getLatitude();
            latLng[1] = lastKnownLocation.getLongitude();
        }

        return latLng;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // If the user granted permission, retrieve the location

                retrieveLocation();
            } else {
                // If the user denied permission, show a message explaining why we need it
                Toast.makeText(this, "Location permission is required to use this feature",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop requesting location updates when the activity is destroyed
        locationManager.removeUpdates(locationListener);
    }

//    ----------------------------Location END------------------

    public void init() {
        mUsageStatsManager = (UsageStatsManager) this
                .getSystemService(Context.USAGE_STATS_SERVICE); //Context.USAGE_STATS_SERVICE

        mUsageListAdapter = new CardUsageList();
        mRecyclerView = findViewById(R.id.recyclerview_app_usage);
        mLayoutManager = mRecyclerView.getLayoutManager();
        mRecyclerView.scrollToPosition(0);
        mRecyclerView.setAdapter(mUsageListAdapter);
    }


    public List<UsageStats> getUsageStatistics(int intervalType) {
        // Get the app statistics since one year ago from the current time.
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -1);

        List<UsageStats> queryUsageStats = mUsageStatsManager
                .queryUsageStats(intervalType, cal.getTimeInMillis(),
                        System.currentTimeMillis());

        Log.d(TAG, "start: " + cal.getTimeInMillis() + " end: " + System.currentTimeMillis());

        if (queryUsageStats.size() == 0) {
            Log.i(TAG, "The user may not allow the access to apps usage. ");
            Toast.makeText(this,
                    getString(R.string.explanation_access_to_appusage_is_not_enabled),
                    Toast.LENGTH_LONG).show();
        }
        return queryUsageStats;
    }

    void updateAppsList(List<UsageStats> usageStatsList) {
        List<XUsageStats> customUsageStatsList = new ArrayList<>();
        for (int i = 0; i < usageStatsList.size(); i++) {
            XUsageStats customUsageStats = new XUsageStats();
            customUsageStats.usageStats = usageStatsList.get(i);
            String packageName = customUsageStats.usageStats.getPackageName();

            if (isSystemApp(packageName)) {
                continue;
            }

            if (packageName.equals(BuildConfig.APPLICATION_ID)) {
                continue;
            }

            try {
                Drawable appIcon = this.getPackageManager()
                        .getApplicationIcon(packageName);
                customUsageStats.appIcon = appIcon;
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, String.format("App Icon is not found for %s",
                        customUsageStats.usageStats.getPackageName()));
                customUsageStats.appIcon = this
                        .getDrawable(R.drawable.ic_launcher_foreground);
            }

            PackageManager packageManager = getApplicationContext().getPackageManager();
            String appName=packageName;

            ApplicationInfo applicationInfo;
            try {
                applicationInfo = packageManager.getApplicationInfo(packageName, 0);
                appName=(String) packageManager.getApplicationLabel(applicationInfo);
            } catch (PackageManager.NameNotFoundException e) {
                applicationInfo = null;
            }
            customUsageStats.appName = appName;

            customUsageStatsList.add(customUsageStats);
        }
        mUsageListAdapter.setCustomUsageStatsList(customUsageStatsList);
        mUsageListAdapter.notifyDataSetChanged();
        mRecyclerView.scrollToPosition(0);

        double[] latLng = retrieveLocation();
        Log.d("LocationOther: ", "Latitude: " + latLng[0] + ", Longitude: " + latLng[1]);

    }

    public boolean isSystemApp(String packageName) {
        try {
            // Get packageinfo for target application
            PackageManager mPackageManager = this.getPackageManager();
            PackageInfo targetPkgInfo = mPackageManager.getPackageInfo(
                    packageName, PackageManager.GET_SIGNATURES);
            // Get packageinfo for system package
            PackageInfo sys = mPackageManager.getPackageInfo(
                    SYSTEM_PACKAGE_NAME, PackageManager.GET_SIGNATURES);
            // Match both packageinfo for there signatures
            return (targetPkgInfo != null && targetPkgInfo.signatures != null && sys.signatures[0]
                    .equals(targetPkgInfo.signatures[0]));
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * The {@link Comparator} to sort a collection of {@link UsageStats} sorted by the timestamp
     * last time the app was used in the descendant order.
     */
    private static class LastTimeLaunchedComparatorDesc implements Comparator<UsageStats> {

        @Override
        public int compare(UsageStats left, UsageStats right) {
            return Long.compare(right.getLastTimeUsed(), left.getLastTimeUsed());
        }
    }

    static enum StatsUsageInterval {
        DAILY("Daily", UsageStatsManager.INTERVAL_DAILY),
        WEEKLY("Weekly", UsageStatsManager.INTERVAL_WEEKLY),
        MONTHLY("Monthly", UsageStatsManager.INTERVAL_MONTHLY),
        YEARLY("Yearly", UsageStatsManager.INTERVAL_YEARLY);

        private int mInterval;
        private String mStringRepresentation;

        StatsUsageInterval(String stringRepresentation, int interval) {
            mStringRepresentation = stringRepresentation;
            mInterval = interval;
        }

        static StatsUsageInterval getValue(String stringRepresentation) {
            for (StatsUsageInterval statsUsageInterval : values()) {
                if (statsUsageInterval.mStringRepresentation.equals(stringRepresentation)) {
                    return statsUsageInterval;
                }
            }
            return null;
        }
    }
    private void currentLoc(){
        double[] latLng = new double[2];
        latLng = retrieveLocation();
        mLatitude=latLng[0];
        mLongitude=latLng[1];
    }
}