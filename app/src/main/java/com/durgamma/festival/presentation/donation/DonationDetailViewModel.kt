package com.durgamma.festival.presentation.donation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.durgamma.festival.di.AppContainer
import com.durgamma.festival.domain.model.Correction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DonationDetailViewModel(
    container: AppContainer,
    donationId: String
) : ViewModel() {
    val corrections: StateFlow<List<Correction>> =
        container.correctionRepository.observeForTarget(donationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
