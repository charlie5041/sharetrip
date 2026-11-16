/// Currencies the app offers, with how many decimal places their amounts
/// carry. Amounts are stored as integers in these minor units.
///
/// TWD and JPY are treated as having no decimals: ISO 4217 gives TWD two,
/// but nobody pays in 角 any more, and a 0.5 that cannot be paid only
/// produces a settlement nobody can carry out.
class Currency {
  const Currency(this.code, this.name, this.decimals);

  final String code;
  final String name;
  final int decimals;

  static const all = <Currency>[
    Currency('TWD', '新台幣', 0),
    Currency('JPY', '日圓', 0),
    Currency('KRW', '韓元', 0),
    Currency('USD', '美元', 2),
    Currency('EUR', '歐元', 2),
    Currency('HKD', '港幣', 2),
    Currency('CNY', '人民幣', 2),
    Currency('THB', '泰銖', 2),
    Currency('SGD', '新加坡幣', 2),
    Currency('VND', '越南盾', 0),
    Currency('GBP', '英鎊', 2),
    Currency('AUD', '澳幣', 2),
  ];

  static Currency of(String code) => all.firstWhere(
        (c) => c.code == code,
        orElse: () => throw ArgumentError.value(code, 'code', '不支援的幣別'),
      );

  /// Integer minor units → a display string, e.g. 12345 USD → "123.45".
  String format(int amountMinor) {
    if (decimals == 0) return amountMinor.toString();
    final neg = amountMinor < 0;
    final s = amountMinor.abs().toString().padLeft(decimals + 1, '0');
    final whole = s.substring(0, s.length - decimals);
    final frac = s.substring(s.length - decimals);
    return '${neg ? '-' : ''}$whole.$frac';
  }

  /// A typed amount → integer minor units, or null when it is not a valid
  /// amount in this currency (too many decimals, not a number).
  int? parse(String input) {
    final t = input.trim().replaceAll(',', '');
    final m = RegExp(r'^(\d+)(?:\.(\d*))?$').firstMatch(t);
    if (m == null) return null;
    final frac = m.group(2) ?? '';
    if (frac.length > decimals) return null;
    return int.parse(m.group(1)! + frac.padRight(decimals, '0'));
  }
}
