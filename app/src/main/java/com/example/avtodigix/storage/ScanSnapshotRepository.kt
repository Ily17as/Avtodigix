package com.example.avtodigix.storage

class ScanSnapshotRepository(private val dao: ScanSnapshotDao) {
    suspend fun saveSnapshot(snapshot: ScanSnapshot): Long {
        return dao.insert(snapshot.toEntity())
    }

    suspend fun getHistory(): List<ScanSnapshot> {
        return dao.getAll().map { it.toModel() }
    }

    suspend fun getLatestSnapshot(): ScanSnapshot? {
        return dao.getLatest()?.toModel()
    }

    suspend fun getSnapshot(id: Long): ScanSnapshot? {
        return dao.getById(id)?.toModel()
    }

    suspend fun deleteSnapshot(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }
}
