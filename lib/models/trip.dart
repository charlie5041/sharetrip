/// A trip: the unit that members, expenses and the itinerary belong to.
class Trip {
  const Trip({
    required this.id,
    required this.name,
    required this.baseCurrency,
    required this.startDate,
    required this.endDate,
    required this.ownerUid,
    required this.memberUids,
    required this.createdAt,
  });

  final String id;
  final String name;

  /// ISO 4217 code every balance is settled in.
  final String baseCurrency;

  /// Calendar dates as "yyyy-MM-dd", free of time zones on purpose: a trip
  /// to Tokyo starts on the 20th wherever the phone happens to be.
  final String startDate;
  final String endDate;
  final String ownerUid;

  /// Signed-in members, for the security rules.
  final List<String> memberUids;
  final int createdAt;

  Map<String, dynamic> toMap() => {
        'name': name,
        'baseCurrency': baseCurrency,
        'startDate': startDate,
        'endDate': endDate,
        'ownerUid': ownerUid,
        'memberUids': memberUids,
        'createdAt': createdAt,
      };

  factory Trip.fromMap(String id, Map<String, dynamic> m) => Trip(
        id: id,
        name: m['name'] as String,
        baseCurrency: m['baseCurrency'] as String,
        startDate: m['startDate'] as String,
        endDate: m['endDate'] as String,
        ownerUid: m['ownerUid'] as String,
        memberUids: List<String>.from(m['memberUids'] as List? ?? const []),
        createdAt: m['createdAt'] as int,
      );
}

/// Someone expenses can be split with. [uid] is null for a person who has
/// no phone with the app; they can claim the name later.
class Member {
  const Member({
    required this.id,
    required this.name,
    required this.order,
    required this.joinedAt,
    this.uid,
    this.relinkedAt,
  });

  final String id;
  final String name;
  final String? uid;

  /// When someone last took over this member from another device, after
  /// the original sign-in was lost. Shown so that a takeover is visible.
  final int? relinkedAt;

  /// Display order, which also decides who absorbs rounding remainders.
  final int order;
  final int joinedAt;

  Map<String, dynamic> toMap() => {
        'name': name,
        'uid': uid,
        'order': order,
        'joinedAt': joinedAt,
        if (relinkedAt != null) 'relinkedAt': relinkedAt,
      };

  factory Member.fromMap(String id, Map<String, dynamic> m) => Member(
        id: id,
        name: m['name'] as String,
        uid: m['uid'] as String?,
        order: m['order'] as int,
        joinedAt: m['joinedAt'] as int,
        relinkedAt: m['relinkedAt'] as int?,
      );
}
