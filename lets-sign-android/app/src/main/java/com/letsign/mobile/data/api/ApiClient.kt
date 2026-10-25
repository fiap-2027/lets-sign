package com.letsign.mobile.data.api

import com.letsign.mobile.BuildConfig
import com.letsign.mobile.data.local.TokenManager
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun createRetrofit(tokenManager: TokenManager, baseUrl: String = BuildConfig.API_BASE_URL): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }

        // Content-Type fica por conta do Retrofit (JSON ou multipart); forçar aqui quebrava o upload de PDF.
        val authInterceptor = Interceptor { chain ->
            val token = tokenManager.getAccessToken()
            val requestBuilder = chain.request().newBuilder().header("Accept", "application/json")
            token?.let { requestBuilder.header("Authorization", "Bearer $it") }
            val response = chain.proceed(requestBuilder.build())
            // 401 com token = sessão expirada. (No login, 401 é "senha errada" e não tem token.)
            if (response.code == 401 && token != null) tokenManager.onUnauthorized()
            response
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
