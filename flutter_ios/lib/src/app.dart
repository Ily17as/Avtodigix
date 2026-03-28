import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import 'helpers/dtc_report_helper.dart';
import 'models/app_models.dart';
import 'report_details.dart';
import 'services/app_store.dart';

class AvtodigixApp extends StatefulWidget {
  const AvtodigixApp({super.key});

  @override
  State<AvtodigixApp> createState() => _AvtodigixAppState();
}

class _AvtodigixAppState extends State<AvtodigixApp> {
  static final Uri _feedbackBaseUri = Uri.parse('https://forms.yandex.ru/u/681f9b4084227c924223e522');
  static const int _maxCommentLength = 500;
  static const List<String> _feedbackTags = ['Подключение', 'Данные', 'Стабильность', 'Дизайн', 'Другое'];

  final AppStore store = AppStore();
  late final VoidCallback _storeListener;
  int currentIndex = 0;

  Future<void> _openFeedbackForm(Uri feedbackUri) async {
    if (!mounted) {
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    final launchModes = <LaunchMode>[
      LaunchMode.externalApplication,
      LaunchMode.platformDefault,
    ];

    for (final mode in launchModes) {
      try {
        final opened = await launchUrl(feedbackUri, mode: mode);
        if (opened) {
          messenger.showSnackBar(
            const SnackBar(
              content: Text('Форма отзыва открыта в браузере'),
            ),
          );
          return;
        }
      } catch (_) {
        // Пробуем следующий режим открытия ссылки.
      }
    }

    if (!mounted) {
      return;
    }

    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Не удалось открыть форму отзыва'),
        content: SelectableText(feedbackUri.toString()),
        actions: [
          TextButton(
            onPressed: () async {
              await Clipboard.setData(ClipboardData(text: feedbackUri.toString()));
              if (!mounted) {
                return;
              }
              Navigator.of(dialogContext).pop();
              messenger.showSnackBar(
                const SnackBar(content: Text('Ссылка на форму скопирована')),
              );
            },
            child: const Text('Скопировать ссылку'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('Закрыть'),
          ),
        ],
      ),
    );
  }

