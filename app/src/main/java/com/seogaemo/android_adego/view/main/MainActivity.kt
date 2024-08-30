package com.seogaemo.android_adego.view.main

import android.Manifest
import android.app.ActivityManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.seogaemo.android_adego.R
import com.seogaemo.android_adego.data.Location
import com.seogaemo.android_adego.data.PlanResponse
import com.seogaemo.android_adego.data.PlanStatus
import com.seogaemo.android_adego.data.UserResponse
import com.seogaemo.android_adego.database.PlanViewModel
import com.seogaemo.android_adego.database.TokenManager
import com.seogaemo.android_adego.databinding.ActiveViewBinding
import com.seogaemo.android_adego.databinding.ActivityMainBinding
import com.seogaemo.android_adego.databinding.DisabledViewBinding
import com.seogaemo.android_adego.databinding.NoPromiseViewBinding
import com.seogaemo.android_adego.network.RetrofitAPI
import com.seogaemo.android_adego.network.RetrofitClient
import com.seogaemo.android_adego.service.BackgroundLocationUpdateService
import com.seogaemo.android_adego.util.Util.copyToClipboard
import com.seogaemo.android_adego.util.Util.getLink
import com.seogaemo.android_adego.util.Util.isActiveDate
import com.seogaemo.android_adego.util.Util.isDateEnd
import com.seogaemo.android_adego.util.Util.leavePlan
import com.seogaemo.android_adego.util.Util.parseDateTime
import com.seogaemo.android_adego.view.alarm.AlarmActivity
import com.seogaemo.android_adego.view.auth.LoginActivity
import com.seogaemo.android_adego.view.plan.PlanActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {
    private lateinit var binding: ActivityMainBinding

    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    private val planViewModel: PlanViewModel by viewModels()
    private val combinedLiveData = MediatorLiveData<Pair<PlanStatus?, PlanResponse?>>()

    private var userImageMap = mutableMapOf<String, String>()

    private val loginReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_LOGIN_REQUIRED") {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finishAffinity()
            }
        }
    }
    private val timeUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                val planStatus = planViewModel.planStatus.value
                val planDate = planViewModel.planDate.value.toString()

                when(planStatus) {
                    PlanStatus.ACTIVE -> {
                        if (isDateEnd(planDate)) {
                            lifecycleScope.launch { leavePlan(this@MainActivity) }
                            planViewModel.setPlanStatus(true)
                        } else {
                            startMyServiceIfNotRunning(this@MainActivity)
                        }
                    }
                    PlanStatus.DISABLED -> {
                        if (isActiveDate(planDate)) {
                            planViewModel.setPlanStatus(false)
                        } else {
                            findViewById<TextView>(R.id.next_text).text = calculateDifference(planDate)
                        }
                    }
                    PlanStatus.NO_PROMISE -> {}
                    null -> {}
                }
            }
        }
    }
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { permissions[Manifest.permission.POST_NOTIFICATIONS] == true } else { true }
            if (fineLocationGranted && coarseLocationGranted && postNotificationsGranted) {
                mainInit()
            } else {
                Toast.makeText(this, "권한을 모두 허용해주세요", Toast.LENGTH_SHORT).show()
                requestPermissions()
            }
        }

        requestPermissions()

    }

    private fun mainInit() {
        LocalBroadcastManager.getInstance(this).registerReceiver(loginReceiver, IntentFilter("ACTION_LOGIN_REQUIRED"))

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this@MainActivity)

        binding.settingButton.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingActivity::class.java))
            overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
        }

        combinedLiveData.addSource(planViewModel.planStatus) { status ->
            val plan = planViewModel.plan.value
            combinedLiveData.value = Pair(status, plan)
        }

        combinedLiveData.addSource(planViewModel.plan) { plan ->
            val status = planViewModel.planStatus.value
            combinedLiveData.value = Pair(status, plan)
        }

        combinedLiveData.observe(this) { (status, plan) ->
            plan?.let {
                if (isDateEnd(plan.date)) {
                    lifecycleScope.launch { leavePlan(this@MainActivity) }
                    planViewModel.setPlanStatus(true)
                }

                if (status == PlanStatus.DISABLED && isActiveDate(plan.date)) {
                    planViewModel.setPlanStatus(false)
                }

                if (status == PlanStatus.ACTIVE && !isDateEnd(plan.date)) {
                    if (!isServiceRunning(this, BackgroundLocationUpdateService::class.java)) {
                        val intent = Intent(this, BackgroundLocationUpdateService::class.java)
                        ContextCompat.startForegroundService(this, intent)
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        initUserImageMap()
                        setUserMaker()
                    }, 3000)
                }
            }

            val inflater: LayoutInflater = layoutInflater
            selectedBottomView(status, plan, inflater)
        }
    }

    private fun initUserImageMap() {
        lifecycleScope.launch {
            val locationResponse = getLocation()
            val userIdList = locationResponse?.map { (id, _) -> id }
            val newUserImageMap = mutableMapOf<String, String>()
            userIdList?.forEach {
                val userImage = getUserById(it)?.profileImage.toString()
                newUserImageMap[it] = userImage
            }
            userImageMap = newUserImageMap
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            )
        } else {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }


    private fun isServiceRunning(context: Context, serviceClass: Class<out Service>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningServices(Int.MAX_VALUE)
        for (service in services) {
            if (service.service.className == serviceClass.name) {
                return true
            }
        }
        return false
    }

    fun startMyServiceIfNotRunning(context: Context) {
        if (!isServiceRunning(context, BackgroundLocationUpdateService::class.java)) {
            val intent = Intent(context, BackgroundLocationUpdateService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        initUserImageMap()
        setUserMaker()
    }

    private fun setUserMaker() {
        lifecycleScope.launch {
            val locationResponse = getLocation()
            locationResponse?.forEach { (id, location) ->
                Glide.with(this@MainActivity)
                    .asBitmap()
                    .load(userImageMap[id])
                    .override(dpToPx(), dpToPx())
                    .apply(RequestOptions().transform(CircleCrop()))
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            mMap.addMarker(MarkerOptions().icon(
                                BitmapDescriptorFactory.fromBitmap(resource)
                            ).position(LatLng(location.lat.toDouble(), location.lng.toDouble())))
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                        }
                    })
            }
        }
    }

    private suspend fun getUserById(id: String): UserResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.getUserById("bearer ${TokenManager.accessToken}", id)
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

    private suspend fun getLocation(): Map<String, Location>? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.getLocation("bearer ${TokenManager.accessToken}")
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

    private fun updateTimeTickReceiver(isRemove: Boolean) {
        if (!isRemove) {
            registerTimeTickReceiver()
        } else {
            unregisterReceiver(timeUpdateReceiver)
        }
    }

    private fun registerTimeTickReceiver() {
        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
        registerReceiver(timeUpdateReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        planViewModel.fetchPlan(this)
        updateTimeTickReceiver(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(loginReceiver)
    }

    override fun onPause() {
        super.onPause()
        updateTimeTickReceiver(true)
    }

    private fun selectedBottomView(status: PlanStatus?, plan: PlanResponse?, inflater: LayoutInflater) {
        when (status) {
            PlanStatus.NO_PROMISE -> {
                showNoPromiseView(inflater)
                showMarker(false, null)
            }
            PlanStatus.ACTIVE -> {
                plan?.let {
                    showActiveView(inflater, it)
                    showMarker(true, it)
                }
            }
            PlanStatus.DISABLED -> {
                plan?.let {
                    showDisabledView(inflater, it)
                    showMarker(true, it)
                }
            }
            else -> {
                showNoPromiseView(inflater)
                showMarker(false, null)
            }
        }

    }

    private fun dpToPx(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            40.toFloat(),
            this.resources.displayMetrics
        ).toInt()
    }

    private fun showDisabledView(inflater: LayoutInflater, promiseInfo: PlanResponse) {
        val view = DisabledViewBinding.inflate(inflater, binding.includeContainer, false).apply {
            this.sharedButton.setOnClickListener {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "text/plain"

                            CoroutineScope(Dispatchers.IO).launch {
                                val link = getLink(this@MainActivity)
                                if (link != null) {
                                    withContext(Dispatchers.Main) {
                                        this@MainActivity.copyToClipboard(link.link)
                                        Toast.makeText(this@MainActivity, "초대 링크가 클립보드에 복사됐어요", Toast.LENGTH_SHORT).show()

                                        val content = "약속에 초대됐어요!\n하단 링크를 통해 어떤 약속인지 확인하세요."
                                        putExtra(Intent.EXTRA_TEXT,"$content\n\n$link")
                                    }
                                }
                            }
                        },
                        "친구에게 초대 링크 공유하기"
                    )
                )
            }

            this.nameText.text = promiseInfo.name

            val (date, time) = parseDateTime(promiseInfo.date)
            this.dateText.text = date
            this.timeText.text = time

            this.locationText.text = promiseInfo.place.name

            this.nextText.text = calculateDifference(promiseInfo.date)

        }
        updateBottomLayout(view)
    }

    private fun showActiveView(inflater: LayoutInflater, promiseInfo: PlanResponse) {
        val view = ActiveViewBinding.inflate(inflater, binding.includeContainer, false).apply {
            this.nameText.text = promiseInfo.name

            val (date, time) = parseDateTime(promiseInfo.date)
            this.dateText.text = date
            this.timeText.text = time

            this.locationText.text = promiseInfo.place.name

            this.nextButton.setOnClickListener {
                startActivity(Intent(this@MainActivity, AlarmActivity::class.java))
                overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
            }
        }
        updateBottomLayout(view)
    }

    private fun showNoPromiseView(inflater: LayoutInflater) {
        val view = NoPromiseViewBinding.inflate(inflater, binding.includeContainer, false).apply {
            this.nextButton.setOnClickListener {
                startActivity(Intent(this@MainActivity, PlanActivity::class.java))
                overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
            }
        }
        updateBottomLayout(view)
    }

    private fun showMarker(isSet: Boolean, plan: PlanResponse?) {
        if (::mMap.isInitialized) {
            mMap.clear()
            if (isSet) {
                plan?.let {
                    val place = plan.place
                    mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromResource(R.drawable.default_marker)).position(LatLng(place.y.toDouble(), place.x.toDouble())))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.y.toDouble(), place.x.toDouble()), 16.0F))
                }
            }
        }
    }

    private fun calculateDifference(input: String): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:m:s")
        val inputDateTime = LocalDateTime.parse(input, formatter)

        val koreaZone = ZoneId.of("Asia/Seoul")
        val currentKoreaDateTime = ZonedDateTime.now(koreaZone).toLocalDateTime()

        val duration = Duration.between(currentKoreaDateTime, inputDateTime)

        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60

        return "${days}일 ${hours}시간 ${minutes}분 뒤 시작돼요"
    }

    private fun updateBottomLayout(view: ViewBinding) {
        binding.includeContainer.removeAllViews()
        binding.includeContainer.addView(view.root)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.style_json)
            )
            if (!success) {
                Toast.makeText(this@MainActivity, "지도 불러오기 실패 다시 시도해주세요", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Resources.NotFoundException) {
            Toast.makeText(this@MainActivity, "지도 불러오기 실패 다시 시도해주세요", Toast.LENGTH_SHORT).show()
        }

        mMap.setOnMarkerClickListener(this@MainActivity)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(37.627717208553854, 126.92327919682702), 16.0F))
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if (this::mMap.isInitialized) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 16.0F))
            return true
        } else {
            return false
        }
    }

}