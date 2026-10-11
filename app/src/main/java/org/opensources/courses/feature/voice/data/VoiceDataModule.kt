package org.opensources.courses.feature.voice.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.opensources.courses.feature.voice.domain.VoiceRecognizer

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceDataModule {
    @Binds abstract fun recognizer(implementation: VoskVoiceRecognizer): VoiceRecognizer
}
