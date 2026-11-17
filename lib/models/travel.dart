/// A flight leg. Times are local to each airport, as printed on the
/// ticket, so they are kept as plain text rather than converted.
class Flight {
  const Flight({
    required this.id,
    required this.flightNo,
    required this.fromCode,
    required this.toCode,
    required this.departDate,
    required this.departTime,
    this.airline,
    this.fromName,
    this.toName,
    this.arriveDate,
    this.arriveTime,
    this.note,
  });

  final String id;
  final String flightNo;
  final String? airline;
  final String fromCode;
  final String? fromName;
  final String toCode;
  final String? toName;

  /// "yyyy-MM-dd" and "HH:mm", local to the departure airport.
  final String departDate;
  final String departTime;

  /// Local to the arrival airport.
  final String? arriveDate;
  final String? arriveTime;
  final String? note;

  Map<String, dynamic> toMap() => {
        'flightNo': flightNo,
        'airline': airline,
        'fromCode': fromCode,
        'fromName': fromName,
        'toCode': toCode,
        'toName': toName,
        'departDate': departDate,
        'departTime': departTime,
        'arriveDate': arriveDate,
        'arriveTime': arriveTime,
        'note': note,
      };

  factory Flight.fromMap(String id, Map<String, dynamic> m) => Flight(
        id: id,
        flightNo: m['flightNo'] as String,
        airline: m['airline'] as String?,
        fromCode: m['fromCode'] as String,
        fromName: m['fromName'] as String?,
        toCode: m['toCode'] as String,
        toName: m['toName'] as String?,
        departDate: m['departDate'] as String,
        departTime: m['departTime'] as String,
        arriveDate: m['arriveDate'] as String?,
        arriveTime: m['arriveTime'] as String?,
        note: m['note'] as String?,
      );

  String get departKey => '$departDate $departTime';
}

/// Somewhere the group sleeps.
class Lodging {
  const Lodging({
    required this.id,
    required this.name,
    required this.checkIn,
    required this.checkOut,
    this.address,
    this.mapUrl,
    this.confirmation,
    this.note,
  });

  final String id;
  final String name;

  /// "yyyy-MM-dd".
  final String checkIn;
  final String checkOut;
  final String? address;
  final String? mapUrl;

  /// Booking number, shown so anyone can check in.
  final String? confirmation;
  final String? note;

  Map<String, dynamic> toMap() => {
        'name': name,
        'checkIn': checkIn,
        'checkOut': checkOut,
        'address': address,
        'mapUrl': mapUrl,
        'confirmation': confirmation,
        'note': note,
      };

  factory Lodging.fromMap(String id, Map<String, dynamic> m) => Lodging(
        id: id,
        name: m['name'] as String,
        checkIn: m['checkIn'] as String,
        checkOut: m['checkOut'] as String,
        address: m['address'] as String?,
        mapUrl: m['mapUrl'] as String?,
        confirmation: m['confirmation'] as String?,
        note: m['note'] as String?,
      );
}

/// Which shared list an entry belongs to.
enum ChecklistKind { todo, predeparture, packing }

extension ChecklistKindLabel on ChecklistKind {
  String get label => switch (this) {
        ChecklistKind.todo => '待辦',
        ChecklistKind.predeparture => '出發前',
        ChecklistKind.packing => '行李',
      };

  /// Starter entries offered on an empty list.
  List<String> get suggestions => switch (this) {
        ChecklistKind.todo => const [],
        ChecklistKind.predeparture => const [
            '確認護照效期（6 個月以上）',
            '換外幣 / 開通海外刷卡',
            '買網卡或開漫遊',
            '線上報到、選位',
            '買旅遊平安險',
            '確認住宿訂房',
          ],
        ChecklistKind.packing => const [
            '護照',
            '手機充電器',
            '行動電源',
            '轉接頭',
            '換洗衣物',
            '盥洗用品',
            '常備藥',
            '雨傘',
          ],
      };
}

/// One line on a shared checklist; ticking it shows for everyone.
class ChecklistEntry {
  const ChecklistEntry({
    required this.id,
    required this.kind,
    required this.text,
    required this.done,
    required this.order,
    this.doneBy,
  });

  final String id;
  final ChecklistKind kind;
  final String text;
  final bool done;

  /// Name of whoever ticked it.
  final String? doneBy;
  final int order;

  Map<String, dynamic> toMap() => {
        'kind': kind.name,
        'text': text,
        'done': done,
        'doneBy': doneBy,
        'order': order,
      };

  factory ChecklistEntry.fromMap(String id, Map<String, dynamic> m) =>
      ChecklistEntry(
        id: id,
        kind: ChecklistKind.values.asNameMap()[m['kind']] ?? ChecklistKind.todo,
        text: m['text'] as String,
        done: m['done'] as bool? ?? false,
        doneBy: m['doneBy'] as String?,
        order: m['order'] as int? ?? 0,
      );
}
