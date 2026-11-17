import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/main.dart';
import 'package:sharetrip/models/expense.dart';
import 'package:sharetrip/screens/trip_screen.dart';
import 'package:sharetrip/services/exchange_rates.dart';
import 'package:sharetrip/services/trip_repository.dart';
import 'package:sharetrip/theme.dart';

void main() {
  testWidgets('an expense shows up in the list and in the settle-up plan',
      (tester) async {
    final repo = TripRepository(firestore: FakeFirebaseFirestore(), uid: 'me');
    final id = await repo.createTrip(
      name: '大阪',
      baseCurrency: 'TWD',
      startDate: '2026-12-20',
      endDate: '2026-12-24',
      nickname: '小明',
    );
    await repo.addNameOnlyMember(id, '阿華');
    final members = await repo.watchMembers(id).first;
    await repo.saveExpense(
      id,
      Expense(
        id: '',
        title: '環球影城門票',
        category: ExpenseCategory.ticket,
        amountMinor: 20000,
        currency: 'JPY',
        rateToBase: '0.2',
        payerId: 'me',
        splitMode: SplitMode.equal,
        splits: {for (final m in members) m.id: 1},
        occurredAt: DateTime(2026, 12, 21, 9).millisecondsSinceEpoch,
        createdBy: '',
        updatedAt: 0,
      ),
    );

    await tester.pumpWidget(AppScope(
      repo: repo,
      rates: ExchangeRates(),
      child: MaterialApp(theme: buildAppTheme(), home: TripScreen(tripId: id)),
    ));
    await tester.pumpAndSettle();

    expect(find.text('環球影城門票'), findsOneWidget);
    expect(find.text('20000 JPY'), findsOneWidget);
    expect(find.text('≈ 4000 TWD'), findsOneWidget);

    await tester.tap(find.text('結算'));
    await tester.pumpAndSettle();
    expect(find.text('阿華 → 小明'), findsOneWidget);
    expect(find.text('2000 TWD'), findsOneWidget);

    await tester.tap(find.text('已轉帳'));
    await tester.pumpAndSettle();
    expect(find.text('大家都結清了 🎉'), findsOneWidget);
  });

  testWidgets('a form error clears as soon as the field is edited',
      (tester) async {
    final repo = TripRepository(firestore: FakeFirebaseFirestore(), uid: 'me');
    final id = await repo.createTrip(
      name: '大阪',
      baseCurrency: 'TWD',
      startDate: '2026-12-20',
      endDate: '2026-12-24',
      nickname: '小明',
    );
    await tester.pumpWidget(AppScope(
      repo: repo,
      rates: ExchangeRates(),
      child: MaterialApp(theme: buildAppTheme(), home: TripScreen(tripId: id)),
    ));
    await tester.pumpAndSettle();
    await tester.tap(find.text('記一筆'));
    await tester.pumpAndSettle();
    final save = find.widgetWithText(FilledButton, '儲存');
    await tester.ensureVisible(save);
    await tester.pumpAndSettle();
    await tester.tap(save);
    await tester.pump();
    expect(find.text('請填項目名稱', skipOffstage: false), findsOneWidget);

    await tester.enterText(find.byType(TextField).first, '拉麵');
    await tester.pump();
    expect(find.text('請填項目名稱', skipOffstage: false), findsNothing);
  });
}
