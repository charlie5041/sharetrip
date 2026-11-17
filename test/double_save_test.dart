import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/main.dart';
import 'package:sharetrip/models/travel.dart';
import 'package:sharetrip/screens/travel_forms.dart';
import 'package:sharetrip/services/exchange_rates.dart';
import 'package:sharetrip/services/trip_repository.dart';
import 'package:sharetrip/theme.dart';

void main() {
  testWidgets('tapping Save several times saves the flight once',
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
      child: MaterialApp(
        theme: buildAppTheme(),
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(
              builder: (_) => FlightFormScreen(
                tripId: id,
                // A filled-in form, as if copied, so Save goes through.
                existing: const Flight(
                  id: '',
                  flightNo: 'BR132',
                  fromCode: 'TPE',
                  toCode: 'KIX',
                  departDate: '2026-12-20',
                  departTime: '08:30',
                ),
              ),
            )),
            child: const Text('open'),
          ),
        ),
      ),
    ));
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    final save = find.widgetWithText(FilledButton, '儲存');
    await tester.ensureVisible(save);
    await tester.pumpAndSettle();
    // Five taps landing before the page has gone, as on a slow phone.
    final onPressed = tester.widget<FilledButton>(save).onPressed!;
    for (var i = 0; i < 5; i++) {
      onPressed();
    }
    await tester.pumpAndSettle();

    expect(await repo.watchFlights(id).first, hasLength(1));
  });

  testWidgets('taps after the form closes do not pop further pages',
      (tester) async {
    final repo = TripRepository(firestore: FakeFirebaseFirestore(), uid: 'me');
    final id = await repo.createTrip(
      name: '大阪',
      baseCurrency: 'TWD',
      startDate: '2026-12-20',
      endDate: '2026-12-24',
      nickname: '小明',
    );
    final nav = GlobalKey<NavigatorState>();
    await tester.pumpWidget(AppScope(
      repo: repo,
      rates: ExchangeRates(),
      child: MaterialApp(
        navigatorKey: nav,
        theme: buildAppTheme(),
        home: const Scaffold(body: Text('home')),
      ),
    ));
    nav.currentState!.push(MaterialPageRoute(
        builder: (_) => const Scaffold(body: Text('middle'))));
    await tester.pumpAndSettle();
    nav.currentState!.push(MaterialPageRoute(
      builder: (_) => FlightFormScreen(
        tripId: id,
        existing: const Flight(
          id: '',
          flightNo: 'BR132',
          fromCode: 'TPE',
          toCode: 'KIX',
          departDate: '2026-12-20',
          departTime: '08:30',
        ),
      ),
    ));
    await tester.pumpAndSettle();
    final save = find.widgetWithText(FilledButton, '儲存');
    await tester.ensureVisible(save);
    await tester.pumpAndSettle();
    final at = tester.getCenter(save);
    for (var i = 0; i < 5; i++) {
      await tester.tapAt(at);
      await tester.pump(const Duration(milliseconds: 60));
    }
    await tester.pumpAndSettle();
    expect(find.text('middle'), findsOneWidget);
  });
}
