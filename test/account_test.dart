import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/main.dart';
import 'package:sharetrip/services/account.dart';
import 'package:sharetrip/services/exchange_rates.dart';
import 'package:sharetrip/services/trip_repository.dart';

class FakeAccount implements Account {
  @override
  String? linkedEmail;
  bool inUse = false;
  int signIns = 0;

  @override
  Future<void> linkGoogle() async {
    if (inUse) throw const AccountInUse(null, 'me@example.com');
    linkedEmail = 'me@example.com';
  }

  @override
  Future<void> signInWithGoogle({AuthCredential? credential}) async {
    signIns++;
  }
}

void main() {
  late TripRepository repo;
  late FakeAccount account;
  late int restarts;

  setUp(() async {
    repo = TripRepository(firestore: FakeFirebaseFirestore(), uid: 'u1');
    account = FakeAccount();
    restarts = 0;
  });

  Future<void> pumpApp(WidgetTester tester) async {
    await tester.pumpWidget(TripShareApp(
      repo: repo,
      rates: ExchangeRates(),
      account: account,
      restart: () => restarts++,
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('first launch offers to restore from Google', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.text('用過同遊？用 Google 帳號找回我的旅程'));
    await tester.pumpAndSettle();
    expect(account.signIns, 1);
    expect(restarts, 1);
  });

  testWidgets('linking Google from the trip list', (tester) async {
    await repo.setNickname('小明');
    await pumpApp(tester);
    await tester.tap(find.byTooltip('備份我的身分'));
    await tester.pumpAndSettle();
    expect(find.textContaining('目前沒有綁定'), findsOneWidget);

    await tester.tap(find.text('綁定 Google 帳號'));
    await tester.pumpAndSettle();
    expect(account.linkedEmail, 'me@example.com');
    expect(restarts, 0);
  });

  testWidgets('an account already in use offers a switch', (tester) async {
    await repo.setNickname('小明');
    account.inUse = true;
    await pumpApp(tester);
    await tester.tap(find.byTooltip('備份我的身分'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('綁定 Google 帳號'));
    await tester.pumpAndSettle();
    expect(find.text('這個帳號已經綁定過'), findsOneWidget);

    await tester.tap(find.text('改用那個身分'));
    await tester.pumpAndSettle();
    expect(account.signIns, 1);
    expect(restarts, 1);
  });
}
