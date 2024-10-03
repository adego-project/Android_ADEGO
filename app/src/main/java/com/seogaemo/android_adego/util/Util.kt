package com.seogaemo.android_adego.util

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Base64
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.seogaemo.android_adego.R
import com.seogaemo.android_adego.data.FCMRequest
import com.seogaemo.android_adego.data.FCMResponse
import com.seogaemo.android_adego.data.InvitePlanUrlResponse
import com.seogaemo.android_adego.data.PlanResponse
import com.seogaemo.android_adego.data.SignInResponse
import com.seogaemo.android_adego.data.UserResponse
import com.seogaemo.android_adego.database.TokenManager
import com.seogaemo.android_adego.network.RetrofitAPI
import com.seogaemo.android_adego.network.RetrofitClient
import com.seogaemo.android_adego.view.auth.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


object Util {
    suspend fun getRefresh(): SignInResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.getRefresh("bearer ${TokenManager.refreshToken}")
                if (response.isSuccessful) {
                    response.body()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseDateTime(dateTimeString: String): Pair<String, String> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:m:ss")
        val dateTime = LocalDateTime.parse(dateTimeString, formatter)

        val date = dateTime.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
        val time = dateTime.format(DateTimeFormatter.ofPattern("a h시 m분"))

        return Pair(date, time)
    }

