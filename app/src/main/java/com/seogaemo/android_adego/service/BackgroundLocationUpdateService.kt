package com.seogaemo.android_adego.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import com.seogaemo.android_adego.R
import com.seogaemo.android_adego.data.Location
import com.seogaemo.android_adego.database.TokenManager
import com.seogaemo.android_adego.network.RetrofitAPI
import com.seogaemo.android_adego.network.RetrofitClient
import com.seogaemo.android_adego.view.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class BackgroundLocationUpdateService : Service() {

    private lateinit var context: Context
    private var stopService = false

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationCallback: LocationCallback
    private lateinit var mLocationRequest: LocationRequest

    private var latitude = "0.0"
    private var longitude = "0.0"

    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    override fun onCreate() {
        super.onCreate()
        context = this
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initializeLocationCallback()
        initializeLocationRequest()
        handler = Handler()
        runnable = Runnable {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        startLocationUpdates()

        val delayMillis = TimeUnit.HOURS.toMillis(1)
        handler.postDelayed(runnable, delayMillis)

        val toastHandler = Handler(Looper.getMainLooper())
        val toastRunnable: Runnable = object : Runnable {
            override fun run() {
                try {
                    if (!stopService) {
                        CoroutineScope(Dispatchers.Main).launch {
                            updateLocation(lat = latitude, lng = longitude)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (!stopService) {
                        toastHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(10))
                    }
                }
            }
        }
        toastHandler.postDelayed(toastRunnable, 2000)

        return START_STICKY
    }

    override fun onDestroy() {
        stopService = true
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
        val CHANNEL_ID = "channel_location"
        val CHANNEL_NAME = "Location Updates"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ADEGO")
            .setContentText("약속 지각 방지 솔루션 ADEGO")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.image_logo)
            .setColor(resources.getColor(R.color.black))
            .setBadgeIconType(R.drawable.image_logo)
            .setAutoCancel(true)

        val notification: Notification = builder.build()
        startForeground(101, notification)
    }

    private fun initializeLocationRequest() {
        mLocationRequest = LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()
    }

    private fun initializeLocationCallback() {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation
                latitude = location?.latitude.toString()
                longitude = location?.longitude.toString()
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
        }
    }

    private fun stopLocationUpdates() {
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
        } catch (e: SecurityException) {
        }
    }

    suspend fun updateLocation(lat: String, lng: String): Boolean? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.updateLocation("bearer ${TokenManager.accessToken}", Location(lat, lng))
                if (response.isSuccessful) {
                    response.body()
                }else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

}
