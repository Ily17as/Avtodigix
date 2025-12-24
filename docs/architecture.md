# Architecture Overview

This document describes the layered architecture for Avtodigix and maps each layer to proposed
packages/modules. The intent is to keep hardware I/O, protocol handling, domain rules, and UI
concerns isolated while enabling testability and clear data flow.

## Layers

### 1) Bluetooth Layer
**Responsibility**: Discover, pair, connect, and stream bytes over RFCOMM to the OBD-II adapter.

**Key concerns**:
- Device discovery and selection (MAC address management)
- Connection lifecycle (connect, reconnect, disconnect)
- Raw byte read/write streams

**Proposed package/module**:
- `app/src/main/java/com/example/avtodigix/bluetooth/`
  - `BluetoothScanner`
  - `BluetoothConnector`
  - `BluetoothSocketProvider`

### 2) ELM327 Transport/Session
**Responsibility**: Encode and send AT/OBD commands, manage session state, parse raw adapter
responses, and handle timeouts/echoes/prompts.

**Key concerns**:
- ELM327 initialization (ATZ, ATL0, ATH0, etc.)
- Command framing and response termination (`>` prompt)
- Response normalization (strip echoes, whitespace, headers)

**Proposed package/module**:
- `app/src/main/java/com/example/avtodigix/elm327/`
  - `ElmTransport` (send/receive bytes)
  - `ElmSession` (init sequence + command queue)
  - `ElmResponseParser`

### 3) OBD Service Layer (Modes 01/03/07/09)
**Responsibility**: Provide typed requests/responses for OBD-II services and PIDs,
with encoding/decoding logic for each supported mode.

**Key concerns**:
- Mode 01 (current data): PID requests and parsing
- Mode 03 (DTCs): current stored trouble codes
- Mode 07 (DTCs): pending trouble codes
- Mode 09 (vehicle info): VIN, calibration IDs, etc.

**Proposed package/module**:
- `app/src/main/java/com/example/avtodigix/obd/service/`
  - `ObdServiceClient`
  - `Mode01Service`
  - `Mode03Service`
  - `Mode07Service`
  - `Mode09Service`
  - `PidDecoder` / `DtcDecoder`

### 4) Domain / Interpretation (Traffic-Light Rules)
**Responsibility**: Interpret OBD values into domain concepts (e.g., health status), applying
traffic-light rules (green/yellow/red) and aggregating findings.

**Key concerns**:
- Thresholds and heuristics per metric (e.g., coolant temp, fuel trim)
- Aggregation (worst-of, priority rules)
- Producing user-facing status models

**Proposed package/module**:
- `app/src/main/java/com/example/avtodigix/domain/`
  - `TrafficLightEvaluator`
  - `HealthStatus`
  - `VehicleSnapshot`
  - `RuleSet`

### 5) UI Layer
**Responsibility**: Display current status, diagnostics, and history; drive user actions to
initiate scans and pairing.

**Key concerns**:
- Screens for pairing, live data, DTCs, vehicle info
- ViewModels consuming domain models
- Error and connection states

**Proposed package/module**:
- `app/src/main/java/com/example/avtodigix/ui/`
  - `MainActivity`
  - `viewmodel/`
  - `screens/`

### 6) Local Storage
**Responsibility**: Persist scan history, DTC snapshots, and configuration data.

**Key concerns**:
- Room entities for scan sessions
- Caching last-known vehicle info
- User preferences (e.g., preferred device)

**Proposed package/module**:
- `app/src/main/java/com/example/avtodigix/storage/`
  - `AppDatabase`
  - `dao/`
  - `entities/`
  - `PreferencesStore`

## Textual Data-Flow Diagram

```
[User Action]
    |
    v
[UI Layer]
    |
    v
[Domain/Interpretation]
    |
    v
[OBD Service Layer (Modes 01/03/07/09)]
    |
    v
[ELM327 Transport/Session]
    |
    v
[Bluetooth Layer]
    |
    v
[OBD-II Adapter / Vehicle]
    |
    v
[Bluetooth Layer]
    |
    v
[ELM327 Transport/Session]
    |
    v
[OBD Service Layer]
    |
    v
[Domain/Interpretation]
    |
    v
[UI Layer]

(Optional side flow: persistence)
[Domain/Interpretation] --> [Local Storage] --> [UI Layer]
```

## Notes
- Each layer should depend only on the layer directly beneath it.
- Use interfaces in upper layers to allow mocking lower layers for tests.
- The `Local Storage` layer should be consumed by domain and UI via repositories,
  without directly coupling storage types to UI.
