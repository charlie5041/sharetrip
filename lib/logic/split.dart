import '../models/currency.dart';
import '../models/expense.dart';
import '../models/trip.dart';

/// Thrown when an expense's splits cannot produce a valid allocation.
class SplitError implements Exception {
  const SplitError(this.message);
  final String message;
  @override
  String toString() => message;
}

/// A decimal string ("0.2134") as an exact fraction, so converting money
/// never goes through a double.
class Rate {
  Rate._(this.numerator, this.denominator);

  final BigInt numerator;
  final BigInt denominator;

  static final _pattern = RegExp(r'^(\d+)(?:\.(\d+))?$');

  /// Null when [text] is not a positive decimal number.
  static Rate? tryParse(String text) {
    final m = _pattern.firstMatch(text.trim());
    if (m == null) return null;
    final frac = m.group(2) ?? '';
    final num = BigInt.parse(m.group(1)! + frac);
    if (num == BigInt.zero) return null;
    return Rate._(num, BigInt.from(10).pow(frac.length));
  }

  static Rate parse(String text) =>
      tryParse(text) ?? (throw SplitError('匯率格式不正確：$text'));

  static final one = Rate._(BigInt.one, BigInt.one);
}

/// Splits [total] in proportion to [weights] by largest remainder: every
/// share is rounded down, then the members whose shares lost the most get
/// one more unit each until the sum is exact. Ties go to whoever comes
/// first in [weights]' key order, which callers set to the members' order.
Map<String, int> allocate(int total, Map<String, int> weights) {
  if (total < 0) throw const SplitError('金額不能是負數');
  final entries = weights.entries.where((e) => e.value > 0).toList();
  if (entries.isEmpty) throw const SplitError('至少要有一個人分攤');
  final sumW =
      entries.fold<BigInt>(BigInt.zero, (s, e) => s + BigInt.from(e.value));
  final t = BigInt.from(total);
  final out = <String, int>{};
  final remainders = <int, BigInt>{};
  var given = 0;
  for (var i = 0; i < entries.length; i++) {
    final p = t * BigInt.from(entries[i].value);
    final v = (p ~/ sumW).toInt();
    out[entries[i].key] = v;
    remainders[i] = p % sumW;
    given += v;
  }
  // The leftover is smaller than the number of participants.
  final byRemainder = List.generate(entries.length, (i) => i)
    ..sort((a, b) {
      final c = remainders[b]!.compareTo(remainders[a]!);
      return c != 0 ? c : a.compareTo(b);
    });
  for (var j = 0; j < total - given; j++) {
    final k = entries[byRemainder[j]].key;
    out[k] = out[k]! + 1;
  }
  return out;
}

/// Each participant's share of [e] in its own currency's minor units.
/// [order] lists member ids in display order.
Map<String, int> sharesInOriginal(Expense e, List<String> order) {
  final ordered = <String, int>{
    for (final id in order)
      if ((e.splits[id] ?? 0) > 0) id: e.splits[id]!,
  };
  switch (e.splitMode) {
    case SplitMode.equal:
      return allocate(e.amountMinor, ordered.map((k, _) => MapEntry(k, 1)));
    case SplitMode.shares:
      return allocate(e.amountMinor, ordered);
    case SplitMode.exact:
      final sum = ordered.values.fold(0, (a, b) => a + b);
      if (sum != e.amountMinor) {
        throw SplitError('各人金額加總（$sum）不等於總額（${e.amountMinor}）');
      }
      if (ordered.isEmpty) throw const SplitError('至少要有一個人分攤');
      return ordered;
  }
}

/// [amountMinor] of [from] converted to [to] minor units at [rate],
/// rounded half up.
int convert(int amountMinor, Currency from, Currency to, Rate rate) {
  final scale = to.decimals - from.decimals;
  var num = BigInt.from(amountMinor) * rate.numerator;
  var den = rate.denominator;
  if (scale >= 0) {
    num *= BigInt.from(10).pow(scale);
  } else {
    den *= BigInt.from(10).pow(-scale);
  }
  return ((num * BigInt.two + den) ~/ (den * BigInt.two)).toInt();
}

/// What [e] costs each participant in the trip's [base] currency.
///
/// The total is converted once and then split in proportion to the shares
/// in the original currency, so the payer is credited exactly what the
/// participants are charged — converting each share on its own could leave
/// them a unit apart.
Map<String, int> sharesInBase(Expense e, Currency base, List<String> order) {
  final original = sharesInOriginal(e, order);
  final totalBase = baseTotal(e, base);
  if (totalBase == 0) return original.map((k, _) => MapEntry(k, 0));
  return allocate(totalBase, original);
}

/// [e]'s total in the trip's [base] currency.
int baseTotal(Expense e, Currency base) {
  final from = Currency.of(e.currency);
  final rate = e.currency == base.code ? Rate.one : Rate.parse(e.rateToBase);
  return convert(e.amountMinor, from, base, rate);
}

/// Net position of every member in base minor units: positive means the
/// others owe them. The values always sum to zero.
Map<String, int> balances({
  required List<Member> members,
  required List<Expense> expenses,
  required List<Settlement> settlements,
  required Currency base,
}) {
  final sorted = [...members]..sort((a, b) => a.order.compareTo(b.order));
  final order = [for (final m in sorted) m.id];
  final net = {for (final id in order) id: 0};
  void add(String id, int v) => net[id] = (net[id] ?? 0) + v;

  for (final e in expenses) {
    final shares = sharesInBase(e, base, order);
    add(e.payerId, shares.values.fold(0, (a, b) => a + b));
    shares.forEach((id, v) => add(id, -v));
  }
  for (final s in settlements) {
    add(s.fromId, s.amountMinor);
    add(s.toId, -s.amountMinor);
  }
  return net;
}

class Transfer {
  const Transfer(this.fromId, this.toId, this.amountMinor);
  final String fromId;
  final String toId;
  final int amountMinor;

  @override
  bool operator ==(Object other) =>
      other is Transfer &&
      other.fromId == fromId &&
      other.toId == toId &&
      other.amountMinor == amountMinor;

  @override
  int get hashCode => Object.hash(fromId, toId, amountMinor);

  @override
  String toString() => '$fromId → $toId: $amountMinor';
}

/// Who should pay whom to bring every balance to zero: the largest debtor
/// repeatedly pays the largest creditor. At most n − 1 transfers; not
/// always the fewest possible (finding that is NP-hard), which does not
/// matter for a group of friends.
List<Transfer> suggestTransfers(Map<String, int> net) {
  final debtors = [
    for (final e in net.entries)
      if (e.value < 0) MapEntry(e.key, -e.value)
  ];
  final creditors = [
    for (final e in net.entries)
      if (e.value > 0) MapEntry(e.key, e.value)
  ];
  final out = <Transfer>[];
  while (debtors.isNotEmpty && creditors.isNotEmpty) {
    debtors.sort((a, b) => b.value.compareTo(a.value));
    creditors.sort((a, b) => b.value.compareTo(a.value));
    final d = debtors.first, c = creditors.first;
    final amt = d.value < c.value ? d.value : c.value;
    out.add(Transfer(d.key, c.key, amt));
    debtors[0] = MapEntry(d.key, d.value - amt);
    creditors[0] = MapEntry(c.key, c.value - amt);
    debtors.removeWhere((e) => e.value == 0);
    creditors.removeWhere((e) => e.value == 0);
  }
  return out;
}
