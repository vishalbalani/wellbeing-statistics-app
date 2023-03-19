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
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private DateFormat mDateFormat = new SimpleDateFormat();

    FirebaseFirestore db = FirebaseFirestore.getInstance();
    private double mLatitude;
    private double mLongitude;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

//        currentLoc();


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

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {


            } else {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,}, 1);
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            }
        }
    }

    private String getAddressFromLocation(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                String addressString = address.getAddressLine(0);
                return addressString;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "";

    }

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
            long lastTimeUsed = customUsageStats.usageStats.getLastTimeUsed();
            if (isSystemApp(packageName)) {
                continue;
            }

            if (packageName.equals(BuildConfig.APPLICATION_ID)) {
                continue;
            }

            Map<String, Object> user = new HashMap<>();
            user.put("Package Name:", packageName);

            user.put("Last Used: ", mDateFormat.format(new Date(lastTimeUsed)));

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
            user.put("App Name:", appName);
            customUsageStats.openLoc = retrieveLocation();
//            user.put("Location", retrieveLocation());
            db.collection("user").add(user);

            customUsageStatsList.add(customUsageStats);

        }
        mUsageListAdapter.setCustomUsageStatsList(customUsageStatsList);
        mUsageListAdapter.notifyDataSetChanged();
        mRecyclerView.scrollToPosition(0);


//        Log.d("LocationOther: ", "Latitude: " + latLng[0] + ", Longitude: " + latLng[1]);

    }

    public boolean isSystemApp(String packageName) {
        try {
            PackageManager mPackageManager = this.getPackageManager();
            PackageInfo targetPkgInfo = mPackageManager.getPackageInfo(
                    packageName, PackageManager.GET_SIGNATURES);
            PackageInfo sys = mPackageManager.getPackageInfo(
                    SYSTEM_PACKAGE_NAME, PackageManager.GET_SIGNATURES);
            // Match both packageinfo for there signatures
            return (targetPkgInfo != null && targetPkgInfo.signatures != null && sys.signatures[0]
                    .equals(targetPkgInfo.signatures[0]));
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }


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
        Map<String, Object> loc = new HashMap<>();
        loc.put("Latitude:", latLng[0]);
        loc.put("Longitude:", latLng[1]);
        db.collection("Loc").add(loc);

    }
}