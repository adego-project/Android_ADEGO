package com.seogaemo.android_adego.view.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.messaging.FirebaseMessaging
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.seogaemo.android_adego.BuildConfig
import com.seogaemo.android_adego.R
import com.seogaemo.android_adego.data.FCMRequest
import com.seogaemo.android_adego.data.SignInRequest
import com.seogaemo.android_adego.data.SignInResponse
import com.seogaemo.android_adego.database.SharedPreference
import com.seogaemo.android_adego.database.TokenManager
import com.seogaemo.android_adego.databinding.ActivityLoginBinding
import com.seogaemo.android_adego.network.RetrofitAPI
import com.seogaemo.android_adego.network.RetrofitClient
import com.seogaemo.android_adego.util.Util.getUser
import com.seogaemo.android_adego.util.Util.setFCMToken
import com.seogaemo.android_adego.view.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.kakaoLoginButton.setOnClickListener {
            kakaoLogin(this@LoginActivity)
        }

        binding.googleLoginButton.setOnClickListener {
            googleLogin(this@LoginActivity)
        }

        binding.logoButton.setOnClickListener {
            saveToken(this@LoginActivity, "test", "test")
        }

    }

    private fun kakaoLogin(context: Context) {
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
            } else if (token != null) {
                saveToken(context, "kakao", token.accessToken)
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                if (error != null) {
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        return@loginWithKakaoTalk
                    }

                    UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                } else if (token != null) {
                    saveToken(context, "kakao", token.accessToken)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
        }
    }

    private fun googleLogin(context: Context) {
        val credentialManager = CredentialManager.create(context)

        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(BuildConfig.CLIENT_ID)
            .setNonce(UUID.randomUUID().toString())
            .build()

        val request: GetCredentialRequest = GetCredentialRequest
            .Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = context,
                )
                val credential = result.credential
                when (result.credential) {
                    is CustomCredential -> {
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            try {
                                val googleIdTokenCredential = GoogleIdTokenCredential
                                    .createFrom(credential.data)
                                saveToken(context, "google", googleIdTokenCredential.idToken)
                            } catch (e: GoogleIdTokenParsingException) {
                                Toast.makeText(context, "구글 로그인에 실패하였습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else {
                            Toast.makeText(context, "구글 로그인에 실패하였습니다", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        Toast.makeText(context, "구글 로그인에 실패하였습니다", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {

            }
        }

    }

    private suspend fun postSignIn(context: Context, type: String, signInRequest: SignInRequest): SignInResponse? {
        return try {
            withContext(Dispatchers.IO) {
                val retrofitAPI = RetrofitClient.getInstance().create(RetrofitAPI::class.java)
                val response = if (type == "kakao") retrofitAPI.kakaoSignIn(signInRequest) else if (type == "google") retrofitAPI.googleSignIn(signInRequest) else retrofitAPI.testSignIn()
                if (response.isSuccessful) {
                    response.body()
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "로그인에 실패하였습니다", Toast.LENGTH_SHORT).show()
                    }
                    null
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "로그인에 실패하였습니다", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    private fun saveToken(context: Context, type: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val signInResponses = postSignIn(context, type, SignInRequest(token))

            withContext(Dispatchers.Main) {
                if (signInResponses != null) {
                    TokenManager.accessToken = signInResponses.accessToken
                    TokenManager.refreshToken = signInResponses.refreshToken
                    finishLogin(context)
                } else {
                    Toast.makeText(context, "로그인에 실패하였습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun finishLogin(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val isFirst = getUser(this@LoginActivity)?.name.isNullOrEmpty()
            val token = fetchFcmToken()

            val isSuccess = token?.let { setFCMToken(FCMRequest(token)) } != null

            withContext(Dispatchers.Main) {
                if (!isSuccess) {
                    Toast.makeText(context, "로그인 실패", Toast.LENGTH_SHORT).show()
                    TokenManager.accessToken = ""
                    TokenManager.refreshToken = ""
                } else {
                    val nextActivity = if (isFirst) {
                        ProfileNameActivity::class.java
                    } else {
                        SharedPreference.isFirst = false
                        MainActivity::class.java
                    }
                    startActivity(Intent(context, nextActivity))
                    overridePendingTransition(R.anim.anim_slide_in_from_right_fade_in, R.anim.anim_fade_out)
                    finishAffinity()
                }
            }
        }
    }

    private suspend fun fetchFcmToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val task = FirebaseMessaging.getInstance().token
                val token = Tasks.await(task)
                token
            } catch (e: Exception) {
                null
            }
        }
    }

}