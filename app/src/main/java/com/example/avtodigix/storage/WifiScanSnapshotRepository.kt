package com.example.avtodigix.storage

class WifiScanSnapshotRepository(private val dao: WifiScanSnapshotDao) {
    suspend fun saveSnapshot(snapshot: WifiScanSnapshot): Long {
        return dao.insert(snapshot.toEntity())
    }

    suspend fun getHistory(): List<WifiScanSnapshot> {
        return dao.getAll().map { it.toModel() }
    }

    suspend fun getLatestSnapshot(): WifiScanSnapshot? {
        return dao.getLatest()?.toModel()
    }

    suspend fun clearHistory() {
        dao.clear()
    }
}
