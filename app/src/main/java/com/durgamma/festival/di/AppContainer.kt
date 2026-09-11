package com.durgamma.festival.di

import android.content.Context
import com.durgamma.festival.core.audio.AudioFocusManager
import com.durgamma.festival.core.audio.AudioRouteDetector
import com.durgamma.festival.core.tts.AndroidTtsClient
import com.durgamma.festival.core.tts.DualTtsEngine
import com.durgamma.festival.core.tts.SarvamTtsClient
import com.durgamma.festival.data.local.AppDatabase
import com.durgamma.festival.data.local.SessionPrefs
import com.durgamma.festival.data.repository.ActivityRepositoryImpl
import com.durgamma.festival.data.repository.CorrectionRepositoryImpl
import com.durgamma.festival.data.repository.DonationRepositoryImpl
import com.durgamma.festival.data.repository.EventRepositoryImpl
import com.durgamma.festival.data.repository.ExpenseRepositoryImpl
import com.durgamma.festival.domain.repository.ActivityRepository
import com.durgamma.festival.domain.repository.CorrectionRepository
import com.durgamma.festival.domain.repository.DonationRepository
import com.durgamma.festival.domain.repository.EventRepository
import com.durgamma.festival.domain.repository.ExpenseRepository
import com.durgamma.festival.domain.usecase.ObserveEventTotalsUseCase
import com.durgamma.festival.domain.usecase.RecordCorrectionUseCase
import com.durgamma.festival.domain.usecase.SaveDonationUseCase
import com.durgamma.festival.domain.usecase.SaveExpenseUseCase

/**
 * Manual service locator (no Hilt — keeps G1–G5 free of annotation-processor
 * surprises beyond KSP+Room). G3 ViewModels take what they need from here.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val sessionPrefs: SessionPrefs by lazy { SessionPrefs(appContext) }

    val eventRepository: EventRepository by lazy {
        EventRepositoryImpl(database.eventDao())
    }
    val donationRepository: DonationRepository by lazy {
        DonationRepositoryImpl(database.donationDao())
    }
    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepositoryImpl(database.expenseDao())
    }
    val correctionRepository: CorrectionRepository by lazy {
        CorrectionRepositoryImpl(database.correctionDao())
    }
    val activityRepository: ActivityRepository by lazy {
        ActivityRepositoryImpl(database.activityDao())
    }

    val saveDonation: SaveDonationUseCase by lazy {
        SaveDonationUseCase(donationRepository, activityRepository)
    }
    val saveExpense: SaveExpenseUseCase by lazy {
        SaveExpenseUseCase(expenseRepository, activityRepository)
    }
    val recordCorrection: RecordCorrectionUseCase by lazy {
        RecordCorrectionUseCase(correctionRepository, activityRepository)
    }
    val observeEventTotals: ObserveEventTotalsUseCase by lazy {
        ObserveEventTotalsUseCase(donationRepository, expenseRepository)
    }

    // G4 audio graph. Singletons: TTS init is expensive, MediaPlayer is exclusive.
    val audioFocus: AudioFocusManager by lazy { AudioFocusManager(appContext) }
    val routeDetector: AudioRouteDetector by lazy { AudioRouteDetector(appContext) }
    val ttsEngine: DualTtsEngine by lazy {
        DualTtsEngine(
            appContext,
            AndroidTtsClient(appContext),
            SarvamTtsClient(appContext),
            audioFocus
        )
    }
}
