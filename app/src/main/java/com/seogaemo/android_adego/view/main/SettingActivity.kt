package com.seogaemo.android_adego.view.main

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.seogaemo.android_adego.R
import com.seogaemo.android_adego.data.ImageRequest
import com.seogaemo.android_adego.data.PlanResponse
import com.seogaemo.android_adego.data.UserResponse
import com.seogaemo.android_adego.database.SharedPreference
import com.seogaemo.android_adego.database.TokenManager
import com.seogaemo.android_adego.databinding.ActivitySettingBinding
import com.seogaemo.android_adego.network.RetrofitAPI
import com.seogaemo.android_adego.network.RetrofitClient
import com.seogaemo.android_adego.util.Util
import com.seogaemo.android_adego.util.Util.createDialog
import com.seogaemo.android_adego.util.Util.getUser
import com.seogaemo.android_adego.util.Util.isValidGlideContext
import com.seogaemo.android_adego.util.Util.leavePlan
import com.seogaemo.android_adego.util.Util.uriToBase64
import com.seogaemo.android_adego.view.auth.LoginActivity
import com.seogaemo.android_adego.view.auth.ProfileNameActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingBinding

    private val getContent: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val image = uriToBase64(this@SettingActivity, it).toString()
                CoroutineScope(Dispatchers.IO).launch {
                    updateImage(this@SettingActivity, image)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CoroutineScope(Dispatchers.IO).launch {
            val userInfo = getUser(this@SettingActivity)
            withContext(Dispatchers.Main) {
                binding.nameText.text = userInfo?.name
                if (this@SettingActivity.isValidGlideContext()) {
                    Glide.with(this@SettingActivity)
                        .load(userInfo?.profileImage)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.imageView)
                }
            }
        }

        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.anim_slide_in_from_left_fade_in, R.anim.anim_fade_out)
        }

        binding.leavePlanButton.apply {
            this.paintFlags = binding.leavePlanButton.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            this.setOnClickListener {
                createDialog(this@SettingActivity, "약속에서\n나가시겠습니까?", "나가기") { dialog ->
                    CoroutineScope(Dispatchers.IO).launch {
                        leavePlan(this@SettingActivity)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingActivity, "약속에서 탈퇴하셨습니다", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    }
                }
            }
        }

        binding.logoutButton.apply {
            this.paintFlags = binding.logoutButton.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            this.setOnClickListener {
                createDialog(this@SettingActivity, "계정에서\n로그아웃 하시겠습니까?", "로그아웃") { dialog ->
                    SharedPreference.isFirst = true
                    TokenManager.refreshToken = ""
                    TokenManager.accessToken = ""
                    dialog.dismiss()

                    startActivity(Intent(this@SettingActivity, LoginActivity::class.java))
                    overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
                    finishAffinity()
                }
            }
        }

        binding.withdrawalButton.apply {
            this.paintFlags = binding.withdrawalButton.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            this.setOnClickListener {
                createDialog(this@SettingActivity, "계정에서\n탈퇴 하시겠습니까?", "탈퇴") { customDialog ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val isSuccess = deleteUser(this@SettingActivity) != null
                        withContext(Dispatchers.Main) {
                            if (isSuccess) {
                                SharedPreference.isFirst = true
                                TokenManager.refreshToken = ""
                                TokenManager.accessToken = ""
                                customDialog.dismiss()

                                startActivity(Intent(this@SettingActivity, LoginActivity::class.java))
                                overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
                                finishAffinity()
                            } else {
                                customDialog.dismiss()
                            }
                        }
                    }
                }
            }
        }

        binding.changeNicknameButton.apply {
            this.paintFlags = binding.changeNicknameButton.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            this.setOnClickListener {
                startActivity(
                    Intent(this@SettingActivity, ProfileNameActivity::class.java).apply {
                        putExtra("isSetting", true)
                        finish()
                    }
                )
                overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
            }
        }

        binding.changeImageButton.apply {
            binding.changeImageText.paintFlags = binding.changeImageText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            this.setOnClickListener { getContent.launch("image/*") }
        }

    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.anim_slide_in_from_left_fade_in, R.anim.anim_fade_out)
    }

    private suspend fun updateImage(context: Context, image: String): Unit? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.updateImage("bearer ${TokenManager.accessToken}", ImageRequest(image))
                if (response.isSuccessful) {
                    response.body()
                } else if (response.code() == 401) {
                    val getRefresh = Util.getRefresh()
                    if (getRefresh != null) {
                        TokenManager.refreshToken = getRefresh.refreshToken
                        TokenManager.accessToken = getRefresh.accessToken
                        updateImage(context, image)
                    } else {
                        TokenManager.refreshToken = ""
                        TokenManager.accessToken = ""
                        startActivity(Intent(context, LoginActivity::class.java))
                        finishAffinity()
                        overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
                        null
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "업로드를 실패하였습니다", Toast.LENGTH_SHORT).show()
                    }
                    null
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "업로드를 실패하였습니다", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    private suspend fun deleteUser(context: Context): UserResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.deleteUser("bearer ${TokenManager.accessToken}")
                if (response.isSuccessful) {
                    response.body()
                } else if (response.code() == 401) {
                    val getRefresh = Util.getRefresh()
                    if (getRefresh != null) {
                        TokenManager.refreshToken = getRefresh.refreshToken
                        TokenManager.accessToken = getRefresh.accessToken
                        deleteUser(context)
                    } else {
                        TokenManager.refreshToken = ""
                        TokenManager.accessToken = ""
                        startActivity(Intent(context, LoginActivity::class.java))
                        finishAffinity()
                        overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
                        null
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "회원탈퇴를 실패하였습니다", Toast.LENGTH_SHORT).show()
                    }
                    null
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "회원탈퇴를 실패하였습니다", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }


}