  Future<void> _showFeedbackSheet() async {
    var rating = 0;
    final selectedTags = <String>{};
    var commentText = '';

    final payload = await showModalBottomSheet<_FeedbackDraft>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (bottomSheetContext) {
        return StatefulBuilder(
          builder: (context, setSheetState) {
            final requiresComment = rating <= 3;
            final rawKeyboardInset = MediaQuery.of(context).viewInsets.bottom;
            final keyboardInset = rawKeyboardInset.isFinite && rawKeyboardInset >= 0 ? rawKeyboardInset : 0.0;
            return SafeArea(
              child: Padding(
                padding: EdgeInsets.fromLTRB(16, 8, 16, 16 + keyboardInset),
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Поделитесь впечатлениями', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 12),
                      const Text('Оцените ваш опыт'),
                      RatingBar(
                        rating: rating.toDouble(),
                        onChanged: (value) => setSheetState(() => rating = value.toInt()),
                      ),
                      const SizedBox(height: 12),
                      const Text('Что стоит улучшить?'),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: _feedbackTags
                            .map(
                              (tag) => FilterChip(
                                label: Text(tag),
                                selected: selectedTags.contains(tag),
                                onSelected: (enabled) {
                                  setSheetState(() {
                                    if (enabled) {
                                      selectedTags.add(tag);
                                    } else {
                                      selectedTags.remove(tag);
                                    }
                                  });
                                },
                              ),
                            )
                            .toList(growable: false),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        maxLength: _maxCommentLength,
                        minLines: 3,
                        maxLines: 5,
                        onChanged: (value) => commentText = value,
                        decoration: const InputDecoration(
                          hintText: 'Комментарий (до 500 символов)',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: OutlinedButton(
                              onPressed: () => Navigator.of(bottomSheetContext).pop(),
                              child: const Text('Позже'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: FilledButton(
                              onPressed: () {
                                final normalizedComment = commentText.trim();
                                if (rating < 1 || rating > 5) {
                                  ScaffoldMessenger.of(bottomSheetContext).showSnackBar(
                                    const SnackBar(content: Text('Выберите оценку от 1 до 5 перед отправкой')),
                                  );
                                  return;
                                }
                                if (requiresComment && normalizedComment.isEmpty) {
                                  ScaffoldMessenger.of(bottomSheetContext).showSnackBar(
                                    const SnackBar(content: Text('Пожалуйста, добавьте комментарий')),
                                  );
                                  return;
                                }
                                Navigator.of(bottomSheetContext).pop(
                                  _FeedbackDraft(
                                    rating: rating,
                                    tags: selectedTags.toList(growable: false),
                                    comment: normalizedComment,
                                  ),
                                );
                              },
                              child: const Text('Продолжить'),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        );
      },
    );

    if (payload == null) {
      return;
    }

    final feedbackUri = _buildFeedbackRedirectUri(payload);
    await _openFeedbackForm(feedbackUri);
  }

  Uri _buildFeedbackRedirectUri(_FeedbackDraft payload) {
    final normalizedRating = payload.rating.clamp(1, 5);
    final features = payload.tags.map((it) => it.trim()).where((it) => it.isNotEmpty).toSet().join(', ').trim();
    final message = StringBuffer()
      ..write('Оценка: $normalizedRating/5\n')
      ..write('Понравилось: ${features.isEmpty ? 'не выбрано' : features}\n')
      ..write('Комментарий: ${payload.comment.isEmpty ? '—' : payload.comment}');

    return _feedbackBaseUri.replace(
      queryParameters: {
        'rating': normalizedRating.toString(),
        'answer_long_text_96199': message.toString(),
        'source': 'avtodigix',
      },
    );
  }

  @override
  void initState() {
    super.initState();
    _storeListener = () {
      if (!mounted) return;
      setState(() {});
    };
    store.init();
    store.addListener(_storeListener);
  }

  @override
  void dispose() {
    store.removeListener(_storeListener);
    store.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Avtodigix iOS',
      theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
      home: Scaffold(
        appBar: AppBar(title: const Text('Avtodigix')),
        body: Stack(
          children: [
            IndexedStack(
              index: currentIndex,
              children: [
                _ConnectionTab(store: store),
                _DataTab(store: store),
                _IssuesHistoryTab(store: store),
                _SettingsTab(store: store),
              ],
            ),
            if (!store.onboardingSeen) _OnboardingOverlay(store: store),
          ],
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: currentIndex,
          onDestinationSelected: (value) => setState(() => currentIndex = value),
          destinations: const [
            NavigationDestination(icon: Icon(Icons.usb), label: 'Подключение'),
            NavigationDestination(icon: Icon(Icons.monitor_heart), label: 'Данные'),
            NavigationDestination(icon: Icon(Icons.error_outline), label: 'Ошибки'),
            NavigationDestination(icon: Icon(Icons.settings), label: 'Настройки'),
          ],
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: _showFeedbackSheet,
          icon: const Icon(Icons.feedback_outlined),
          label: const Text('Отзыв'),
        ),
      ),
    );
  }
}

class _FeedbackDraft {
  const _FeedbackDraft({
    required this.rating,
    required this.tags,
    required this.comment,
  });

  final int rating;
  final List<String> tags;
  final String comment;
}

class RatingBar extends StatelessWidget {
  const RatingBar({required this.rating, required this.onChanged, super.key});

  final double rating;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: List.generate(
        5,
        (index) {
          final starIndex = index + 1;
          return IconButton(
            icon: Icon(starIndex <= rating ? Icons.star_rounded : Icons.star_outline_rounded),
            color: Theme.of(context).colorScheme.primary,
            onPressed: () => onChanged(starIndex.toDouble()),
            tooltip: '$starIndex',
          );
        },
      ),
    );
  }
}

class _OnboardingOverlay extends StatelessWidget {
  const _OnboardingOverlay({required this.store});
  final AppStore store;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black54,
      child: Center(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Быстрый старт', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                const Text('1. Подключите OBD-II адаптер.'),
                const Text('2. Выберите Bluetooth/Wi-Fi.'),
                const Text('3. Откройте сводку.'),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: store.dismissOnboarding,
                  child: const Text('Понятно'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ConnectionTab extends StatefulWidget {
  const _ConnectionTab({required this.store});
  final AppStore store;

  @override
  State<_ConnectionTab> createState() => _ConnectionTabState();
}

class _ConnectionTabState extends State<_ConnectionTab> {
  final hostController = TextEditingController(text: '192.168.0.10');
  final portController = TextEditingController(text: '35000');

  @override
  void dispose() {
    hostController.dispose();
    portController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.store.connectionState;
    final hasError = state.status == ConnectionStatus.error;
    final errorColor = Theme.of(context).colorScheme.error;
    final connectLabel = state.status == ConnectionStatus.connected ? 'Отключиться' : 'Подключиться';

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('3 шага', style: TextStyle(fontWeight: FontWeight.bold)),
        const Text('1. Включите адаптер OBD-II.'),
        const Text('2. Выберите Bluetooth или Wi-Fi.'),
        const Text('3. Нажмите «Подключить».'),
        const SizedBox(height: 16),
        SegmentedButton<ScannerType>(
          segments: const [
            ButtonSegment(value: ScannerType.bluetooth, label: Text('Bluetooth')),
            ButtonSegment(value: ScannerType.wifi, label: Text('Wi‑Fi')),
          ],
          selected: {state.scannerType},
          onSelectionChanged: (s) => widget.store.selectScannerType(s.first),
        ),
        if (state.scannerType == ScannerType.wifi) ...[
          const SizedBox(height: 12),
          ExpansionTile(
            tilePadding: EdgeInsets.zero,
            childrenPadding: const EdgeInsets.only(bottom: 8),
            title: const Text('Дополнительно'),
            subtitle: const Text('Ручные параметры Wi‑Fi'),
            children: [
              TextField(controller: hostController, decoration: const InputDecoration(labelText: 'IP-адрес')),
              const SizedBox(height: 8),
              TextField(controller: portController, decoration: const InputDecoration(labelText: 'Порт')),
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerLeft,
                child: OutlinedButton.icon(
                  onPressed: () {
                    widget.store.resetWifiAdvancedToAuto();
                    hostController.text = '192.168.0.10';
                    portController.text = '35000';
                  },
                  icon: const Icon(Icons.restart_alt),
                  label: const Text('Сбросить на авто'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton.icon(
                onPressed: () => _openSystemSetting(SystemQuickAction.wifi),
                icon: const Icon(Icons.wifi),
                label: const Text('Настройки Wi‑Fi'),
              ),
            ],
          ),
        ] else ...[
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton.icon(
                onPressed: () => _openSystemSetting(SystemQuickAction.bluetooth),
                icon: const Icon(Icons.bluetooth),
                label: const Text('Настройки Bluetooth'),
              ),
            ],
          ),
        ],
        const SizedBox(height: 16),
        FilledButton(
          onPressed: state.status == ConnectionStatus.connected
              ? widget.store.disconnect
              : () => widget.store.connect(host: hostController.text, port: int.tryParse(portController.text)),
          child: Text(connectLabel),
        ),
        if (hasError && state.isRetryable) ...[
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => widget.store.retryConnect(host: hostController.text, port: int.tryParse(portController.text)),
            style: OutlinedButton.styleFrom(
              side: BorderSide(color: errorColor, width: 2),
              foregroundColor: errorColor,
            ),
            icon: const Icon(Icons.refresh),
            label: const Text('Повторить'),
          ),
        ],
        const SizedBox(height: 12),
        Text(_statusText(state)),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          onPressed: () => _showTroubleshootingSheet(state),
          style: OutlinedButton.styleFrom(
            side: BorderSide(color: hasError ? errorColor : Theme.of(context).colorScheme.outline),
            foregroundColor: hasError ? errorColor : null,
          ),
          icon: const Icon(Icons.help_outline),
          label: const Text('Что делать, если не подключается?'),
        ),
      ],
    );
  }

  Future<void> _showTroubleshootingSheet(AppConnectionState state) async {
    final steps = state.troubleshootingSteps.isEmpty ? AppStore.troubleshootingSteps : state.troubleshootingSteps;
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (_) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Не подключается', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
              const SizedBox(height: 12),
              ...steps.map((step) => Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text('• $step'),
                  )),
              const SizedBox(height: 8),
              const Text('Подробная инструкция: https://avtodigix.tilda.ws/instruction'),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _openSystemSetting(SystemQuickAction action) async {
    final platform = Theme.of(context).platform;
    final candidates = switch (action) {
      SystemQuickAction.bluetooth => <Uri>[
          if (platform == TargetPlatform.iOS) Uri.parse('App-Prefs:Bluetooth'),
          Uri.parse('app-settings:'),
        ],
      SystemQuickAction.wifi => <Uri>[
          if (platform == TargetPlatform.iOS) Uri.parse('App-Prefs:WIFI'),
          Uri.parse('app-settings:'),
        ],
    };

    for (final uri in candidates) {
      try {
        if (await canLaunchUrl(uri) && await launchUrl(uri, mode: LaunchMode.externalApplication)) {
          return;
        }
      } catch (_) {
        // fallback to next option
      }
    }

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Не удалось открыть настройки. Откройте их вручную.')),
    );
  }

  String _statusText(AppConnectionState state) {
    switch (state.status) {
      case ConnectionStatus.connected:
        return '${AppStore.statusConnectedPrefix} ${state.selectedDeviceName ?? state.wifiResolvedEndpoint ?? AppStore.unknownTarget}';
      case ConnectionStatus.connecting:
        return AppStore.statusConnecting;
      case ConnectionStatus.error:
        return '${AppStore.statusErrorPrefix} ${state.errorMessage ?? AppStore.unknownError}';
      default:
        return AppStore.statusDisconnected;
    }
  }
}

enum SystemQuickAction { bluetooth, wifi }

class _DataTab extends StatelessWidget {
  const _DataTab({required this.store});
  final AppStore store;