    fun uriToBase64(context: Context, uri: Uri): String? {
        val contentResolver = context.contentResolver
        val inputStream: InputStream? = contentResolver.openInputStream(uri)

        return inputStream?.use {
            val bitmap = BitmapFactory.decodeStream(it)

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 150, 150, true)

            val byteArrayOutputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            Base64.encodeToString(byteArray, Base64.DEFAULT)
        }
    }

    suspend fun getUser(activity: Activity): UserResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.getUser("bearer ${TokenManager.accessToken}")
                if (response.isSuccessful) {
                    response.body()
                } else if (response.code() == 401) {
                    val getRefresh = getRefresh()
                    if (getRefresh != null) {
                        TokenManager.refreshToken = getRefresh.refreshToken
                        TokenManager.accessToken = getRefresh.accessToken
                        getUser(activity)
                    } else {
                        TokenManager.refreshToken = ""
                        TokenManager.accessToken = ""
                        activity.startActivity(Intent(activity, LoginActivity::class.java))
                        activity.finishAffinity()
                        null
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "정보 조회를 실패하였습니다", Toast.LENGTH_SHORT).show()
                    }
                    null
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "정보 조회를 실패하였습니다", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    fun createDialog(context: Context, text: String, mainButtonText: String, buttonAction: (customDialog: Dialog) -> Unit) {
        val customDialog = Dialog(context)
        customDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        customDialog.window?.requestFeature(Window.FEATURE_NO_TITLE)

        customDialog.setContentView(R.layout.dialog_setting)

        customDialog.findViewById<TextView>(R.id.textView).text = text
        customDialog.findViewById<TextView>(R.id.main_button_text).text = mainButtonText
        customDialog.findViewById<CardView>(R.id.main_button).setOnClickListener {
            buttonAction(customDialog)
        }
        customDialog.findViewById<CardView>(R.id.cancel_button).setOnClickListener {
            customDialog.cancel()
        }

        customDialog.show()
    }

    fun convertDateFormat(inputDate: String, inputFormat: String = "yyyy-MM-dd", outputFormat: String = "yyyy년 M월 d일"): String {
        val formatter = DateTimeFormatter.ofPattern(inputFormat)
        val date = LocalDate.parse(inputDate, formatter)

        val outputFormatter = DateTimeFormatter.ofPattern(outputFormat)
        return date.format(outputFormatter)
    }

    fun Float.fromDpToPx(): Int =
        (this * Resources.getSystem().displayMetrics.density).toInt()

    fun keyboardDown(activity: Activity) {
        val manager = activity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
        manager!!.hideSoftInputFromWindow(
            activity.currentFocus?.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
    }

    suspend fun getPlan(activity: Activity): PlanResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.getPlan("bearer ${TokenManager.accessToken}")
                if (response.isSuccessful) {
                    response.body()
                } else if (response.code() == 401) {
                    val getRefresh = getRefresh()
                    if (getRefresh != null) {
                        TokenManager.refreshToken = getRefresh.refreshToken
                        TokenManager.accessToken = getRefresh.accessToken
                        getPlan(activity)
                    } else if (response.code() == 404) {
                        null
                    } else {
                        TokenManager.refreshToken = ""
                        TokenManager.accessToken = ""
                        activity.startActivity(Intent(activity, LoginActivity::class.java))
                        activity.finishAffinity()
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "네트워크 에러", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    fun Context.isValidGlideContext() = this !is Activity || (!this.isDestroyed && !this.isFinishing)

    suspend fun getLink(activity: Activity): InvitePlanUrlResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.getInvitePlanUrl("bearer ${TokenManager.accessToken}")
                if (response.isSuccessful) {
                    response.body()
                } else if (response.code() == 401) {
                    val getRefresh = getRefresh()
                    if (getRefresh != null) {
                        TokenManager.refreshToken = getRefresh.refreshToken
                        TokenManager.accessToken = getRefresh.accessToken
                        getLink(activity)
                    } else {
                        TokenManager.refreshToken = ""
                        TokenManager.accessToken = ""
                        activity.startActivity(Intent(activity, LoginActivity::class.java))
                        activity.finishAffinity()
                        null
                    }
                }else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun Context.copyToClipboard(text: String) {
        val clipboardManager = getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("url", text)
        clipboardManager.setPrimaryClip(clipData)
    }

    suspend fun setFCMToken(fcmRequest: FCMRequest): FCMResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.setFCMToken("bearer ${TokenManager.accessToken}", fcmRequest)
                if (response.isSuccessful) {
                    response.body()
                } else if (response.code() == 401) {
                    val getRefresh = getRefresh()
                    if (getRefresh != null) {
                        TokenManager.refreshToken = getRefresh.refreshToken
                        TokenManager.accessToken = getRefresh.accessToken
                        setFCMToken(fcmRequest)
                    } else {
                        TokenManager.refreshToken = ""
                        TokenManager.accessToken = ""
                        null
                    }
                }else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isDateEnd(dateString: String): Boolean {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:m:ss")
        val inputDateTime = LocalDateTime.parse(dateString, formatter).plusMinutes(30)

        val koreaZone = ZoneId.of("Asia/Seoul")
        val currentKoreaDateTime = LocalDateTime.now(koreaZone)

        return currentKoreaDateTime.isAfter(inputDateTime) || currentKoreaDateTime.isEqual(inputDateTime)
    }

    fun isActiveDate(dateString: String): Boolean {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:m:ss")
        val inputDateTime = LocalDateTime.parse(dateString, formatter).minusMinutes(30)

        val koreaZone = ZoneId.of("Asia/Seoul")
        val currentKoreaDateTime = LocalDateTime.now(koreaZone)

        return currentKoreaDateTime.isEqual(inputDateTime) || currentKoreaDateTime.isAfter(inputDateTime)
    }

    suspend fun leavePlan(context: Context): PlanResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = retrofitAPI.leavePlan("bearer ${TokenManager.accessToken}")
                if (response.isSuccessful) {
                    response.body()
                } else if (response.code() == 401) {
                    val getRefresh = Util.getRefresh()
                    if (getRefresh != null) {
                        TokenManager.refreshToken = getRefresh.refreshToken
                        TokenManager.accessToken = getRefresh.accessToken
                        leavePlan(context)
                    } else {
                        TokenManager.refreshToken = ""
                        TokenManager.accessToken = ""
                        context.startActivity(Intent(context, LoginActivity::class.java))
                        (context as Activity).finishAffinity()
                        context.overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
                        null
                    }
                } else if (response.code() == 404) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "약속이 없습니다", Toast.LENGTH_SHORT).show()
                    }
                    null
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "약속 나가기를 실패하셨습니다", Toast.LENGTH_SHORT).show()
                    }
                    null
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "약속 나가기를 실패하셨습니다", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

}