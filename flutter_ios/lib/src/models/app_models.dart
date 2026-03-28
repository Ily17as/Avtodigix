enum ScannerType { bluetooth, wifi }
enum ConnectionStatus { idle, initializing, permissionsRequired, connecting, connected, error }
enum UserMode { novice, professional }

class AppConnectionState {
  const AppConnectionState({
    this.scannerType = ScannerType.bluetooth,
    this.status = ConnectionStatus.idle,
    this.selectedDeviceName,
    this.wifiHost,
    this.wifiPort,
    this.wifiResolvedEndpoint,
    this.errorMessage,
    this.isRetryable = false,
    this.troubleshootingSteps = const [],
    this.log = const ['Лог: ожидание → поиск → подключение.'],
  });

  final ScannerType scannerType;
  final ConnectionStatus status;
  final String? selectedDeviceName;
  final String? wifiHost;
  final int? wifiPort;
  final String? wifiResolvedEndpoint;
  final String? errorMessage;
  final bool isRetryable;
  final List<String> troubleshootingSteps;
  final List<String> log;

  AppConnectionState copyWith({
    ScannerType? scannerType,
    ConnectionStatus? status,
    String? selectedDeviceName,
    String? wifiHost,
    int? wifiPort,
    String? wifiResolvedEndpoint,
    String? errorMessage,
    bool? isRetryable,
    List<String>? troubleshootingSteps,
    List<String>? log,
  }) {
    return AppConnectionState(
      scannerType: scannerType ?? this.scannerType,
      status: status ?? this.status,
      selectedDeviceName: selectedDeviceName ?? this.selectedDeviceName,
      wifiHost: wifiHost ?? this.wifiHost,
      wifiPort: wifiPort ?? this.wifiPort,
      wifiResolvedEndpoint: wifiResolvedEndpoint ?? this.wifiResolvedEndpoint,
      errorMessage: errorMessage,
      isRetryable: isRetryable ?? this.isRetryable,
      troubleshootingSteps: troubleshootingSteps ?? this.troubleshootingSteps,
      log: log ?? this.log,
    );
  }
}

class ObdMetrics {
  const ObdMetrics({required this.rpm, required this.speed, required this.coolantTemp, required this.battery});

  final int rpm;
  final int speed;
  final int coolantTemp;
  final double battery;
}

class ObdState {
  const ObdState({
    this.milOn = false,
    this.storedDtcs = const [],
    this.pendingDtcs = const [],
    this.readinessCount = 0,
    this.supportedPids = 0,
    this.metrics,
    this.lastUpdated,
    this.lastCommand,
    this.lastRawResponse,
    this.lastError = 'Нет',
  });

  final bool milOn;
  final List<String> storedDtcs;
  final List<String> pendingDtcs;
  final int readinessCount;
  final int supportedPids;
  final ObdMetrics? metrics;
  final DateTime? lastUpdated;
  final String? lastCommand;
  final String? lastRawResponse;
  final String lastError;

  ObdState copyWith({
    bool? milOn,
    List<String>? storedDtcs,
    List<String>? pendingDtcs,
    int? readinessCount,
    int? supportedPids,
    ObdMetrics? metrics,
    DateTime? lastUpdated,
    String? lastCommand,
    String? lastRawResponse,
    String? lastError,
  }) {
    return ObdState(
      milOn: milOn ?? this.milOn,
      storedDtcs: storedDtcs ?? this.storedDtcs,
      pendingDtcs: pendingDtcs ?? this.pendingDtcs,
      readinessCount: readinessCount ?? this.readinessCount,
      supportedPids: supportedPids ?? this.supportedPids,
      metrics: metrics ?? this.metrics,
      lastUpdated: lastUpdated ?? this.lastUpdated,
      lastCommand: lastCommand ?? this.lastCommand,
      lastRawResponse: lastRawResponse ?? this.lastRawResponse,
      lastError: lastError ?? this.lastError,
    );
  }
}

class ScanSnapshot {
  const ScanSnapshot({required this.id, required this.timestamp, required this.metrics, required this.dtcs});

  final String id;
  final DateTime timestamp;
  final Map<String, String> metrics;
  final List<String> dtcs;

  Map<String, dynamic> toJson() => {
        'id': id,
        'timestamp': timestamp.toIso8601String(),
        'metrics': metrics,
        'dtcs': dtcs,
      };

  factory ScanSnapshot.fromJson(Map<String, dynamic> json) => ScanSnapshot(
        id: json['id'] as String,
        timestamp: DateTime.parse(json['timestamp'] as String),
        metrics: Map<String, String>.from(json['metrics'] as Map),
        dtcs: List<String>.from(json['dtcs'] as List),
      );
}
