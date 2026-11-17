import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sharetrip/services/install/install_env.dart';
import 'package:sharetrip/widgets/install_guide.dart';

Future<void> pumpBanner(WidgetTester tester, InstallEnv env) async {
  InstallEnv.current = () => env;
  await tester.pumpWidget(
    const MaterialApp(
      home: Scaffold(body: Column(children: [InstallBanner()])),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('the native apps never show the guide', () {
    expect(InstallEnv.current().showGuide, isFalse);
  });

  testWidgets('opened from the home screen, there is no card', (tester) async {
    await pumpBanner(
      tester,
      const InstallEnv(
        isWeb: true,
        standalone: true,
        platform: InstallPlatform.ios,
      ),
    );
    expect(find.text('把同遊加到主畫面'), findsNothing);
  });

  testWidgets('iPhone Safari gets the share-sheet steps', (tester) async {
    await pumpBanner(
      tester,
      const InstallEnv(
        isWeb: true,
        standalone: false,
        platform: InstallPlatform.ios,
      ),
    );
    expect(find.text('先加到主畫面，再從主畫面打開開始用'), findsOneWidget);
    await tester.tap(find.text('怎麼加'));
    await tester.pumpAndSettle();
    expect(find.text('加到 iPhone 主畫面'), findsOneWidget);
    expect(find.textContaining('加入主畫面'), findsWidgets);
  });

  testWidgets('Android installs with one tap when Chrome offers it', (
    tester,
  ) async {
    var prompted = false;
    await pumpBanner(
      tester,
      InstallEnv(
        isWeb: true,
        standalone: false,
        platform: InstallPlatform.android,
        canPrompt: true,
        prompt: () async => prompted = true,
      ),
    );
    await tester.tap(find.text('怎麼加'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('安裝同遊'));
    await tester.pumpAndSettle();
    expect(prompted, isTrue);
    expect(find.text('加到手機主畫面'), findsNothing);
  });

  testWidgets('inside LINE, it sends the person to the real browser', (
    tester,
  ) async {
    var opened = false;
    await pumpBanner(
      tester,
      InstallEnv(
        isWeb: true,
        standalone: false,
        platform: InstallPlatform.ios,
        inAppBrowser: 'LINE',
        openExternal: () => opened = true,
      ),
    );
    await tester.tap(find.text('怎麼加'));
    await tester.pumpAndSettle();
    expect(find.textContaining('LINE'), findsOneWidget);
    await tester.tap(find.text('用 Safari 打開'));
    expect(opened, isTrue);
  });

  testWidgets('closing the card keeps it closed', (tester) async {
    const env = InstallEnv(
      isWeb: true,
      standalone: false,
      platform: InstallPlatform.android,
    );
    await pumpBanner(tester, env);
    await tester.tap(find.byTooltip('不用了'));
    await tester.pumpAndSettle();
    expect(find.text('把同遊加到主畫面'), findsNothing);
    await pumpBanner(tester, env);
    expect(find.text('把同遊加到主畫面'), findsNothing);
  });
}