  @override
  Widget build(BuildContext context) {
    final connected = store.connectionState.status == ConnectionStatus.connected;
    if (!connected) {
      return const Center(child: Text('Подключитесь к адаптеру для получения метрик.'));
    }
    final obd = store.obdState;
    final f = DateFormat('HH:mm:ss');
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('MIL: ${obd.milOn ? 'активен' : 'не активен'}'),
        Text('DTC: ${obd.storedDtcs.length + obd.pendingDtcs.length}'),
        Text('Readiness: ${obd.readinessCount}'),
        Text('Поддержка PID: ${obd.supportedPids}'),
        const Divider(),
        Text('RPM: ${obd.metrics?.rpm ?? '—'}'),
        Text('Скорость: ${obd.metrics?.speed ?? '—'}'),
        Text('Темп. ОЖ: ${obd.metrics?.coolantTemp ?? '—'}'),
        Text('АКБ: ${obd.metrics == null ? '—' : obd.metrics!.battery.toStringAsFixed(1)}'),
        const Divider(),
        Text('Обновлено: ${obd.lastUpdated == null ? '—' : f.format(obd.lastUpdated!)}'),
      ],
    );
  }
}

class _IssuesHistoryTab extends StatefulWidget {
  const _IssuesHistoryTab({required this.store});
  final AppStore store;

