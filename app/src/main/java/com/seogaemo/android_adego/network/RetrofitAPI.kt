package com.seogaemo.android_adego.network

import com.seogaemo.android_adego.data.AddressResponse
import com.seogaemo.android_adego.data.FCMRequest
import com.seogaemo.android_adego.data.FCMResponse
import com.seogaemo.android_adego.data.ImageRequest
import com.seogaemo.android_adego.data.NameRequest
import com.seogaemo.android_adego.data.InvitePlanUrlResponse
import com.seogaemo.android_adego.data.Location
import com.seogaemo.android_adego.data.LocationResponse
import com.seogaemo.android_adego.data.PlanInviteResponse
import com.seogaemo.android_adego.data.PlanRequest
import com.seogaemo.android_adego.data.PlanResponse
import com.seogaemo.android_adego.data.SignInRequest
import com.seogaemo.android_adego.data.SignInResponse
import com.seogaemo.android_adego.data.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query


interface RetrofitAPI {
    @POST("oauth/kakao/login")
    suspend fun kakaoSignIn(
        @Body body: SignInRequest
    ): Response<SignInResponse>

    @POST("oauth/google/login")
    suspend fun googleSignIn(
        @Body body: SignInRequest
    ): Response<SignInResponse>

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") authorization: String,
    ): Response<UserResponse>

    @GET("user/{id}")
    suspend fun getUserById(
        @Header("Authorization") authorization: String,
        @Path(value = "id") id : String
    ): Response<UserResponse>

    @PATCH("user")
    suspend fun updateName(
        @Header("Authorization") authorization: String,
        @Body body: NameRequest
    ): Response<UserResponse>

    @PUT("user/profile-image")
    suspend fun updateImage(
        @Header("Authorization") authorization: String,
        @Body body: ImageRequest
    ): Response<Unit>

    @GET("auth/refresh")
    suspend fun getRefresh(
        @Header("Authorization") authorization: String,
    ): Response<SignInResponse>

    @DELETE("user")
    suspend fun deleteUser(
        @Header("Authorization") authorization: String,
    ): Response<UserResponse>

    @DELETE("plan")
    suspend fun leavePlan(
        @Header("Authorization") authorization: String,
    ): Response<PlanResponse>

    @GET("address/search")
    suspend fun searchAddress(
        @Header("Authorization") authorization: String,
        @Query("query") query: String
    ): Response<AddressResponse>

    @POST("plan")
    suspend fun setPlan(
        @Header("Authorization") authorization: String,
        @Body body: PlanRequest
    ): Response<PlanResponse>

    @GET("plan")
    suspend fun getPlan(
        @Header("Authorization") authorization: String,
    ): Response<PlanResponse>

    @POST("plan/invite")
    suspend fun getInvitePlanUrl(
        @Header("Authorization") authorization: String,
    ): Response<InvitePlanUrlResponse>

    @GET("plan/invite/{inviteId}")
    suspend fun getInvitePlanInfo(
        @Header("Authorization") authorization: String,
        @Path("inviteId") inviteId: String
    ): Response<PlanInviteResponse>

    @PUT("plan/invite/{inviteId}")
    suspend fun acceptPlan(
        @Header("Authorization") authorization: String,
        @Path("inviteId") inviteId: String
    ): Response<PlanResponse>

    @POST("auth/test-account")
    suspend fun testSignIn(
    ): Response<SignInResponse>

    @GET("location/participants")
    suspend fun getLocation(
        @Header("Authorization") authorization: String,
    ): Response<Map<String, Location>>

    @POST("location/update")
    suspend fun updateLocation(
        @Header("Authorization") authorization: String,
        @Body body: Location
    ): Response<Boolean>

    @PUT("user/fcm")
    suspend fun setFCMToken(
        @Header("Authorization") authorization: String,
        @Body body: FCMRequest
    ): Response<FCMResponse>

    @PUT("plan/alert")
    suspend fun callUser (
        @Header("Authorization") authorization: String,
        @Query("targetUserId") targetUserId: String
    ): Response<Unit>
}