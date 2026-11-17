import 'dart:math';

import 'package:cloud_firestore/cloud_firestore.dart';

import '../models/expense.dart';
import '../models/itinerary_item.dart';
import '../models/travel.dart';
import '../models/trip.dart';

enum InviteErrorKind { notFound, expired, alreadyMember }

class InviteError implements Exception {
  const InviteError(this.kind);
  final InviteErrorKind kind;

  String get message => switch (kind) {
        InviteErrorKind.notFound => '找不到這組邀請碼，請確認後重新輸入',
        InviteErrorKind.expired => '這組邀請碼已過期，請對方重新產生',
        InviteErrorKind.alreadyMember => '你已經在這趟旅程裡了',
      };

  @override
  String toString() => message;
}

/// An invite that checked out, before the device actually joins.
class PendingJoin {
  const PendingJoin({required this.code, required this.tripId});
  final String code;
  final String tripId;
}

/// Every Firestore read and write the app makes, for one signed-in device.
class TripRepository {
  TripRepository({required FirebaseFirestore firestore, required this.uid})
      : _db = firestore;

  final FirebaseFirestore _db;
  final String uid;

  static const inviteTtl = Duration(hours: 24);

  /// No 0/O/1/I, so a code read aloud cannot be mistyped.
  static const _alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  static const codeLength = 8;

  int get _now => DateTime.now().millisecondsSinceEpoch;

  CollectionReference<Map<String, dynamic>> get _trips =>
      _db.collection('trips');
  DocumentReference<Map<String, dynamic>> get _userDoc =>
      _db.collection('users').doc(uid);
  CollectionReference<Map<String, dynamic>> _members(String tripId) =>
      _trips.doc(tripId).collection('members');
  CollectionReference<Map<String, dynamic>> _expenses(String tripId) =>
      _trips.doc(tripId).collection('expenses');
  CollectionReference<Map<String, dynamic>> _settlements(String tripId) =>
      _trips.doc(tripId).collection('settlements');

  // ---- Profile -----------------------------------------------------------

  Future<String?> loadNickname() async =>
      (await _userDoc.get()).data()?['nickname'] as String?;

  Future<void> setNickname(String nickname) =>
      _userDoc.set({'nickname': nickname.trim()}, SetOptions(merge: true));

  // ---- Trips -------------------------------------------------------------

  Stream<List<Trip>> watchMyTrips() => _trips
      .where('memberUids', arrayContains: uid)
      .snapshots()
      .map((s) => s.docs.map((d) => Trip.fromMap(d.id, d.data())).toList()
        ..sort((a, b) => b.startDate.compareTo(a.startDate)));

  Stream<Trip> watchTrip(String tripId) =>
      _trips.doc(tripId).snapshots().map((d) => Trip.fromMap(d.id, d.data()!));

  /// A fresh trip id, so a screen can open the trip before the write
  /// reaches the server (it may be offline).
  String newTripId() => _trips.doc().id;

  /// Creates a trip with this device as its first member.
  Future<String> createTrip({
    String? id,
    required String name,
    required String baseCurrency,
    required String startDate,
    required String endDate,
    required String nickname,
  }) async {
    final ref = _trips.doc(id);
    final batch = _db.batch()
      ..set(ref, {
        'name': name.trim(),
        'baseCurrency': baseCurrency,
        'startDate': startDate,
        'endDate': endDate,
        'ownerUid': uid,
        'memberUids': [uid],
        'createdAt': _now,
      })
      ..set(_members(ref.id).doc(uid), {
        'name': nickname,
        'uid': uid,
        'order': 0,
        'joinedAt': _now,
      });
    await batch.commit();
    return ref.id;
  }

  Future<void> updateTrip(String tripId,
          {String? name, String? startDate, String? endDate}) =>
      _trips.doc(tripId).update({
        'name': ?name,
        'startDate': ?startDate,
        'endDate': ?endDate,
      });

  // ---- Members -----------------------------------------------------------

  Stream<List<Member>> watchMembers(String tripId) =>
      _members(tripId).snapshots().map((s) =>
          s.docs.map((d) => Member.fromMap(d.id, d.data())).toList()
            ..sort((a, b) => a.order.compareTo(b.order)));

  /// Adds someone who has no phone with the app, by name only.
  Future<void> addNameOnlyMember(String tripId, String name) async {
    final existing = await _members(tripId).get();
    final nextOrder = existing.docs.fold<int>(
            -1, (m, d) => max(m, d.data()['order'] as int? ?? 0)) +
        1;
    await _members(tripId).add({
      'name': name.trim(),
      'uid': null,
      'order': nextOrder,
      'joinedAt': _now,
    });
  }

