import 'package:flutter/material.dart';

import '../illustrations/trip_scene.dart';
import '../models/travel.dart';
import '../theme.dart';
import '../widgets/dates.dart';
import '../widgets/maps.dart';
import '../widgets/ui.dart';
import 'travel_forms.dart';
import 'trip_screen.dart';

/// Title, countdown, flights and where everyone sleeps.
class OverviewTab extends StatelessWidget {
  const OverviewTab({super.key, required this.data});
  final TripData data;

  @override
  Widget build(BuildContext context) {
    final trip = data.trip;
    return ListView(
      padding: const EdgeInsets.only(bottom: 32),
      children: [
        _Hero(data: data),
        SectionHeading('航班',
            action: TextButton.icon(
              onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => FlightFormScreen(tripId: trip.id))),
              icon: const Icon(Icons.add),
              label: const Text('新增'),
            )),
        if (data.flights.isEmpty)
          const _Empty('還沒有航班，新增後大家都看得到'),
        for (final f in data.flights) _FlightCard(tripId: trip.id, f: f),
        SectionHeading('住宿',
            action: TextButton.icon(
              onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => LodgingFormScreen(tripId: trip.id))),
              icon: const Icon(Icons.add),
              label: const Text('新增'),
            )),
        if (data.lodgings.isEmpty) const _Empty('還沒有住宿資訊'),
        for (final l in data.lodgings) _LodgingCard(tripId: trip.id, l: l),
      ],
    );
  }
}

class _Hero extends StatelessWidget {
  const _Hero({required this.data});
  final TripData data;

  @override
  Widget build(BuildContext context) {
    final trip = data.trip;
    final now = DateTime.now();
    final days = daysUntil(trip.startDate, now);
    final ended = daysUntil(trip.endDate, now) < 0;
    final text = Theme.of(context).textTheme;
    final (label, big) = days > 0
        ? ('距離出發還有', '$days 天')
        : ended
            ? ('旅程已結束', '辛苦了')
            : ('旅行中', '第 ${1 - days} 天');
    return Card(
      color: AppColors.paper,
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            TripScene(tripName: trip.name),
            const SizedBox(height: 12),
            Text(trip.name, style: text.headlineMedium),
            const SizedBox(height: 4),
            Text('${shortDate(trip.startDate)} – ${shortDate(trip.endDate)}'),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              decoration: BoxDecoration(
                color: AppColors.coral,
                border: Border.all(color: AppColors.ink, width: 3),
                borderRadius: BorderRadius.circular(AppShape.radius),
              ),
              child: Column(
                children: [
                  Text(label),
                  Text(big, style: text.displaySmall),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _FlightCard extends StatelessWidget {
  const _FlightCard({required this.tripId, required this.f});
  final String tripId;
  final Flight f;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    Widget end(String code, String? name, String? date, String? time,
            CrossAxisAlignment a) =>
        Expanded(
          child: Column(
            crossAxisAlignment: a,
            children: [
              Text(code, style: text.headlineMedium),
              if (name != null) Text(name, style: text.bodySmall),
              if (time != null) Text(time, style: text.titleMedium),
              if (date != null) Text(shortDate(date), style: text.bodySmall),
            ],
          ),
        );
    return Card(
      color: AppColors.yamabuki,
      child: InkWell(
        borderRadius: BorderRadius.circular(AppShape.radius),
        onTap: () => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => FlightFormScreen(tripId: tripId, existing: f))),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              Row(
                children: [
                  const Icon(Icons.flight_takeoff),
                  const SizedBox(width: 8),
                  Text([f.airline, f.flightNo].whereType<String>().join(' '),
                      style: text.titleMedium),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  end(f.fromCode, f.fromName, f.departDate, f.departTime,
                      CrossAxisAlignment.start),
                  const Icon(Icons.arrow_forward),
                  end(f.toCode, f.toName, f.arriveDate, f.arriveTime,
                      CrossAxisAlignment.end),
                ],
              ),
              if (f.note != null) ...[
                const SizedBox(height: 8),
                Align(alignment: Alignment.centerLeft, child: Text(f.note!)),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _LodgingCard extends StatelessWidget {
  const _LodgingCard({required this.tripId, required this.l});
  final String tripId;
  final Lodging l;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final nights = parseYmd(l.checkOut).difference(parseYmd(l.checkIn)).inDays;
    return Card(
      color: AppColors.wakatake,
      child: InkWell(
        borderRadius: BorderRadius.circular(AppShape.radius),
        onTap: () => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => LodgingFormScreen(tripId: tripId, existing: l))),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                const Icon(Icons.hotel_outlined),
                const SizedBox(width: 8),
                Expanded(child: Text(l.name, style: text.titleMedium)),
              ]),
              const SizedBox(height: 4),
              Text('${shortDate(l.checkIn)} 入住 → ${shortDate(l.checkOut)} 退房'
                  '（$nights 晚）'),
              if (l.confirmation != null) Text('訂房編號：${l.confirmation}'),
              if (l.address != null) Text(l.address!),
              if (l.note != null) Text(l.note!),
              if (l.address != null || l.mapUrl != null)
                Align(
                  alignment: Alignment.centerRight,
                  child: OutlinedButton.icon(
                    onPressed: () => openMap(
                        place: l.address ?? l.name, mapUrl: l.mapUrl),
                    icon: const Icon(Icons.map_outlined),
                    label: const Text('地圖'),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
        child: Text(text, style: Theme.of(context).textTheme.bodySmall),
      );
}

