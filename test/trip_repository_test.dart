import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/logic/split.dart';
import 'package:sharetrip/models/currency.dart';
import 'package:sharetrip/models/expense.dart';
import 'package:sharetrip/services/trip_repository.dart';

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
      );

  Expense ramen({required String payer, required Map<String, int> splits}) =>
      Expense(
        id: '',
        title: '拉麵',
        category: ExpenseCategory.food,
        amountMinor: 3000,
        currency: 'JPY',
        rateToBase: '0.21',
        payerId: payer,
        splitMode: SplitMode.equal,
        splits: splits,
        occurredAt: 0,
        createdBy: '',
        updatedAt: 0,
      );

  test('the creator is the first member', () async {
    final id = await newTrip();
    final trips = await alice.watchMyTrips().first;
    expect(trips.single.id, id);
    final members = await alice.watchMembers(id).first;
    expect(members.single.uid, 'alice');
    expect(members.single.name, 'Alice');
  });

  test('a friend joins with an invite code', () async {
    final id = await newTrip();
    final code = await alice.createInvite(id);
    final pending = await bob.checkInvite(TripRepository.formatCode(code));
    expect(pending.tripId, id);
    await bob.join(pending, nickname: 'Bob');

    expect((await bob.watchMyTrips().first).single.id, id);
    final members = await bob.watchMembers(id).first;
    expect(members.map((m) => m.name), ['Alice', 'Bob']);
    expect(
        () => bob.checkInvite(code),
        throwsA(isA<InviteError>()
            .having((e) => e.kind, 'kind', InviteErrorKind.alreadyMember)));
  });

  test('a wrong code is reported as not found', () async {
    expect(
        () => bob.checkInvite('AAAA-BBBB'),
        throwsA(isA<InviteError>()
            .having((e) => e.kind, 'kind', InviteErrorKind.notFound)));
  });

  test('claiming a name-only member keeps their expenses', () async {
    final id = await newTrip();
    await alice.addNameOnlyMember(id, '阿嬤');
    final grandma =
        (await alice.watchMembers(id).first).firstWhere((m) => m.uid == null);
    await alice.saveExpense(
        id, ramen(payer: 'alice', splits: {'alice': 1, grandma.id: 1}));

    final pending = await bob.checkInvite(await alice.createInvite(id));
    await bob.join(pending, nickname: 'Bob');
    await bob.claimMember(id, grandma.id, 'bob');

    final members = await bob.watchMembers(id).first;
    expect(members.length, 2);
    expect(members.firstWhere((m) => m.id == grandma.id).uid, 'bob');

    final net = balances(
      members: members,
      expenses: await bob.watchExpenses(id).first,
      settlements: [],
      base: Currency.of('TWD'),
    );
    // 3000 JPY × 0.21 = 630 TWD, half each.
    expect(net, {'alice': 315, grandma.id: -315});
  });

  test('a member who already has expenses cannot claim another name',
      () async {
    final id = await newTrip();
    await alice.addNameOnlyMember(id, '阿嬤');
    final grandma =
        (await alice.watchMembers(id).first).firstWhere((m) => m.uid == null);
    await alice.saveExpense(id, ramen(payer: 'alice', splits: {'alice': 1}));
    expect(() => alice.claimMember(id, grandma.id, 'alice'),
        throwsA(isA<StateError>()));
  });

  test('settlements are recorded and can be undone', () async {
    final id = await newTrip();
    await alice.addSettlement(id, 'bob', 'alice', 100);
    final s = await alice.watchSettlements(id).first;
    expect(s.single.amountMinor, 100);
    await alice.deleteSettlement(id, s.single.id);
    expect(await alice.watchSettlements(id).first, isEmpty);
  });
}
