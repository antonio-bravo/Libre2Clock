package com.tonio.libre2clock.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.MessageDigest

object LibreService {
    private var baseUrl = "https://api.libreview.io/"
    private const val PRODUCT = "llu.android"
    private const val VERSION = "4.16.0"

    private var authToken: String? = null
    private var userId: String? = null

    private var retrofit: Retrofit? = null

    fun setAuth(token: String, id: String) {
        authToken = token
        userId = id
    }

    fun updateRegion(region: String) {
        val newBaseUrl = "https://api-$region.libreview.io/"
        if (baseUrl != newBaseUrl) {
            baseUrl = newBaseUrl
            retrofit = null // Force rebuild of API
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
            .addHeader("product", PRODUCT)
            .addHeader("version", VERSION)
            .addHeader("Content-Type", "application/json")

        authToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        userId?.let {
            requestBuilder.addHeader("Account-Id", it.sha256())
        }

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val api: LibreLinkUpApi
        get() {
            if (retrofit == null) {
                retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
            }
            return retrofit!!.create(LibreLinkUpApi::class.java)
        }

    private fun String.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
