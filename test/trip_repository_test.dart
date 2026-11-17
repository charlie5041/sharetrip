import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
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
        others: ['Bob', '阿嬤'],
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
    expect(members.map((m) => m.name), ['Alice', 'Bob', '阿嬤']);
    expect(members.map((m) => m.uid), ['alice', null, null]);
  });

  test('a friend joins with an invite code', () async {
    final id = await newTrip();
    final code = await alice.createInvite(id);
    final pending = await bob.checkInvite(TripRepository.formatCode(code));
    expect(pending.tripId, id);
    final places = await bob.openPlaces(pending);
    expect(places.map((m) => m.name), ['Bob', '阿嬤']);
    await bob.join(pending, places.first.id);

    expect((await bob.watchMyTrips().first).single.id, id);
    final members = await bob.watchMembers(id).first;
    expect(members.map((m) => m.name), ['Alice', 'Bob', '阿嬤']);
    expect(members[1].uid, 'bob');
    expect((await bob.openPlaces(pending)).map((m) => m.name), ['阿嬤']);
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

  Future<String> bobJoins(String id) async {
    final pending = await bob.checkInvite(await alice.createInvite(id));
    final place = (await bob.openPlaces(pending)).firstWhere((m) => m.name == 'Bob');
    await bob.join(pending, place.id);
    return place.id;
  }

  test('a freed place is taken back from a new phone, expenses and all',
      () async {
    final id = await newTrip();
    final bobId = await bobJoins(id);
    await alice.saveExpense(
        id, ramen(payer: bobId, splits: {'alice': 1, bobId: 1}));

    final member = (await alice.watchMembers(id).first)
        .firstWhere((m) => m.id == bobId);
    await alice.unbindMember(id, member);
    expect((await alice.watchTrip(id).first)!.memberUids, ['alice']);

    // Bob's phone forgot him; he comes back as a new anonymous user.
    final newBob = TripRepository(firestore: db, uid: 'bob2');
    final again = await newBob.checkInvite(await alice.createInvite(id));
    await newBob.join(again, bobId);
    final b = (await newBob.watchMembers(id).first)
        .firstWhere((m) => m.id == bobId);
    expect(b.uid, 'bob2');
    expect((await newBob.watchExpenses(id).first).single.payerId, bobId);
  });

  test('a member with expenses cannot be removed; one without can',
      () async {
    final id = await newTrip();
    final members = await alice.watchMembers(id).first;
    final grandma = members.firstWhere((m) => m.name == '阿嬤');
    final b = members.firstWhere((m) => m.name == 'Bob');
    await alice.saveExpense(
        id, ramen(payer: 'alice', splits: {'alice': 1, grandma.id: 1}));
    expect(() => alice.removeMember(id, grandma), throwsA(isA<StateError>()));
    await alice.removeMember(id, b);
    expect((await alice.watchMembers(id).first).map((m) => m.name),
        ['Alice', '阿嬤']);
  });

  test('removing a joined member takes them out of the trip', () async {
    final id = await newTrip();
    final bobId = await bobJoins(id);
    final b = (await alice.watchMembers(id).first)
        .firstWhere((m) => m.id == bobId);
    await alice.removeMember(id, b);
    expect((await alice.watchTrip(id).first)!.memberUids, ['alice']);
  });

  test('dissolving deletes the trip and everything in it', () async {
    final id = await newTrip();
    await alice.saveExpense(id, ramen(payer: 'alice', splits: {'alice': 1}));
    await alice.dissolveTrip(id);
    expect(await alice.watchTrip(id).first, isNull);
    expect(await alice.watchMyTrips().first, isEmpty);
    expect((await db.collection('trips/$id/expenses').get()).docs, isEmpty);
    expect((await db.collection('trips/$id/members').get()).docs, isEmpty);
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
