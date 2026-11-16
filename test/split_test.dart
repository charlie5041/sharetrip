import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/logic/split.dart';
import 'package:sharetrip/models/currency.dart';
import 'package:sharetrip/models/expense.dart';
import 'package:sharetrip/models/trip.dart';

final twd = Currency.of('TWD');
final jpy = Currency.of('JPY');
final usd = Currency.of('USD');

const members = [
  Member(id: 'a', name: 'A', order: 0, joinedAt: 0),
  Member(id: 'b', name: 'B', order: 1, joinedAt: 0),
  Member(id: 'c', name: 'C', order: 2, joinedAt: 0),
];
const order = ['a', 'b', 'c'];

Expense exp({
  int amount = 300,
  String currency = 'TWD',
  String rate = '1',
  String payer = 'a',
  SplitMode mode = SplitMode.equal,
  Map<String, int> splits = const {'a': 1, 'b': 1, 'c': 1},
}) =>
    Expense(
      id: 'x',
      title: 't',
      category: ExpenseCategory.food,
      amountMinor: amount,
      currency: currency,
      rateToBase: rate,
      payerId: payer,
      splitMode: mode,
      splits: splits,
      occurredAt: 0,
      createdBy: 'u',
      updatedAt: 0,
    );

int sum(Map<String, int> m) => m.values.fold(0, (a, b) => a + b);

void main() {
  group('allocate', () {
    test('ties in the remainder go in order', () {
      expect(allocate(100, {'a': 1, 'b': 1, 'c': 1}), {'a': 34, 'b': 33, 'c': 33});
      expect(allocate(101, {'c': 1, 'a': 1, 'b': 1}), {'c': 34, 'a': 34, 'b': 33});
    });

    test('splits by shares', () {
      expect(allocate(300, {'a': 2, 'b': 1}), {'a': 200, 'b': 100});
      expect(allocate(100, {'a': 2, 'b': 1}), {'a': 67, 'b': 33});
      // 16.67 / 66.67 / 16.67: equal remainders, so the first two in order.
      expect(allocate(100, {'a': 1, 'b': 4, 'c': 1}), {'a': 17, 'b': 67, 'c': 16});
    });

    test('always sums to the total', () {
      for (var t = 0; t < 500; t += 7) {
        expect(sum(allocate(t, {'a': 3, 'b': 5, 'c': 7})), t);
      }
    });

    test('rejects nobody participating', () {
      expect(() => allocate(100, {'a': 0}), throwsA(isA<SplitError>()));
    });
  });

  group('sharesInOriginal', () {
    test('equal ignores the weights and follows member order', () {
      final e = exp(amount: 100, splits: {'c': 5, 'a': 1});
      expect(sharesInOriginal(e, order), {'a': 50, 'c': 50});
    });

    test('exact must add up', () {
      final ok = exp(mode: SplitMode.exact, splits: {'a': 100, 'b': 200});
      expect(sharesInOriginal(ok, order), {'a': 100, 'b': 200});
      final bad = exp(mode: SplitMode.exact, splits: {'a': 100, 'b': 100});
      expect(() => sharesInOriginal(bad, order), throwsA(isA<SplitError>()));
    });
  });

  group('convert', () {
    test('JPY to TWD rounds half up', () {
      // 3960 × 0.2134 = 845.064
      expect(convert(3960, jpy, twd, Rate.parse('0.2134')), 845);
      // 1000 × 0.2135 = 213.5
      expect(convert(1000, jpy, twd, Rate.parse('0.2135')), 214);
    });

    test('handles different decimal places', () {
      // USD 12.34 × 32.5 = 401.05 TWD
      expect(convert(1234, usd, twd, Rate.parse('32.5')), 401);
      // TWD 1000 × 0.0308 = USD 30.80
      expect(convert(1000, twd, usd, Rate.parse('0.0308')), 3080);
    });

    test('rejects malformed rates', () {
      expect(Rate.tryParse('abc'), isNull);
      expect(Rate.tryParse('0'), isNull);
      expect(Rate.tryParse('-1'), isNull);
      expect(Rate.tryParse('1.5'), isNotNull);
    });
  });

  group('balances', () {
    test('a foreign expense credits the payer exactly what others owe', () {
      final e = exp(amount: 1000, currency: 'JPY', rate: '0.2134');
      final net = balances(
          members: members, expenses: [e], settlements: [], base: twd);
      // 1000 JPY splits 334/333/333; 213 TWD then splits 71/71/71.
      expect(net, {'a': 142, 'b': -71, 'c': -71});
      expect(sum(net), 0);
    });

    test('a base-currency expense ignores its stored rate', () {
      final e = exp(amount: 300, rate: '999');
      final net = balances(
          members: members, expenses: [e], settlements: [], base: twd);
      expect(net, {'a': 200, 'b': -100, 'c': -100});
    });

    test('settlements move balances toward zero', () {
      final e = exp(amount: 300);
      const s = Settlement(
          id: 's', fromId: 'b', toId: 'a', amountMinor: 100, at: 0, createdBy: 'u');
      final net = balances(
          members: members, expenses: [e], settlements: [s], base: twd);
      expect(net, {'a': 100, 'b': 0, 'c': -100});
    });

    test('mixed currencies always sum to zero', () {
      final expenses = [
        exp(amount: 3960, currency: 'JPY', rate: '0.2134', payer: 'b'),
        exp(amount: 1234, currency: 'USD', rate: '32.5', payer: 'c',
            mode: SplitMode.shares, splits: {'a': 2, 'b': 1, 'c': 1}),
        exp(amount: 777, payer: 'a', splits: {'b': 1, 'c': 1}),
      ];
      final net = balances(
          members: members, expenses: expenses, settlements: [], base: twd);
      expect(sum(net), 0);
    });
  });

  group('suggestTransfers', () {
    test('settles everyone with at most n − 1 transfers', () {
      final t = suggestTransfers({'a': 500, 'b': -200, 'c': -300});
      expect(t, [const Transfer('c', 'a', 300), const Transfer('b', 'a', 200)]);
    });

    test('nothing to do when everyone is even', () {
      expect(suggestTransfers({'a': 0, 'b': 0}), isEmpty);
    });

    test('applying the transfers zeroes every balance', () {
      final net = {'a': 120, 'b': -45, 'c': 80, 'd': -155, 'e': 0};
      final after = Map.of(net);
      final t = suggestTransfers(net);
      for (final x in t) {
        after[x.fromId] = after[x.fromId]! + x.amountMinor;
        after[x.toId] = after[x.toId]! - x.amountMinor;
      }
      expect(after.values.every((v) => v == 0), isTrue);
      expect(t.length, lessThanOrEqualTo(net.length - 1));
    });
  });
}
