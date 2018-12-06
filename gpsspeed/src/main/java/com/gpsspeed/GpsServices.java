package com.gpsspeed;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.*;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GpsServices extends Service implements LocationListener, GpsStatus.Listener {
    Data data;
    private LocationManager mLocationManager;
    private SharedPreferences sharedPreferences;
    private Data.onGpsServiceUpdate onGpsServiceUpdate;
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    public  String gps_notification = "gps_channel";

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (data == null) {
            data = new Data(onGpsServiceUpdate);
        } else {
            data.setOnGpsServiceUpdate(onGpsServiceUpdate);
        }

        gpsListener();

        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (mLocationManager.getAllProviders().indexOf(LocationManager.GPS_PROVIDER) >= 0) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
        } else {
            Log.w("SideMenuActivity", "No GPS location provider found. GPS data display will not be available.");
        }

        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Gps not enabled", Toast.LENGTH_SHORT).show();
        }

    }

    public void onLocationChanged(Location location) {

        Gson gson = new Gson();
        String json = sharedPreferences.getString("data", "");
        data = gson.fromJson(json, Data.class);

        if (data == null) {
            data = new Data(onGpsServiceUpdate);
        } else {
            data.setOnGpsServiceUpdate(onGpsServiceUpdate);
        }

        String speed = String.format(Locale.ENGLISH, "%.0f", location.getSpeed() * 3.6);

        Toast.makeText(this, speed, Toast.LENGTH_SHORT).show();

        Log.e("PhonoisRunningFalse", speed);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    private void gpsListener() {
        onGpsServiceUpdate = new Data.onGpsServiceUpdate() {
            @Override
            public void update() {
//                double maxSpeedTemp = data.getMaxSpeed();
//                double distanceTemp = data.getDistance();
//                double averageTemp;
//                if (sharedPreferences.getBoolean("auto_average", false)) {
//                    averageTemp = data.getAverageSpeedMotion();
//                } else {
//                    averageTemp = data.getAverageSpeed();
//                }
//
//                String speedUnits;
//                String distanceUnits;
//                if (sharedPreferences.getBoolean("miles_per_hour", false)) {
//                    maxSpeedTemp *= 0.62137119;
//                    distanceTemp = distanceTemp / 1000.0 * 0.62137119;
//                    averageTemp *= 0.62137119;
//                    speedUnits = "mi/h";
//                    distanceUnits = "mi";
//                } else {
//                    speedUnits = "km/h";
//                    if (distanceTemp <= 1000.0) {
//                        distanceUnits = "m";
//                    } else {
//                        distanceTemp /= 1000.0;
//                        distanceUnits = "km";
//                    }
//                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;//needed for stop.

        if (intent != null) {
            msg.setData(intent.getExtras());
            mServiceHandler.sendMessage(msg);
        } else {
            Toast.makeText(GpsServices.this, "The Intent to start is null?!", Toast.LENGTH_SHORT).show();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    /* Remove the locationlistener updates when Services is stopped */
    @Override
    public void onDestroy() {
        mLocationManager.removeUpdates(this);
        mLocationManager.removeGpsStatusListener(this);
        stopForeground(true);
    }

    @Override
    public void onGpsStatusChanged(int event) {
        switch (event) {
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                @SuppressLint("MissingPermission") GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
                int satsInView = 0;
                int satsUsed = 0;
                Iterable<GpsSatellite> sats = gpsStatus.getSatellites();
                for (GpsSatellite sat : sats) {
                    satsInView++;
                    if (sat.usedInFix()) {
                        satsUsed++;
                    }
                }

                if (satsUsed == 0) {
                    data.setRunning(false);
                    stopService(new Intent(getBaseContext(), GpsServices.class));
//                    firstfix = true;
                }
                break;

            case GpsStatus.GPS_EVENT_STOPPED:
                if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Toast.makeText(this, "Gps not enabled.", Toast.LENGTH_SHORT).show();
                }
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                break;
        }
    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            //promote to foreground and create persistent notification.
            //in Oreo we only have a few seconds to do this or the service is killed.
            Notification notification = getNotification("Phono is running");
            startForeground(msg.arg1, notification);  //not sure what the ID needs to be.
            // Normally we would do some work here, like download a file.
            // For our example, we just sleep for 5 seconds then display toasts.
            //setup how many messages
            int times = 1, i;

            Bundle extras = msg.getData();
            if (extras != null) {
                times = 1000*60*60*24;  //default is one
            }
            //loop that many times, sleeping for 5 seconds.
            for (i = 0; i < times; i++) {
                synchronized (this) {
                    try {
                        wait(5000); //5 second sleep
                    } catch (InterruptedException e) {
                    }
                }
                String info = i + "GPS SPEED LOG";
                Log.d("intentServer", info);
                //make a toast
                //unable to ensure the toasts will always show, so use a handler and post it for later.
                // Toast.makeText(MyForeGroundService.this, info, Toast.LENGTH_SHORT).show();
            }
            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
//            stopSelf(msg.arg1);  //notification will go away as well.
        }
    }

    // build a persistent notification and return it.
    public Notification getNotification(String message) {

        return new NotificationCompat.Builder(getApplicationContext(), gps_notification)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)  //persistent notification!
                .setChannelId(gps_notification)
                .setContentTitle("Gps Service")   //Title message top row.
                .setContentText(message)  //message when looking at the notification, second row
                .build();  //finally build and return a Notification.
    }
}
