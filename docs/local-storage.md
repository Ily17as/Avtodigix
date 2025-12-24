# Local Storage (Room)

This document describes the Room-based local storage implementation for scan history.
It defines the schema and the access layer used by the app.

## Schema

**Database**: `avtodigix.db`

**Table**: `scan_snapshots`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER (PK) | Auto-generated primary key. |
| `timestampMillis` | INTEGER | Epoch millis when the scan completed. |
| `keyMetrics` | TEXT | JSON object map of metric name ➜ numeric value. |
| `dtcList` | TEXT | JSON array of stored diagnostic trouble codes. |

**Type converters** (`ScanSnapshotConverters`):
- `Map<String, Double>` ↔ JSON object
- `List<String>` ↔ JSON array

## Access Layer

**Entities & Converters**
- `ScanSnapshotEntity` (`app/src/main/java/com/example/avtodigix/storage/ScanSnapshotEntity.kt`)
- `ScanSnapshotConverters` (`app/src/main/java/com/example/avtodigix/storage/ScanSnapshotConverters.kt`)

**DAO**
- `ScanSnapshotDao` (`app/src/main/java/com/example/avtodigix/storage/ScanSnapshotDao.kt`)
  - `insert(snapshot)`
  - `getAll()` (history)
  - `getById(id)`
  - `deleteById(id)`
  - `clearAll()`

**Database**
- `AppDatabase` (`app/src/main/java/com/example/avtodigix/storage/AppDatabase.kt`)
  - `AppDatabase.create(context)` builds the Room database.

**Repository**
- `ScanSnapshotRepository` (`app/src/main/java/com/example/avtodigix/storage/ScanSnapshotRepository.kt`)
  - `saveSnapshot(snapshot)`
  - `getHistory()`
  - `getSnapshot(id)`
  - `deleteSnapshot(id)`
  - `clearHistory()`

## Usage Example

```kotlin
val database = AppDatabase.create(context)
val repository = ScanSnapshotRepository(database.scanSnapshotDao())

val snapshotId = repository.saveSnapshot(
    ScanSnapshot(
        timestampMillis = System.currentTimeMillis(),
        keyMetrics = mapOf("rpm" to 850.0, "coolantTemp" to 90.0),
        dtcList = listOf("P0300", "P0420")
    )
)

val history = repository.getHistory()
repository.deleteSnapshot(snapshotId)
```
