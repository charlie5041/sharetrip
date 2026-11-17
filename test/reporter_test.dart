import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:sharetrip/screens/feedback_dialog.dart';
import 'package:sharetrip/services/reporter.dart';

void main() {
  late FakeFirebaseFirestore fs;

  Future<List<Map<String, dynamic>>> reports() async =>
      (await fs.collection('errorReports').get()).docs
          .map((d) => d.data())
          .toList();

  setUp(() {
    fs = FakeFirebaseFirestore();
    Reporter.reset();
    PackageInfo.setMockInitialValues(
      appName: '同遊',
      packageName: 'x',
      version: '0.1.0',
      buildNumber: '9',
      buildSignature: '',
      installerStore: null,
    );
  });

  test('errors before sign-in are sent once it happens', () async {
    Reporter.recordError(StateError('early'), StackTrace.current);
    expect(await reports(), isEmpty);
    await Reporter.attach(firestore: fs, uid: 'alice');
    await pumpEventQueue();
    final r = await reports();
    expect(r, hasLength(1));
    expect(r.single['uid'], 'alice');
    expect(r.single['kind'], 'error');
    expect(r.single['message'], contains('early'));
    expect(r.single['version'], startsWith('0.1.0+9 '));
  });

  test('the same error is sent once, and a session sends at most 20', () async {
    await Reporter.attach(firestore: fs, uid: 'alice');
    for (var i = 0; i < 3; i++) {
      Reporter.recordError(StateError('same'), null);
    }
    for (var i = 0; i < 50; i++) {
      Reporter.recordError(StateError('loop $i'), null);
    }
    await pumpEventQueue();
    expect(await reports(), hasLength(Reporter.maxErrorsPerSession));
  });

  test('long messages are cut to what the rules accept', () async {
    await Reporter.attach(firestore: fs, uid: 'alice');
    Reporter.recordError('x' * 5000, null);
    await pumpEventQueue();
    expect((await reports()).single['message'].length, Reporter.maxMessage);
  });

  testWidgets('回報問題 sends what was typed', (tester) async {
    await tester.runAsync(() => Reporter.attach(firestore: fs, uid: 'alice'));
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () => showFeedbackDialog(context),
              child: const Text('open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '結算金額不對');
    await tester.tap(find.text('送出'));
    await tester.pumpAndSettle();
    expect(find.text('謝謝！已經送出'), findsOneWidget);
    final r = await tester.runAsync(reports);
    expect(r!.single['kind'], 'feedback');
    expect(r.single['message'], '結算金額不對');
  });
}
