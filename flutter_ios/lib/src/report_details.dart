import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'helpers/dtc_report_helper.dart';
import 'models/app_models.dart';

class ReportDetails extends StatelessWidget {
  const ReportDetails({required this.snapshot, super.key});

  final ScanSnapshot snapshot;

  @override
  Widget build(BuildContext context) {
    final formatter = DateFormat('dd.MM.yyyy HH:mm');
    final formattedTimestamp = formatter.format(snapshot.timestamp);
    final reportText = buildSnapshotReport(snapshot: snapshot, formattedTimestamp: formattedTimestamp);
    final status = snapshot.dtcs.isEmpty ? 'OK' : 'Есть ошибки';

    return Scaffold(
      appBar: AppBar(title: const Text('Детали отчёта')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _DetailCard(
            title: 'Время снимка',
            child: Text(formattedTimestamp),
          ),
          const SizedBox(height: 12),
          _DetailCard(
            title: 'Статус',
            child: Text(status),
          ),
          const SizedBox(height: 12),
          _DetailCard(
            title: 'DTC список',
            child: snapshot.dtcs.isEmpty
                ? const Text('Нет кодов')
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: snapshot.dtcs.map((code) => Text('• $code')).toList(growable: false),
                  ),
          ),
          const SizedBox(height: 12),
          _DetailCard(
            title: 'Ключевые метрики',
            child: snapshot.metrics.isEmpty
                ? const Text('—')
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: snapshot.metrics.entries
                        .map((entry) => Text('${entry.key}: ${entry.value}'))
                        .toList(growable: false),
                  ),
          ),
          const SizedBox(height: 16),
          const Text(
            'Формат для Android-версии',
            style: TextStyle(fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          SelectableText(reportText),
        ],
      ),
    );
  }
}

class _DetailCard extends StatelessWidget {
  const _DetailCard({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            child,
          ],
        ),
      ),
    );
  }
}