  Future<void> renameMember(String tripId, String memberId, String name) =>
      _members(tripId).doc(memberId).update({'name': name.trim()});

  // ---- Invites -----------------------------------------------------------

  static String generateCode() {
    final rnd = Random.secure();
    return List.generate(
        codeLength, (_) => _alphabet[rnd.nextInt(_alphabet.length)]).join();
  }

  static String normalizeCode(String input) =>
      input.toUpperCase().replaceAll(RegExp(r'[^A-Z0-9]'), '');

  static String formatCode(String code) =>
      '${code.substring(0, 4)}-${code.substring(4)}';

  Future<String> createInvite(String tripId) async {
    final code = generateCode();
    await _db.collection('invites').doc(code).set({
      'tripId': tripId,
      'expiresAt': Timestamp.fromDate(DateTime.now().add(inviteTtl)),
      'createdBy': uid,
    });
    return code;
  }

  /// Checks [rawCode] and records it on this device's user document, which
  /// the security rules read to allow the join.
  Future<PendingJoin> checkInvite(String rawCode) async {
    final code = normalizeCode(rawCode);
    if (code.length != codeLength) {
      throw const InviteError(InviteErrorKind.notFound);
    }
    final snap = await _db.collection('invites').doc(code).get();
    if (!snap.exists) throw const InviteError(InviteErrorKind.notFound);
    final data = snap.data()!;
    if ((data['expiresAt'] as Timestamp).toDate().isBefore(DateTime.now())) {
      throw const InviteError(InviteErrorKind.expired);
    }
    final tripId = data['tripId'] as String;
    final mine = await _trips.where('memberUids', arrayContains: uid).get();
    if (mine.docs.any((d) => d.id == tripId)) {
      throw const InviteError(InviteErrorKind.alreadyMember);
    }
    await _userDoc.set({'joinCode': code}, SetOptions(merge: true));
    return PendingJoin(code: code, tripId: tripId);
  }

  /// Joins the trip as a new member called [nickname].
  Future<void> join(PendingJoin pending, {required String nickname}) async {
    final batch = _db.batch()
      ..update(_trips.doc(pending.tripId), {
        'memberUids': FieldValue.arrayUnion([uid]),
      })
      ..set(_members(pending.tripId).doc(uid), {
        'name': nickname,
        'uid': uid,
        // Joined members go after everyone already there; the exact value
        // only needs to sort last.
        'order': _now,
        'joinedAt': _now,
      });
    await batch.commit();
  }

  /// Makes this device the person behind member [placeholderId], dropping
  /// the member this device joined as. Refused while anything still points
  /// at that member, so no expense is ever orphaned.
  ///
  /// [placeholderId] is either a name-only member, or a member whose
  /// device lost its anonymous sign-in (a new phone, cleared web data) and
  /// came back through a fresh invite. The second case is stamped with
  /// relinkedAt so the others can see it happened.
  Future<void> claimMember(
      String tripId, String placeholderId, String myMemberId) async {
    final used = await _isReferenced(tripId, myMemberId);
    if (used) {
      throw StateError('你已經有記帳紀錄，無法認領其他名字');
    }
    final target = await _members(tripId).doc(placeholderId).get();
    final relink = target.data()?['uid'] != null;
    final batch = _db.batch()
      ..update(_members(tripId).doc(placeholderId), {
        'uid': uid,
        if (relink) 'relinkedAt': _now,
      })
      ..delete(_members(tripId).doc(myMemberId));
    await batch.commit();
  }

  Future<bool> _isReferenced(String tripId, String memberId) async {
    final ex = await _expenses(tripId).get();
    for (final d in ex.docs) {
      final m = d.data();
      if (m['payerId'] == memberId) return true;
      if ((m['splits'] as Map?)?.containsKey(memberId) ?? false) return true;
    }
    final st = await _settlements(tripId).get();
    return st.docs.any((d) =>
        d.data()['fromId'] == memberId || d.data()['toId'] == memberId);
  }

  // ---- Expenses and settlements ------------------------------------------

  Stream<List<Expense>> watchExpenses(String tripId) =>
      _expenses(tripId).snapshots().map((s) =>
          s.docs.map((d) => Expense.fromMap(d.id, d.data())).toList()
            ..sort((a, b) => b.occurredAt.compareTo(a.occurredAt)));

