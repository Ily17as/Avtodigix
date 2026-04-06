package com.example.avtodigix.storage

import com.example.avtodigix.domain.CheckSession

class DailyCheckSessionRepository(private val dao: DailyCheckSessionDao) {
    suspend fun saveSession(session: CheckSession): Long {
        return dao.insert(session.toStorageModel().toEntity())
    }

    suspend fun getRecentSessions(vehicleId: String, limit: Int): List<CheckSession> {
        return dao.getRecentSessions(vehicleId, limit)
            .map { it.toModel().toDomainModel() }
    }

    suspend fun getRecentSuccessfulSessions(vehicleId: String, limit: Int): List<CheckSession> {
        return dao.getRecentSuccessfulSessions(vehicleId, limit)
            .map { it.toModel().toDomainModel() }
    }

    suspend fun getLastSuccessfulSession(vehicleId: String): CheckSession? {
        return dao.getLastSuccessfulSession(vehicleId)
            ?.toModel()
            ?.toDomainModel()
    }
}