  @override
  State<_IssuesHistoryTab> createState() => _IssuesHistoryTabState();
}

class _IssuesHistoryTabState extends State<_IssuesHistoryTab> with SingleTickerProviderStateMixin {
  late TabController tabController;

  @override
  void initState() {
    super.initState();
    tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TabBar(controller: tabController, tabs: const [Tab(text: 'DTC'), Tab(text: 'Отчёты')]),
        Expanded(
          child: TabBarView(
            controller: tabController,
            children: [
              _DtcTab(store: widget.store),
              _ReportsTab(store: widget.store),
            ],
          ),
        ),
      ],
    );
  }
}

class _DtcTab extends StatelessWidget {
  const _DtcTab({required this.store});
  final AppStore store;

  Future<void> _shareCurrentReport(BuildContext context) async {
    final report = buildCurrentDtcReport(
      stored: store.obdState.storedDtcs,
      pending: store.obdState.pendingDtcs,
    );
    await Share.share(report, subject: 'Текущий отчёт DTC');
  }

  Future<void> _showRecommendationsSheet(BuildContext context) async {
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (context) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Что делать с ошибками',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 12),
                ...List.generate(
                  dtcRecommendations.length,
                  (index) => Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text('${index + 1}. ${dtcRecommendations[index]}'),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<void> _confirmAndClearErrors(BuildContext context) async {
    final shouldClear = await showDialog<bool>(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          content: const Text('Очистить коды ошибок? Это действие нельзя отменить.'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('Отмена'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: const Text('Очистить ошибки'),
            ),
          ],
        );
      },
    );

    if (shouldClear == true) {
      await store.clearErrors();
    }
  }

