const List<String> dtcRecommendations = [
  'Сначала устраните причину ошибки, а не только код.',
  'Pending ошибки наблюдайте после 1–2 поездок.',
  'Сохраните отчёт перед ремонтом для сравнения.',
  'Очищайте ошибки только после выполнения работ.',
];

String buildCurrentDtcReport({
  required List<String> stored,
  required List<String> pending,
}) {
  final summary = 'Stored: ${stored.length}, Pending: ${pending.length}';
  final dtcLines = <String>{
    ...stored.map((code) => '$code (Stored): Описание недоступно'),
    ...pending.map((code) => '$code (Pending): Описание недоступно'),
  }.join('\n');

  final normalizedDtcLines = dtcLines.isEmpty ? 'Нет кодов' : dtcLines;

  return [
    'Текущий отчёт DTC',
    summary,
    '',
    'Коды ошибок',
    normalizedDtcLines,
  ].join('\n');
}