  /// Creates [e] when its id is empty, otherwise replaces it.
  Future<void> saveExpense(String tripId, Expense e) {
    final data = e.toMap()
      ..['updatedAt'] = _now
      ..['createdBy'] = e.createdBy.isEmpty ? uid : e.createdBy;
    return e.id.isEmpty
        ? _expenses(tripId).add(data)
        : _expenses(tripId).doc(e.id).set(data);
  }

  Future<void> deleteExpense(String tripId, String expenseId) =>
      _expenses(tripId).doc(expenseId).delete();

  Stream<List<Settlement>> watchSettlements(String tripId) =>
      _settlements(tripId).snapshots().map((s) =>
          s.docs.map((d) => Settlement.fromMap(d.id, d.data())).toList()
            ..sort((a, b) => b.at.compareTo(a.at)));

  Future<void> addSettlement(
          String tripId, String fromId, String toId, int amountMinor) =>
      _settlements(tripId).add({
        'fromId': fromId,
        'toId': toId,
        'amountMinor': amountMinor,
        'at': _now,
        'createdBy': uid,
      });

  Future<void> deleteSettlement(String tripId, String id) =>
      _settlements(tripId).doc(id).delete();

  // ---- Itinerary, flights, lodging, checklists ---------------------------

  CollectionReference<Map<String, dynamic>> _sub(String tripId, String name) =>
      _trips.doc(tripId).collection(name);

  Stream<List<ItineraryItem>> watchItems(String tripId) =>
      _sub(tripId, 'items').snapshots().map((s) =>
          s.docs.map((d) => ItineraryItem.fromMap(d.id, d.data())).toList()
            ..sort(ItineraryItem.compare));

  Future<void> saveItem(String tripId, ItineraryItem item) {
    final data = item.toMap();
    if (item.id.isEmpty) {
      data['order'] = _now;
      return _sub(tripId, 'items').add(data);
    }
    return _sub(tripId, 'items').doc(item.id).set(data);
  }

  Future<void> deleteItem(String tripId, String id) =>
      _sub(tripId, 'items').doc(id).delete();

  Stream<List<Flight>> watchFlights(String tripId) =>
      _sub(tripId, 'flights').snapshots().map((s) =>
          s.docs.map((d) => Flight.fromMap(d.id, d.data())).toList()
            ..sort((a, b) => a.departKey.compareTo(b.departKey)));

  Future<void> saveFlight(String tripId, Flight f) => f.id.isEmpty
      ? _sub(tripId, 'flights').add(f.toMap())
      : _sub(tripId, 'flights').doc(f.id).set(f.toMap());

  Future<void> deleteFlight(String tripId, String id) =>
      _sub(tripId, 'flights').doc(id).delete();

  Stream<List<Lodging>> watchLodgings(String tripId) =>
      _sub(tripId, 'lodgings').snapshots().map((s) =>
          s.docs.map((d) => Lodging.fromMap(d.id, d.data())).toList()
            ..sort((a, b) => a.checkIn.compareTo(b.checkIn)));

  Future<void> saveLodging(String tripId, Lodging l) => l.id.isEmpty
      ? _sub(tripId, 'lodgings').add(l.toMap())
      : _sub(tripId, 'lodgings').doc(l.id).set(l.toMap());

  Future<void> deleteLodging(String tripId, String id) =>
      _sub(tripId, 'lodgings').doc(id).delete();

  Stream<List<ChecklistEntry>> watchChecklist(String tripId) =>
      _sub(tripId, 'checklist').snapshots().map((s) =>
          s.docs.map((d) => ChecklistEntry.fromMap(d.id, d.data())).toList()
            ..sort((a, b) => a.order.compareTo(b.order)));

  Future<void> addChecklistEntries(
      String tripId, ChecklistKind kind, List<String> texts) async {
    final batch = _db.batch();
    var order = _now;
    for (final t in texts) {
      batch.set(_sub(tripId, 'checklist').doc(), {
        'kind': kind.name,
        'text': t.trim(),
        'done': false,
        'doneBy': null,
        'order': order++,
      });
    }
    await batch.commit();
  }

  Future<void> setChecklistDone(
          String tripId, String id, bool done, String byName) =>
      _sub(tripId, 'checklist')
          .doc(id)
          .update({'done': done, 'doneBy': done ? byName : null});

  Future<void> deleteChecklistEntry(String tripId, String id) =>
      _sub(tripId, 'checklist').doc(id).delete();
}
