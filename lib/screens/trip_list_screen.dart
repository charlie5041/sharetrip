import 'package:flutter/material.dart';

import '../main.dart';
import '../models/trip.dart';
import '../services/trip_repository.dart';
import '../theme.dart';
import '../widgets/dates.dart';
import '../widgets/install_guide.dart';
import 'account_sheet.dart';
import 'join_trip_screen.dart';
import 'trip_form_screen.dart';
import 'trip_screen.dart';

class TripListScreen extends StatelessWidget {
  const TripListScreen({super.key, required this.nickname});

  final String nickname;

  @override
  Widget build(BuildContext context) {
    final repo = AppScope.of(context).repo;
    return Scaffold(
      appBar: AppBar(
        title: const Text('我的旅程'),
        actions: [
          if (AppScope.of(context).account != null)
            IconButton(
              tooltip: '備份我的身分',
              icon: Icon(AppScope.of(context).account!.linkedEmail == null
                  ? Icons.shield_outlined
                  : Icons.verified_user_outlined),
              onPressed: () => showAccountSheet(context),
            ),
          IconButton(
            tooltip: '用邀請碼加入',
            icon: const Icon(Icons.group_add_outlined),
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => JoinTripScreen(nickname: nickname))),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => TripFormScreen(nickname: nickname))),
        icon: const Icon(Icons.add),
        label: const Text('新旅程'),
      ),
      body: Column(
        children: [
          const InstallBanner(margin: EdgeInsets.fromLTRB(16, 8, 16, 0)),
          Expanded(child: _trips(repo)),
        ],
      ),
    );
  }

  Widget _trips(TripRepository repo) => StreamBuilder<List<Trip>>(
        stream: repo.watchMyTrips(),
        builder: (context, snap) {
          if (!snap.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          final trips = snap.data!;
          if (trips.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Text('還沒有旅程\n按右下角建立一趟，或用邀請碼加入朋友的旅程',
                    textAlign: TextAlign.center),
              ),
            );
          }
          return ListView(
            padding: const EdgeInsets.only(top: 8, bottom: 96),
            children: [for (final t in trips) _TripCard(trip: t)],
          );
        },
      );
}

class _TripCard extends StatelessWidget {
  const _TripCard({required this.trip});
  final Trip trip;

  @override
  Widget build(BuildContext context) {
    final days = daysUntil(trip.startDate, DateTime.now());
    final badge = days > 0
        ? '還有 $days 天'
        : daysUntil(trip.endDate, DateTime.now()) >= 0
            ? '旅行中'
            : '已結束';
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(AppShape.radius),
        onTap: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => TripScreen(tripId: trip.id))),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(trip.name,
                        style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 4),
                    Text(
                        '${shortDate(trip.startDate)} – ${shortDate(trip.endDate)}'
                        '　·　${trip.baseCurrency} 結算'),
                  ],
                ),
              ),
              Chip(
                label: Text(badge),
                backgroundColor:
                    days > 0 ? AppColors.yamabuki : AppColors.wakatake,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Shown when an invite fails; kept here so both join paths word it alike.
String inviteErrorText(Object e) =>
    e is InviteError ? e.message : '加入失敗，請檢查網路後再試一次';
