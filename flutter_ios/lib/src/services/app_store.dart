import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/app_models.dart';

class AppStore extends ChangeNotifier {
  AppStore();

  static const _snapshotsKey = 'scan_snapshots';
  static const _onboardingKey = 'onboarding_seen';
  static const _userModeKey = 'user_mode';
  static const _diagnosticsKey = 'diagnostics_mode';

  ConnectionState connectionState = const ConnectionState();
  ObdState obdState = const ObdState();
  List<ScanSnapshot> snapshots = const [];
  bool onboardingSeen = false;
  UserMode userMode = UserMode.novice;
  bool diagnosticsMode = false;

  Timer? _metricsTimer;
  final Random _random = Random();

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    onboardingSeen = prefs.getBool(_onboardingKey) ?? false;
    userMode = (prefs.getString(_userModeKey) == 'professional') ? UserMode.professional : UserMode.novice;
    diagnosticsMode = prefs.getBool(_diagnosticsKey) ?? false;
    final raw = prefs.getStringList(_snapshotsKey) ?? const [];
    snapshots = raw.map((e) => ScanSnapshot.fromJson(jsonDecode(e) as Map<String, dynamic>)).toList();
    notifyListeners();
  }

  Future<void> dismissOnboarding() async {
    onboardingSeen = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_onboardingKey, true);
    notifyListeners();
  }

  void selectScannerType(ScannerType type) {
    connectionState = connectionState.copyWith(scannerType: type);
    notifyListeners();
  }

  Future<void> connect({String? host, int? port}) async {
    connectionState = connectionState.copyWith(status: ConnectionStatus.connecting, errorMessage: null);
    notifyListeners();
    await Future<void>.delayed(const Duration(milliseconds: 900));
    if (connectionState.scannerType == ScannerType.wifi) {
      final resolvedHost = host ?? connectionState.wifiHost ?? '192.168.0.10';
      final resolvedPort = port ?? connectionState.wifiPort ?? 35000;
      connectionState = connectionState.copyWith(
        status: ConnectionStatus.connected,
        wifiHost: resolvedHost,
        wifiPort: resolvedPort,
        wifiResolvedEndpoint: '$resolvedHost:$resolvedPort',
      );
    } else {
      connectionState = connectionState.copyWith(status: ConnectionStatus.connected, selectedDeviceName: 'OBDII Scanner');
    }
    _startMetricsStream();
    notifyListeners();
  }

  void disconnect() {
    _metricsTimer?.cancel();
    connectionState = connectionState.copyWith(status: ConnectionStatus.idle, errorMessage: null);
    obdState = const ObdState();
    notifyListeners();
  }

  void _startMetricsStream() {
    _metricsTimer?.cancel();
    _metricsTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      final hasIssue = _random.nextBool();
      obdState = obdState.copyWith(
        milOn: hasIssue,
        storedDtcs: hasIssue ? const ['P0301'] : const [],
        pendingDtcs: hasIssue ? const ['P0171'] : const [],
        readinessCount: 8,
        supportedPids: 24,
        metrics: ObdMetrics(
          rpm: 780 + _random.nextInt(1200),
          speed: _random.nextInt(110),
          coolantTemp: 75 + _random.nextInt(25),
          battery: 12 + _random.nextDouble() * 2,
        ),
        lastUpdated: DateTime.now(),
        lastCommand: '010C',
        lastRawResponse: '41 0C 1A F8',
      );
      notifyListeners();
    });
  }

  Future<void> setUserMode(UserMode mode) async {
    userMode = mode;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_userModeKey, mode == UserMode.professional ? 'professional' : 'novice');
    notifyListeners();
  }

  Future<void> setDiagnosticsMode(bool enabled) async {
    diagnosticsMode = enabled;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_diagnosticsKey, enabled);
    notifyListeners();
  }

  Future<void> clearErrors() async {
    obdState = obdState.copyWith(storedDtcs: const [], pendingDtcs: const [], milOn: false);
    notifyListeners();
  }

  Future<void> saveReport() async {
    final metrics = <String, String>{
      'RPM': '${obdState.metrics?.rpm ?? '—'}',
      'Скорость': '${obdState.metrics?.speed ?? '—'} км/ч',
      'Темп. ОЖ': '${obdState.metrics?.coolantTemp ?? '—'} °C',
      'АКБ': obdState.metrics == null ? '—' : '${obdState.metrics!.battery.toStringAsFixed(1)} В',
    };
    final snapshot = ScanSnapshot(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      timestamp: DateTime.now(),
      metrics: metrics,
      dtcs: [...obdState.storedDtcs, ...obdState.pendingDtcs],
    );
    snapshots = [snapshot, ...snapshots];
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(_snapshotsKey, snapshots.map((e) => jsonEncode(e.toJson())).toList());
    notifyListeners();
  }
}
