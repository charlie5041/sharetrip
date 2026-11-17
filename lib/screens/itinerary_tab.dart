import 'package:flutter/material.dart';

import '../models/itinerary_item.dart';
import '../theme.dart';
import '../widgets/dates.dart';
import '../widgets/maps.dart';
import '../widgets/ui.dart';
import 'travel_forms.dart';
import 'trip_screen.dart';

Color transportColor(Transport t) => switch (t) {
      Transport.walk => AppColors.wakatake,
      Transport.train => AppColors.mizu,
      Transport.bus => AppColors.yamabuki,
      Transport.taxi => AppColors.yamabuki,
      Transport.car => AppColors.wakatake,
      Transport.flight => AppColors.mizu,
      Transport.ferry => AppColors.mizu,
      Transport.none => Colors.white,
    };

/// Every date of the trip, start to end inclusive, as "yyyy-MM-dd".
List<String> tripDays(String start, String end) {
  final out = <String>[];
  var d = parseYmd(start);
  final last = parseYmd(end);
  while (!d.isAfter(last)) {
    out.add(ymd(d));
    d = DateTime(d.year, d.month, d.day + 1);
  }
  return out;
}

/// Date switcher on top, the chosen day's timeline below.
class ItineraryTab extends StatefulWidget {
  const ItineraryTab({super.key, required this.data});
  final TripData data;

  @override
  State<ItineraryTab> createState() => _ItineraryTabState();
}

class _ItineraryTabState extends State<ItineraryTab> {
  String? _day;

  List<String> get _days =>
      tripDays(widget.data.trip.startDate, widget.data.trip.endDate);

  String get _selected {
    final days = _days;
    if (_day != null && days.contains(_day)) return _day!;
    final today = ymd(DateTime.now());
    return days.contains(today) ? today : days.first;
  }

  @override
  Widget build(BuildContext context) {
    final days = _days;
    final day = _selected;
    final items = widget.data.items.where((i) => i.date == day).toList()
      ..sort(ItineraryItem.compare);
    final calm = reduceMotion(context);
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'add-item',
        onPressed: () => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) =>
                ItemFormScreen(tripId: widget.data.trip.id, date: day, days: days))),
        icon: const Icon(Icons.add),
        label: const Text('加行程'),
      ),
      body: Column(
        children: [
          SizedBox(
            height: 72,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
              children: [
                for (var i = 0; i < days.length; i++)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: ChoiceChip(
                      label: Text('Day ${i + 1}　${shortDate(days[i])}'),
                      selected: days[i] == day,
                      selectedColor: AppColors.coral,
                      backgroundColor: Colors.white,
                      showCheckmark: false,
                      onSelected: (_) => setState(() => _day = days[i]),
                    ),
                  ),
              ],
            ),
          ),
          Expanded(
            child: AnimatedSwitcher(
              duration: calm ? Duration.zero : const Duration(milliseconds: 450),
              switchInCurve: Curves.elasticOut,
              switchOutCurve: Curves.easeIn,
              transitionBuilder: (child, anim) => calm
                  ? child
                  : ScaleTransition(
                      scale: Tween(begin: 0.92, end: 1.0).animate(anim),
                      child: FadeTransition(opacity: anim, child: child),
                    ),
              child: KeyedSubtree(
                key: ValueKey(day),
                child: items.isEmpty
                    ? const Center(child: Text('這天還沒有行程'))
                    : ListView(
                        padding: const EdgeInsets.fromLTRB(8, 0, 8, 96),
                        children: [
                          for (var i = 0; i < items.length; i++)
                            _TimelineRow(
                              data: widget.data,
                              item: items[i],
                              first: i == 0,
                              last: i == items.length - 1,
                              days: days,
                            ),
                        ],
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _TimelineRow extends StatelessWidget {
  const _TimelineRow({
    required this.data,
    required this.item,
    required this.first,
    required this.last,
    required this.days,
  });

  final TripData data;
  final ItineraryItem item;
  final bool first;
  final bool last;
  final List<String> days;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 52,
            child: Padding(
              padding: const EdgeInsets.only(top: 22),
              child: Text(item.time ?? '',
                  textAlign: TextAlign.right, style: text.titleSmall),
            ),
          ),
          SizedBox(
            width: 28,
            child: CustomPaint(
              painter: _RailPainter(first: first, last: last, mustGo: item.mustGo),
            ),
          ),
          Expanded(
            child: Card(
              margin: const EdgeInsets.fromLTRB(0, 6, 8, 6),
              color: item.mustGo ? const Color(0xFFFFF0EC) : Colors.white,
              child: InkWell(
                borderRadius: BorderRadius.circular(AppShape.radius),
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => ItemFormScreen(
                        tripId: data.trip.id,
                        date: item.date,
                        days: days,
                        existing: item))),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Wrap(
                        spacing: 6,
                        runSpacing: 4,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          if (item.transport != Transport.none)
                            Tag(item.transport.label,
                                color: transportColor(item.transport)),
                          if (item.mustGo) const Tag('必去', color: AppColors.coral),
                        ],
                      ),
                      if (item.transport != Transport.none || item.mustGo)
                        const SizedBox(height: 4),
                      Text(item.title, style: text.titleMedium),
                      if (item.note != null) Text(item.note!),
                      if (item.place != null || item.mapUrl != null)
                        TextButton.icon(
                          style: TextButton.styleFrom(
                              padding: EdgeInsets.zero,
                              foregroundColor: AppColors.ink),
                          onPressed: () =>
                              openMap(place: item.place, mapUrl: item.mapUrl),
                          icon: const Icon(Icons.place_outlined, size: 18),
                          label: Text(item.place ?? '開啟地圖'),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// The vertical line and dot of the timeline.
class _RailPainter extends CustomPainter {
  _RailPainter({required this.first, required this.last, required this.mustGo});
  final bool first;
  final bool last;
  final bool mustGo;

  @override
  void paint(Canvas canvas, Size size) {
    final x = size.width / 2;
    const dotY = 30.0;
    final line = Paint()
      ..color = AppColors.ink
      ..strokeWidth = 3;
    canvas.drawLine(Offset(x, first ? dotY : 0),
        Offset(x, last ? dotY : size.height), line);
    canvas.drawCircle(Offset(x, dotY), 8,
        Paint()..color = mustGo ? AppColors.coral : AppColors.yamabuki);
    canvas.drawCircle(
        Offset(x, dotY),
        8,
        Paint()
          ..color = AppColors.ink
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3);
  }

  @override
  bool shouldRepaint(_RailPainter old) =>
      old.first != first || old.last != last || old.mustGo != mustGo;
}
