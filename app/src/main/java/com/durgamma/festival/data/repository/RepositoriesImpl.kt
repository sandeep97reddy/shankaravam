package com.durgamma.festival.data.repository

import com.durgamma.festival.data.local.ActivityDao
import com.durgamma.festival.data.local.CorrectionDao
import com.durgamma.festival.data.local.DonationDao
import com.durgamma.festival.data.local.EventDao
import com.durgamma.festival.data.local.ExpenseDao
import com.durgamma.festival.data.local.toDomain
import com.durgamma.festival.data.local.toEntity
import com.durgamma.festival.domain.model.ActivityRecord
import com.durgamma.festival.domain.model.Correction
import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.domain.model.DonationStatus
import com.durgamma.festival.domain.model.Event
import com.durgamma.festival.domain.model.EventStatus
import com.durgamma.festival.domain.model.Expense
import com.durgamma.festival.domain.model.ExpenseStatus
import com.durgamma.festival.domain.repository.ActivityRepository
import com.durgamma.festival.domain.repository.CorrectionRepository
import com.durgamma.festival.domain.repository.DonationRepository
import com.durgamma.festival.domain.repository.EventRepository
import com.durgamma.festival.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first implementations: Room is the single source of truth.
 * Writes run on Dispatchers.IO; Flows emit straight from Room (zero-jank UI per skill).
 */

class EventRepositoryImpl(private val dao: EventDao) : EventRepository {
    override fun observeEvents(): Flow<List<Event>> =
        dao.observeEvents().map { list -> list.map { it.toDomain() } }

    override fun observeEvent(id: String): Flow<Event?> = dao.observeEvent(id).map { it?.toDomain() }
    override suspend fun save(event: Event) = withContext(Dispatchers.IO) { dao.upsert(event.toEntity()) }
    override suspend fun closeEvent(id: String, now: Long) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, EventStatus.CLOSED.name, now)
    }
}

class DonationRepositoryImpl(private val dao: DonationDao) : DonationRepository {
    override fun observeForEvent(eventId: String): Flow<List<Donation>> =
        dao.observeForEvent(eventId).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Donation?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun save(donation: Donation) =
        withContext(Dispatchers.IO) { dao.upsert(donation.toEntity()) }

    override suspend fun updateStatus(id: String, status: DonationStatus, now: Long) =
        withContext(Dispatchers.IO) { dao.updateStatus(id, status.name, now) }

    override suspend fun pendingSync(): List<Donation> =
        withContext(Dispatchers.IO) { dao.pendingSync().map { it.toDomain() } }
}

class ExpenseRepositoryImpl(private val dao: ExpenseDao) : ExpenseRepository {
    override fun observeForEvent(eventId: String): Flow<List<Expense>> =
        dao.observeForEvent(eventId).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Expense?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun save(expense: Expense) =
        withContext(Dispatchers.IO) { dao.upsert(expense.toEntity()) }

    override suspend fun cancel(id: String, now: Long) =
        withContext(Dispatchers.IO) { dao.updateStatus(id, ExpenseStatus.CANCELLED.name, now) }

    override suspend fun pendingSync(): List<Expense> =
        withContext(Dispatchers.IO) { dao.pendingSync().map { it.toDomain() } }
}

class CorrectionRepositoryImpl(private val dao: CorrectionDao) : CorrectionRepository {
    override fun observeForEvent(eventId: String): Flow<List<Correction>> =
        dao.observeForEvent(eventId).map { list -> list.map { it.toDomain() } }

    override fun observeForTarget(targetId: String): Flow<List<Correction>> =
        dao.observeForTarget(targetId).map { list -> list.map { it.toDomain() } }

    override suspend fun record(correction: Correction) =
        withContext(Dispatchers.IO) { dao.insert(correction.toEntity()) }
}

class ActivityRepositoryImpl(private val dao: ActivityDao) : ActivityRepository {
    override fun observeRecent(eventId: String, limit: Int): Flow<List<ActivityRecord>> =
        dao.observeRecent(eventId, limit).map { list -> list.map { it.toDomain() } }

    override suspend fun log(entry: ActivityRecord) =
        withContext(Dispatchers.IO) { dao.insert(entry.toEntity()) }
}
