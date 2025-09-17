package com.zyf.camera.di

import android.content.Context
import com.zyf.camera.data.datasource.CameraDataSource
import com.zyf.camera.data.datasource.CameraDataSourceImpl
import com.zyf.camera.data.repository.CameraRepositoryImpl
import com.zyf.camera.domain.repository.CameraRepository
import com.zyf.camera.domain.usercase.CaptureImageUseCase
import com.zyf.camera.domain.usercase.StartRecordingUseCase
import com.zyf.camera.domain.usercase.StopRecordingUseCase
import com.zyf.camera.domain.usercase.SwitchCameraUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    @Provides
    @Singleton
    fun provideCameraDataSource(@ApplicationContext context: Context): CameraDataSource {
        return CameraDataSourceImpl(context)
    }

    @Provides
    @Singleton
    fun provideCameraRepository(dataSource: CameraDataSource): CameraRepository {
        return CameraRepositoryImpl(dataSource)
    }

    @Provides
    fun provideCaptureImageUseCase(repository: CameraRepository): CaptureImageUseCase {
        return CaptureImageUseCase(repository)
    }

    @Provides
    fun provideStartRecordingUseCase(repository: CameraRepository): StartRecordingUseCase {
        return StartRecordingUseCase(repository)
    }

    @Provides
    fun provideStopRecordingUseCase(repository: CameraRepository): StopRecordingUseCase {
        return StopRecordingUseCase(repository)
    }

    @Provides
    fun provideSwitchCameraUseCase(repository: CameraRepository): SwitchCameraUseCase {
        return SwitchCameraUseCase(repository)
    }
}