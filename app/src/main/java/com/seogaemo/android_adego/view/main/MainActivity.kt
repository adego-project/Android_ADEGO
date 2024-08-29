package com.seogaemo.android_adego.view.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewbinding.ViewBinding
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
import com.seogaemo.android_adego.data.PlanResponse
import com.seogaemo.android_adego.data.PlanStatus
import com.seogaemo.android_adego.database.PlanViewModel
import com.seogaemo.android_adego.databinding.ActiveViewBinding
import com.seogaemo.android_adego.databinding.ActivityMainBinding
import com.seogaemo.android_adego.databinding.DisabledViewBinding
import com.seogaemo.android_adego.databinding.NoPromiseViewBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        askNotificationPermission()
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
            }

            val inflater: LayoutInflater = layoutInflater
            selectedBottomView(status, plan, inflater)
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (!isGranted) {
            askNotificationPermission()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    Toast.makeText(this@MainActivity, "알람 권한을 허용해주세요", Toast.LENGTH_SHORT).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

}