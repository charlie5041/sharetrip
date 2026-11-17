import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/main.dart';
import 'package:sharetrip/models/itinerary_item.dart';
import 'package:sharetrip/models/travel.dart';
import 'package:sharetrip/screens/itinerary_tab.dart';
import 'package:sharetrip/screens/trip_screen.dart';
import 'package:sharetrip/services/exchange_rates.dart';
import 'package:sharetrip/services/trip_repository.dart';
import 'package:sharetrip/theme.dart';
import 'package:sharetrip/widgets/maps.dart';

Future<(TripRepository, String)> seed() async {
  final repo = TripRepository(firestore: FakeFirebaseFirestore(), uid: 'me');
  final id = await repo.createTrip(
    name: '東京五日',
    baseCurrency: 'TWD',
    startDate: '2030-12-20',
    endDate: '2030-12-22',
    nickname: '小明',
  );
  return (repo, id);
}

// Calm by default: the header illustration animates forever, which
// pumpAndSettle would wait on.
Widget app(TripRepository repo, String id, {int tab = 0, bool calm = true}) =>
    AppScope(
      repo: repo,
      rates: ExchangeRates(),
      child: MaterialApp(
        theme: buildAppTheme(),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: calm),
          child: child!,
        ),
        home: TripScreen(tripId: id, initialTab: tab),
      ),
    );

void main() {
  test('tripDays lists every date inclusive', () {
    expect(tripDays('2026-12-30', '2027-01-02'),
        ['2026-12-30', '2026-12-31', '2027-01-01', '2027-01-02']);
  });

  test('timed stops come first, untimed ones keep their order', () {
    ItineraryItem it(String id, String? time, int order) => ItineraryItem(
        id: id, date: 'd', title: id, time: time, order: order);
    final list = [it('c', null, 1), it('b', '10:00', 5), it('a', '09:00', 9),
        it('d', null, 0)]
      ..sort(ItineraryItem.compare);
    expect(list.map((e) => e.id), ['a', 'b', 'd', 'c']);
  });

  test('new fields round-trip, old documents still read', () {
    const i = ItineraryItem(
        id: 'i',
        date: '2030-12-20',
        title: '淺草寺',
        order: 1,
        transport: Transport.train,
        mustGo: true);
    final back = ItineraryItem.fromMap('i', i.toMap());
    expect(back.transport, Transport.train);
    expect(back.mustGo, isTrue);
    final old = ItineraryItem.fromMap(
        'o', {'date': '2030-12-20', 'title': 'x', 'order': 0});
    expect(old.transport, Transport.none);
    expect(old.mustGo, isFalse);

    const f = Flight(
        id: 'f',
        flightNo: 'BR198',
        fromCode: 'TPE',
        toCode: 'NRT',
        departDate: '2030-12-20',
        departTime: '08:50');
    expect(Flight.fromMap('f', f.toMap()).toMap(), f.toMap());
    const l = Lodging(
        id: 'l', name: '淺草飯店', checkIn: '2030-12-20', checkOut: '2030-12-22');
    expect(Lodging.fromMap('l', l.toMap()).toMap(), l.toMap());
  });

  test('map links search by place unless a link was saved', () {
    expect(mapUri(place: '淺草寺').toString(),
        contains('google.com/maps/search/?api=1&query='));
    expect(mapUri(place: 'x', mapUrl: 'https://maps.app.goo.gl/abc').toString(),
        'https://maps.app.goo.gl/abc');
  });

  testWidgets('overview shows the countdown, flights and lodging',
      (tester) async {
    final (repo, id) = await seed();
    await repo.saveFlight(
        id,
        const Flight(
            id: '',
            flightNo: 'BR198',
            airline: '長榮',
            fromCode: 'TPE',
            toCode: 'NRT',
            departDate: '2030-12-20',
            departTime: '08:50'));
    await repo.saveLodging(
        id,
        const Lodging(
            id: '',
            name: '淺草飯店',
            checkIn: '2030-12-20',
            checkOut: '2030-12-22',
            confirmation: 'ABC123'));
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(app(repo, id));
    await tester.pumpAndSettle();
    expect(find.text('距離出發還有'), findsOneWidget);
    expect(find.text('長榮 BR198'), findsOneWidget);
    expect(find.text('TPE'), findsOneWidget);
    expect(find.text('淺草飯店'), findsOneWidget);
    expect(find.textContaining('（2 晚）'), findsOneWidget);
    expect(find.text('訂房編號：ABC123'), findsOneWidget);
  });

  testWidgets('itinerary switches days and tags stops', (tester) async {
    final (repo, id) = await seed();
    await repo.saveItem(
        id,
        const ItineraryItem(
            id: '',
            date: '2030-12-20',
            time: '10:00',
            title: '淺草寺',
            order: 0,
            transport: Transport.train,
            mustGo: true));
    await repo.saveItem(
        id,
        const ItineraryItem(
            id: '', date: '2030-12-21', title: '迪士尼', order: 0));
    await tester.pumpWidget(app(repo, id, tab: 1, calm: true));
    await tester.pumpAndSettle();

    expect(find.text('淺草寺'), findsOneWidget);
    expect(find.text('電車'), findsOneWidget);
    expect(find.text('必去'), findsOneWidget);
    expect(find.text('迪士尼'), findsNothing);

    await tester.tap(find.textContaining('Day 2'));
    await tester.pumpAndSettle();
    expect(find.text('迪士尼'), findsOneWidget);
    expect(find.text('淺草寺'), findsNothing);
  });

  testWidgets('checklists offer starters and record who ticked',
      (tester) async {
    final (repo, id) = await seed();
    await tester.pumpWidget(app(repo, id, tab: 2));
    await tester.pumpAndSettle();

    await tester.tap(find.text('行李'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('加入常用的「行李」項目'));
    await tester.pumpAndSettle();
    expect(find.text('護照'), findsOneWidget);
    expect(find.text('完成 0 / ${ChecklistKind.packing.suggestions.length}'),
        findsOneWidget);

    await tester.tap(find.text('護照'));
    await tester.pumpAndSettle();
    expect(find.text('小明 完成'), findsOneWidget);

    await tester.tap(find.text('待辦'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '訂晴空塔門票');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(find.text('訂晴空塔門票'), findsOneWidget);
    expect(find.text('護照'), findsNothing);
  });
}
