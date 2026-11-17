/// How you get to a stop. Shown as a coloured tag on the timeline.
enum Transport { none, walk, train, bus, taxi, car, flight, ferry }

extension TransportLabel on Transport {
  String get label => switch (this) {
        Transport.none => '無',
        Transport.walk => '步行',
        Transport.train => '電車',
        Transport.bus => '巴士',
        Transport.taxi => '計程車',
        Transport.car => '開車',
        Transport.flight => '飛機',
        Transport.ferry => '船',
      };
}

/// One stop on one day of the trip.
class ItineraryItem {
  const ItineraryItem({
    required this.id,
    required this.date,
    required this.title,
    required this.order,
    this.time,
    this.place,
    this.mapUrl,
    this.note,
    this.transport = Transport.none,
    this.mustGo = false,
  });

  final String id;

  /// "yyyy-MM-dd".
  final String date;

  /// "HH:mm", or null for "sometime that day".
  final String? time;
  final String title;
  final String? place;
  final String? mapUrl;
  final String? note;

  /// How you get there from the previous stop.
  final Transport transport;

  /// Marked 必去 on the timeline.
  final bool mustGo;

  /// Tie-breaker within a day, and the position of untimed stops.
  final int order;

  Map<String, dynamic> toMap() => {
        'date': date,
        'time': time,
        'title': title,
        'place': place,
        'mapUrl': mapUrl,
        'note': note,
        'transport': transport.name,
        'mustGo': mustGo,
        'order': order,
      };

  factory ItineraryItem.fromMap(String id, Map<String, dynamic> m) =>
      ItineraryItem(
        id: id,
        date: m['date'] as String,
        time: m['time'] as String?,
        title: m['title'] as String,
        place: m['place'] as String?,
        mapUrl: m['mapUrl'] as String?,
        note: m['note'] as String?,
        transport: Transport.values.asNameMap()[m['transport']] ?? Transport.none,
        mustGo: m['mustGo'] as bool? ?? false,
        order: m['order'] as int? ?? 0,
      );

  /// Timed stops first, by time; then untimed ones in [order].
  static int compare(ItineraryItem a, ItineraryItem b) {
    if (a.time != null && b.time != null) {
      final c = a.time!.compareTo(b.time!);
      return c != 0 ? c : a.order.compareTo(b.order);
    }
    if (a.time != null) return -1;
    if (b.time != null) return 1;
    return a.order.compareTo(b.order);
  }
}
