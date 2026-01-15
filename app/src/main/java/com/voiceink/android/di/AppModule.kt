package com.voiceink.android.di

import android.content.Context
import com.voiceink.android.data.database.TranscriptionDao
import com.voiceink.android.data.database.VoiceInkDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideVoiceInkDatabase(
        @ApplicationContext context: Context
    ): VoiceInkDatabase {
        return VoiceInkDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideTranscriptionDao(database: VoiceInkDatabase): TranscriptionDao {
        return database.transcriptionDao()
    }
}
