import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/illustrations/scenes.dart';
import 'package:sharetrip/illustrations/trip_scene.dart';

void main() {
  test('destination is guessed from the trip name', () {
    expect(themeFor('東京五日遊'), SceneTheme.japan);
    expect(themeFor('2027 北海道滑雪'), SceneTheme.japan);
    expect(themeFor('首爾購物'), SceneTheme.korea);
    expect(themeFor('沖繩自駕'), SceneTheme.island);
    expect(themeFor('歐洲蜜月'), SceneTheme.anywhere);
  });

  Future<void> pump(WidgetTester tester, {required bool calm}) =>
      tester.pumpWidget(MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: calm),
          child: child!,
        ),
        home: const Scaffold(body: TripScene(tripName: '東京')),
      ));

  double vehicleLeft(WidgetTester tester) => tester
      .widgetList<Positioned>(find.byType(Positioned))
      .last
      .left!;

  testWidgets('the scene moves', (tester) async {
    await pump(tester, calm: false);
    final a = vehicleLeft(tester);
    await tester.pump(const Duration(seconds: 2));
    expect(vehicleLeft(tester), isNot(a));
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('the scene holds still with reduced motion', (tester) async {
    await pump(tester, calm: true);
    final a = vehicleLeft(tester);
    await tester.pump(const Duration(seconds: 2));
    expect(vehicleLeft(tester), a);
    expect(tester.hasRunningAnimations, isFalse);
  });
}
