package com.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.location.LocationManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.location.*
import com.gpsspeed.Data
import com.gpsspeed.GpsServices
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private var googleApiClient: GoogleApiClient? = null
    internal val REQUEST_LOCATION = 199
    private var mGpsService:GpsServices?=null
    private var onGpsServiceUpdate: Data.onGpsServiceUpdate? = null
    internal var data: Data? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mGpsService = GpsServices()
        if (data == null) {
            data = Data(onGpsServiceUpdate)
        } else {
            data?.setOnGpsServiceUpdate(onGpsServiceUpdate)
        }

        //TODO please check location permission programatically
        // it is remains here

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (gpsEnabled) {
            startGpsService()
        } else {
            enableLoc()
        }

        onGpsServiceUpdate = Data.onGpsServiceUpdate {
            var currentSpeed = data?.curSpeed
            txtSpeed.text = currentSpeed.toString()+" km/h"
        }
    }

    private fun enableLoc() {
        // TODO Add this Dependency
        // compile 'com.google.android.gms:play-services-location:7.+'

        if (googleApiClient == null) {
            googleApiClient = GoogleApiClient.Builder(this@MainActivity)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                    override fun onConnected(p0: Bundle?) {
                        startGpsService()
                    }

                    override fun onConnectionSuspended(i: Int) {
                        googleApiClient?.connect()
                    }
                })
                .addOnConnectionFailedListener(object : GoogleApiClient.OnConnectionFailedListener {
                    override fun onConnectionFailed(connectionResult: ConnectionResult) {
                        Log.d("Location error", "Location error " + connectionResult.getErrorCode())
                    }
                }).build()
            googleApiClient?.connect() as GoogleApiClient?

            val locationRequest = LocationRequest.create()
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            locationRequest.setInterval(30 * 1000)
            locationRequest.setFastestInterval(5 * 1000)
            val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)

            builder.setAlwaysShow(true)

            val result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build())
            result.setResultCallback(object : ResultCallback<LocationSettingsResult> {
                override fun onResult(result: LocationSettingsResult) {
                    val status = result.getStatus()

                    Log.e("keshav", "status Called  -->" + status.getStatusCode())

                    when (status.getStatusCode()) {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                            Log.e("keshav", "LocationSettingsStatusCodes.RESOLUTION_REQUIRED Called ....")
                            try {
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                status.startResolutionForResult(this@MainActivity, REQUEST_LOCATION)
                            } catch (e: IntentSender.SendIntentException) {
                                // Ignore the error.
                            }

                        }
                    }
                }
            })
        }
    }

    //start gps servide as per android version oreo and lower
    private fun startGpsService() {

        //channel created for gps notification channel
        createchannel()

        val myIntent = Intent(this@MainActivity, GpsServices::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(myIntent)
        } else {
            startService(myIntent)
        }
    }

    /**
     * for API 26+ create notification channels
     */
    private fun createchannel() { //in=0 for gps and in=1 for call state
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val mChannel: NotificationChannel

            mChannel = NotificationChannel("gps_channel", "Gps_Speed", //name of the channel
                NotificationManager.IMPORTANCE_LOW
            )   //importance level

            //important level: default is is high on the phone.  high is urgent on the phone.  low is medium, so none is low?
            // Configure the notification channel.
            //            mChannel.setDescription(getString(R.string.channel_description));
            mChannel.enableLights(true)
            // Sets the notification light color for notifications posted to this channel, if the device supports this feature.
            mChannel.setShowBadge(true)
            nm.createNotificationChannel(mChannel)
        }
    }
}
