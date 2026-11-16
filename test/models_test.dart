import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/models/currency.dart';
import 'package:sharetrip/models/expense.dart';
import 'package:sharetrip/models/itinerary_item.dart';
import 'package:sharetrip/models/trip.dart';

void main() {
  group('Currency', () {
    test('formats and parses minor units', () {
      final usd = Currency.of('USD');
      expect(usd.format(12345), '123.45');
      expect(usd.format(5), '0.05');
      expect(usd.format(-250), '-2.50');
      expect(usd.parse('123.45'), 12345);
      expect(usd.parse('1,000.5'), 100050);
      expect(usd.parse('1.234'), isNull);
      expect(usd.parse('abc'), isNull);
    });

    test('TWD and JPY carry no decimals', () {
      expect(Currency.of('TWD').decimals, 0);
      expect(Currency.of('JPY').parse('1500'), 1500);
      expect(Currency.of('JPY').parse('1500.5'), isNull);
    });

    test('unknown code throws', () {
      expect(() => Currency.of('XXX'), throwsArgumentError);
    });
  });

  test('Expense round-trips through a map', () {
    const e = Expense(
      id: 'e1',
      title: '一蘭拉麵',
      category: ExpenseCategory.food,
      amountMinor: 3960,
      currency: 'JPY',
      rateToBase: '0.2134',
      payerId: 'm1',
      splitMode: SplitMode.shares,
      splits: {'m1': 2, 'm2': 1},
      occurredAt: 1766200000000,
      createdBy: 'uid1',
      updatedAt: 1766200000000,
      note: '宵夜',
    );
    final back = Expense.fromMap('e1', e.toMap());
    expect(back.toMap(), e.toMap());
  });

  test('Trip, Member and ItineraryItem round-trip through a map', () {
    const t = Trip(
      id: 't1',
      name: '東京',
      baseCurrency: 'TWD',
      startDate: '2026-12-20',
      endDate: '2026-12-25',
      ownerUid: 'uid1',
      memberUids: ['uid1'],
      createdAt: 1,
    );
    expect(Trip.fromMap('t1', t.toMap()).toMap(), t.toMap());

    const m = Member(id: 'm2', name: '阿嬤', order: 1, joinedAt: 2);
    expect(Member.fromMap('m2', m.toMap()).toMap(), m.toMap());

    const i = ItineraryItem(
        id: 'i1', date: '2026-12-21', time: '09:30', title: '淺草寺', order: 0);
    expect(ItineraryItem.fromMap('i1', i.toMap()).toMap(), i.toMap());
  });
}
