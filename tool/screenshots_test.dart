// Renders the main screens to PNGs with the real fonts, for reviewing the
// look without a phone. Not part of CI: run
//   flutter test tool/screenshots_test.dart --update-goldens
// and find the images in tool/screenshots/.
import 'dart:io';

import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/main.dart';
import 'package:sharetrip/models/expense.dart';
import 'package:sharetrip/models/itinerary_item.dart';
import 'package:sharetrip/models/travel.dart';
import 'package:sharetrip/screens/trip_screen.dart';
import 'package:sharetrip/services/exchange_rates.dart';
import 'package:sharetrip/services/trip_repository.dart';
import 'package:sharetrip/theme.dart';

Future<void> loadFont(String family, String path) async {
  final bytes = File(path).readAsBytesSync();
  final loader = FontLoader(family)
    ..addFont(Future.value(ByteData.view(bytes.buffer)));
  await loader.load();
}

void main() {
  setUpAll(() async {
    await loadFont('Huninn', 'assets/fonts/Huninn-Regular.ttf');
    final sdk = Platform.environment['FLUTTER_ROOT'] ?? '/home/user/sdk/flutter';
    await loadFont('MaterialIcons',
        '$sdk/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf');
  });

  Future<(TripRepository, String)> seed() async {
    final repo = TripRepository(firestore: FakeFirebaseFirestore(), uid: 'me');
    final now = DateTime.now();
    String d(int plus) {
      final x = DateTime(now.year, now.month, now.day + plus);
      return '${x.year}-${x.month.toString().padLeft(2, '0')}-${x.day.toString().padLeft(2, '0')}';
    }

    final id = await repo.createTrip(
        name: '東京五日遊',
        baseCurrency: 'TWD',
        startDate: d(34),
        endDate: d(38),
        nickname: '小明');
    await repo.addNameOnlyMember(id, '阿華');
    final members = await repo.watchMembers(id).first;
    await repo.saveFlight(
        id,
        Flight(
            id: '',
            flightNo: 'BR198',
            airline: '長榮',
            fromCode: 'TPE',
            fromName: '桃園',
            toCode: 'NRT',
            toName: '成田',
            departDate: d(34),
            departTime: '08:50',
            arriveDate: d(34),
            arriveTime: '13:15',
            note: '第二航廈，行李 23kg'));
    await repo.saveLodging(
        id,
        Lodging(
            id: '',
            name: '淺草 Richmond Hotel',
            checkIn: d(34),
            checkOut: d(38),
            address: '東京都台東区浅草 2-7-10',
            confirmation: 'RH-88231'));
    for (final it in [
      ItineraryItem(id: '', date: d(34), time: '15:00', title: '飯店 check-in', place: '淺草 Richmond Hotel', order: 0, transport: Transport.train),
      ItineraryItem(id: '', date: d(34), time: '16:30', title: '淺草寺、仲見世通', place: '淺草寺', order: 1, transport: Transport.walk, mustGo: true),
      ItineraryItem(id: '', date: d(34), time: '19:00', title: '晴空塔夜景', place: '東京晴空塔', order: 2, transport: Transport.bus, note: '已買 19:00 門票'),
      ItineraryItem(id: '', date: d(34), title: '便利商店宵夜', order: 3),
    ]) {
      await repo.saveItem(id, it);
    }
    await repo.addChecklistEntries(id, ChecklistKind.todo, ['訂晴空塔門票', '查迪士尼開園時間', '換日幣']);
    final todo = (await repo.watchChecklist(id).first).first;
    await repo.setChecklistDone(id, todo.id, true, '小明');
    await repo.saveExpense(
        id,
        Expense(
            id: '',
            title: '一蘭拉麵',
            category: ExpenseCategory.food,
            amountMinor: 3960,
            currency: 'JPY',
            rateToBase: '0.2134',
            payerId: 'me',
            splitMode: SplitMode.equal,
            splits: {for (final m in members) m.id: 1},
            occurredAt: now.millisecondsSinceEpoch,
            createdBy: '',
            updatedAt: 0));
    return (repo, id);
  }

  for (final (name, tab) in [
    ('01-home', 0),
    ('02-itinerary', 1),
    ('03-checklist', 2),
    ('04-expenses', 3),
  ]) {
    testWidgets(name, (tester) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      final (repo, id) = await seed();
      await tester.pumpWidget(AppScope(
        repo: repo,
        rates: ExchangeRates(),
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: buildAppTheme(),
          home: TripScreen(tripId: id, initialTab: tab),
        ),
      ));
      await tester.pumpAndSettle();
      await expectLater(find.byType(MaterialApp),
          matchesGoldenFile('screenshots/$name.png'));
    });
  }
}
