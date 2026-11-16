import 'package:intl/intl.dart';

final _ymd = DateFormat('yyyy-MM-dd');

String ymd(DateTime d) => _ymd.format(d);
DateTime parseYmd(String s) => _ymd.parse(s);

/// "12/20（六）"
String shortDate(String ymdText) {
  final d = parseYmd(ymdText);
  const w = ['一', '二', '三', '四', '五', '六', '日'];
  return '${d.month}/${d.day}（${w[d.weekday - 1]}）';
}

/// Whole days from [today] until [startYmd]; negative once it has begun.
int daysUntil(String startYmd, DateTime today) {
  final s = parseYmd(startYmd);
  final t = DateTime(today.year, today.month, today.day);
  return s.difference(t).inDays;
}
