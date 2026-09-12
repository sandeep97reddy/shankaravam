package com.shankaravam.festival.di

import android.content.Context
import com.shankaravam.festival.core.audio.AudioFocusManager
import com.shankaravam.festival.core.audio.AudioRouteDetector
import com.shankaravam.festival.core.tts.AndroidTtsClient
import com.shankaravam.festival.core.tts.DualTtsEngine
import com.shankaravam.festival.core.tts.SarvamTtsClient
import com.shankaravam.festival.data.local.AppDatabase
import com.shankaravam.festival.data.local.SecureKeyStore
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.data.remote.AuthRepository
import com.shankaravam.festival.data.remote.FirestoreSyncService
import com.shankaravam.festival.data.repository.ActivityRepositoryImpl
import com.shankaravam.festival.data.repository.CorrectionRepositoryImpl
import com.shankaravam.festival.data.repository.DonationRepositoryImpl
import com.shankaravam.festival.data.repository.EventRepositoryImpl
import com.shankaravam.festival.data.repository.ExpenseRepositoryImpl
import com.shankaravam.festival.domain.repository.ActivityRepository
import com.shankaravam.festival.domain.repository.CorrectionRepository
import com.shankaravam.festival.domain.repository.DonationRepository
import com.shankaravam.festival.domain.repository.EventRepository
import com.shankaravam.festival.core.export.ReportExporter
import com.shankaravam.festival.domain.repository.ExpenseRepository
import com.shankaravam.festival.domain.usecase.CorrectRecordUseCase
import com.shankaravam.festival.domain.usecase.ObserveEventTotalsUseCase
import com.shankaravam.festival.domain.usecase.RecordCorrectionUseCase
import com.shankaravam.festival.domain.usecase.SaveDonationUseCase
import com.shankaravam.festival.domain.usecase.SaveExpenseUseCase

/**
 * Manual service locator (no Hilt — keeps G1–G5 free of annotation-processor
 * surprises beyond KSP+Room). G3 ViewModels take what they need from here.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

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
    val correctRecord: CorrectRecordUseCase by lazy {
        CorrectRecordUseCase(recordCorrection)
    }
    val reportExporter: ReportExporter by lazy {
        ReportExporter(appContext)
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
            AndroidTtsClient(appContext) {
                sessionPrefs.nativeTtsVoice to sessionPrefs.nativeTtsSpeed
            },
            SarvamTtsClient(appContext),
            audioFocus,
            chimeEnabled = { sessionPrefs.playTempleChime }
        )
    }

    // G6 cloud graph. All inert until the user enables Cloud Sync in Settings;
    // Firebase getters are guarded so builds without google-services.json run fine.
    val secureKeys: SecureKeyStore by lazy { SecureKeyStore(appContext, sessionPrefs) }
    val authRepository: AuthRepository by lazy { AuthRepository(appContext, sessionPrefs) }
    val syncService: FirestoreSyncService by lazy {
        FirestoreSyncService(database, sessionPrefs)
    }
}
