import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/main.dart';
import 'package:sharetrip/services/exchange_rates.dart';
import 'package:sharetrip/services/trip_repository.dart';
import 'package:sharetrip/theme.dart';

void main() {
  testWidgets(
    'first launch goes straight to the trips, in the picture-book theme',
    (tester) async {
      final repo = TripRepository(
        firestore: FakeFirebaseFirestore(),
        uid: 'u1',
      );
      await tester.pumpWidget(TripShareApp(repo: repo, rates: ExchangeRates()));
      await tester.pumpAndSettle();
      expect(find.text('我的旅程'), findsOneWidget);

      final theme = Theme.of(tester.element(find.text('我的旅程')));
      expect(theme.scaffoldBackgroundColor, AppColors.paper);
      expect(theme.brightness, Brightness.light);
      expect(theme.textTheme.bodyMedium?.fontFamily, 'Huninn');
    },
  );
}
