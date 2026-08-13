package com.tonio.libre2clock.di

import android.content.Context
import com.tonio.libre2clock.data.repository.GlucoseRepositoryImpl
import com.tonio.libre2clock.data.repository.PreferenceManager

object AppContainer {

    @Volatile
    private var preferenceManager: PreferenceManager? = null

    @Volatile
    private var glucoseRepository: GlucoseRepositoryImpl? = null

    fun providePreferenceManager(context: Context): PreferenceManager {
        val existing = preferenceManager
        if (existing != null) return existing

        return synchronized(this) {
            val cached = preferenceManager
            if (cached != null) cached
            else PreferenceManager(context.applicationContext).also { preferenceManager = it }
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
