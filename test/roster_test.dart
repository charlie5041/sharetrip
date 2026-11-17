import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/main.dart';
import 'package:sharetrip/screens/join_trip_screen.dart';
import 'package:sharetrip/screens/trip_form_screen.dart';
import 'package:sharetrip/screens/trip_screen.dart';
import 'package:sharetrip/services/exchange_rates.dart';
import 'package:sharetrip/services/trip_repository.dart';
import 'package:sharetrip/theme.dart';

Widget _app(TripRepository repo, Widget home) => AppScope(
      repo: repo,
      rates: ExchangeRates(),
      child: MaterialApp(theme: buildAppTheme(), home: home),
    );

void main() {
  late FakeFirebaseFirestore db;
  late TripRepository alice;
  late TripRepository bob;

  setUp(() {
    db = FakeFirebaseFirestore();
    alice = TripRepository(firestore: db, uid: 'alice');
    bob = TripRepository(firestore: db, uid: 'bob');
  });

  Future<String> newTrip() => alice.createTrip(
        name: '東京',
        baseCurrency: 'TWD',
        startDate: '2026-12-20',
        endDate: '2026-12-25',
        nickname: 'Alice',
        others: ['Bob'],
      );

  testWidgets('the trip form takes the member list', (tester) async {
    await tester.pumpWidget(_app(alice, const TripFormScreen(nickname: 'Alice')));
    await tester.enterText(find.widgetWithText(TextField, '朋友的名字'), 'Bob');
    await tester.tap(find.byTooltip('加進名單'));
    await tester.pump();
    expect(find.text('Bob'), findsOneWidget);
    expect(find.text('Alice（我）'), findsOneWidget);
  });

  testWidgets('a friend joins by picking their name', (tester) async {
    final id = await newTrip();
    final code = await alice.createInvite(id);
    await tester.pumpWidget(_app(bob, const JoinTripScreen(nickname: 'B')));
    await tester.enterText(find.byType(TextField), code);
    await tester.tap(find.text('下一步'));
    await tester.pumpAndSettle();
    expect(find.text('你是哪一位？'), findsOneWidget);
    await tester.tap(find.text('Bob'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('是我'));
    // The trip screen that opens next animates for ever, so no settle.
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    final members = await bob.watchMembers(id).first;
    expect(members.firstWhere((m) => m.name == 'Bob').uid, 'bob');
  });

  testWidgets('only the owner manages members', (tester) async {
    final id = await newTrip();
    final pending = await bob.checkInvite(await alice.createInvite(id));
    await bob.join(pending, (await bob.openPlaces(pending)).single.id);

    await tester.pumpWidget(_app(alice, TripScreen(tripId: id, initialTab: 4)));
    await tester.pumpAndSettle();
    expect(find.text('加入成員'), findsOneWidget);
    expect(find.text('解散旅程'), findsOneWidget);
    expect(find.text('群主'), findsOneWidget);

    await tester.pumpWidget(_app(bob, TripScreen(tripId: id, initialTab: 4)));
    await tester.pumpAndSettle();
    expect(find.text('加入成員'), findsNothing);
    expect(find.text('解散旅程'), findsNothing);
  });
}
