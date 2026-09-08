package com.tonio.libre2clock.di

import android.content.Context
import com.tonio.libre2clock.data.repository.GlucoseRepositoryImpl
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.sync.AuthManager
import com.tonio.libre2clock.data.sync.CloudSyncManager

object AppContainer {

    @Volatile
    private var preferenceManager: PreferenceManager? = null

    @Volatile
    private var glucoseRepository: GlucoseRepositoryImpl? = null

    @Volatile
    private var authManager: AuthManager? = null

    @Volatile
    private var cloudSyncManager: CloudSyncManager? = null

    fun providePreferenceManager(context: Context): PreferenceManager {
        val existing = preferenceManager
        if (existing != null) return existing

        return synchronized(this) {
            val cached = preferenceManager
            if (cached != null) cached
            else PreferenceManager(context.applicationContext).also { preferenceManager = it }
        }
    }

    fun provideAuthManager(context: Context): AuthManager {
        val existing = authManager
        if (existing != null) return existing

        return synchronized(this) {
            val cached = authManager
            if (cached != null) cached
            else AuthManager(context.applicationContext).also { authManager = it }
        }
    }

    fun provideCloudSyncManager(context: Context): CloudSyncManager {
        val existing = cloudSyncManager
        if (existing != null) return existing

        return synchronized(this) {
            val cached = cloudSyncManager
            if (cached != null) {
                cached
            } else {
                val manager = CloudSyncManager(
                    context = context.applicationContext,
                    authManager = provideAuthManager(context),
                    preferenceManager = providePreferenceManager(context)
                )
                cloudSyncManager = manager
                manager
            }
        }
    }

    fun provideGlucoseRepository(context: Context): GlucoseRepositoryImpl {
        val existing = glucoseRepository
        if (existing != null) return existing

        return synchronized(this) {
            val cached = glucoseRepository
            if (cached != null) cached
            else GlucoseRepositoryImpl(
                context = context.applicationContext,
                preferenceManager = providePreferenceManager(context)
            ).also {
                glucoseRepository = it
            }
        }
    }
}