  @override
  Widget build(BuildContext context) {
    final all = [...store.obdState.storedDtcs, ...store.obdState.pendingDtcs];
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Stored: ${store.obdState.storedDtcs.length}, Pending: ${store.obdState.pendingDtcs.length}'),
        const SizedBox(height: 8),
        ...all.map((e) => ListTile(title: Text(e), subtitle: const Text('Описание недоступно'))),
        if (all.isEmpty) const ListTile(title: Text('Нет кодов')),
        const SizedBox(height: 16),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            FilledButton(onPressed: store.saveReport, child: const Text('Сохранить отчёт')),
            OutlinedButton(onPressed: () => _shareCurrentReport(context), child: const Text('Поделиться')),
            OutlinedButton(onPressed: () => _confirmAndClearErrors(context), child: const Text('Очистить ошибки')),
            TextButton(
              onPressed: () => _showRecommendationsSheet(context),
              child: const Text('Что делать с ошибками'),
            ),
          ],
        ),
      ],
    );
  }
}

class _ReportsTab extends StatelessWidget {
  const _ReportsTab({required this.store});
  final AppStore store;

  Future<void> _shareSnapshot(ScanSnapshot snapshot) async {
    final formatter = DateFormat('dd.MM.yyyy HH:mm');
    final report = buildSnapshotReport(
      snapshot: snapshot,
      formattedTimestamp: formatter.format(snapshot.timestamp),
    );
    await Share.share(report, subject: 'Отчёт из истории');
  }

  @override
  Widget build(BuildContext context) {
    if (store.snapshots.isEmpty) return const Center(child: Text('История пока пуста'));
    final f = DateFormat('dd.MM.yyyy HH:mm');
    return ListView.builder(
      itemCount: store.snapshots.length,
      itemBuilder: (context, index) {
        final s = store.snapshots[index];
        return ListTile(
          title: Text(f.format(s.timestamp)),
          subtitle: Text('DTC: ${s.dtcs.length} · Статус: ${s.dtcs.isEmpty ? 'OK' : 'Есть ошибки'}'),
          onTap: () {
            Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => ReportDetails(snapshot: s),
              ),
            );
          },
          trailing: PopupMenuButton<_ReportAction>(
            onSelected: (action) async {
              if (action == _ReportAction.open) {
                Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => ReportDetails(snapshot: s),
                  ),
                );
              } else if (action == _ReportAction.share) {
                await _shareSnapshot(s);
              }
            },
            itemBuilder: (context) => const [
              PopupMenuItem(
                value: _ReportAction.open,
                child: Text('Открыть'),
              ),
              PopupMenuItem(
                value: _ReportAction.share,
                child: Text('Поделиться'),
              ),
            ],
          ),
        );
      },
    );
  }
}

enum _ReportAction { open, share }

class _SettingsTab extends StatelessWidget {
  const _SettingsTab({required this.store});
  final AppStore store;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('Режим пользователя'),
        RadioListTile<UserMode>(
          value: UserMode.novice,
          groupValue: store.userMode,
          title: const Text('Новичок'),
          onChanged: (v) => store.setUserMode(v!),
        ),
        RadioListTile<UserMode>(
          value: UserMode.professional,
          groupValue: store.userMode,
          title: const Text('Профессионал'),
          onChanged: (v) => store.setUserMode(v!),
        ),
        SwitchListTile(
          value: store.diagnosticsMode,
          onChanged: store.setDiagnosticsMode,
          title: const Text('Режим диагностики'),
        ),
        if (store.userMode == UserMode.professional && store.diagnosticsMode) ...[
          Text('Последняя команда: ${store.obdState.lastCommand ?? '—'}'),
          Text('Сырой ответ: ${store.obdState.lastRawResponse ?? '—'}'),
          Text('Ошибка: ${store.obdState.lastError}'),
        ],
      ],
    );
  }
}
