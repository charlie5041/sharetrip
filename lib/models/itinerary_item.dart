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

  /// Position within the day.
  final int order;

  Map<String, dynamic> toMap() => {
        'date': date,
        'time': time,
        'title': title,
        'place': place,
        'mapUrl': mapUrl,
        'note': note,
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
        order: m['order'] as int,
      );
}
