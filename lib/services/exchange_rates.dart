import 'dart:convert';

import 'package:http/http.dart' as http;

/// Today's reference rate, as a starting point the user can overwrite with
/// what their card actually charged. Uses open.er-api.com's free endpoint,
/// which needs no key and updates daily.
class ExchangeRates {
  ExchangeRates({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;
  final _cache = <String, Map<String, num>>{};

  /// One unit of [from] in [to], as a decimal string, or null when offline
  /// or the service does not know the pair.
  Future<String?> rate(String from, String to) async {
    if (from == to) return '1';
    try {
      final table = _cache[from] ?? await _fetch(from);
      if (table == null) return null;
      _cache[from] = table;
      final r = table[to];
      return r == null ? null : _format(r.toDouble());
    } catch (_) {
      return null;
    }
  }

  Future<Map<String, num>?> _fetch(String base) async {
    final res = await _client
        .get(Uri.parse('https://open.er-api.com/v6/latest/$base'))
        .timeout(const Duration(seconds: 8));
    if (res.statusCode != 200) return null;
    final body = jsonDecode(res.body) as Map<String, dynamic>;
    if (body['result'] != 'success') return null;
    return Map<String, num>.from(body['rates'] as Map);
  }

  /// Six significant digits are plenty for a trip and keep the stored
  /// string readable.
  static String _format(double r) {
    final s = r.toStringAsPrecision(6);
    if (s.contains('e')) return r.toStringAsFixed(8);
    return s.contains('.') ? s.replaceFirst(RegExp(r'\.?0+$'), '') : s;
  }
}
