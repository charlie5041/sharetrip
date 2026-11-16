import 'package:flutter/material.dart';

import '../main.dart';
import '../models/expense.dart';
import '../models/trip.dart';
import 'expenses_tab.dart';
import 'members_tab.dart';
import 'settle_tab.dart';

/// Everything a trip's screens read, gathered from the live streams.
class TripData {
  const TripData({
    required this.trip,
    required this.members,
    required this.expenses,
    required this.settlements,
    required this.myUid,
  });

  final Trip trip;
  final List<Member> members;
  final List<Expense> expenses;
  final List<Settlement> settlements;
  final String myUid;

  Member? get me {
    for (final m in members) {
      if (m.uid == myUid) return m;
    }
    return null;
  }

  String nameOf(String memberId) {
    for (final m in members) {
      if (m.id == memberId) return m.name;
    }
    return '（已移除）';
  }
}

class TripScreen extends StatelessWidget {
  const TripScreen({super.key, required this.tripId});
  final String tripId;

  @override
  Widget build(BuildContext context) {
    final repo = AppScope.of(context).repo;
    return StreamBuilder<Trip>(
      stream: repo.watchTrip(tripId),
      builder: (context, tripSnap) => StreamBuilder<List<Member>>(
        stream: repo.watchMembers(tripId),
        builder: (context, memSnap) => StreamBuilder<List<Expense>>(
          stream: repo.watchExpenses(tripId),
          builder: (context, expSnap) => StreamBuilder<List<Settlement>>(
            stream: repo.watchSettlements(tripId),
            builder: (context, setSnap) {
              if (!tripSnap.hasData ||
                  !memSnap.hasData ||
                  !expSnap.hasData ||
                  !setSnap.hasData) {
                return Scaffold(
                  appBar: AppBar(),
                  body: const Center(child: CircularProgressIndicator()),
                );
              }
              final data = TripData(
                trip: tripSnap.data!,
                members: memSnap.data!,
                expenses: expSnap.data!,
                settlements: setSnap.data!,
                myUid: repo.uid,
              );
              return _TripTabs(data: data);
            },
          ),
        ),
      ),
    );
  }
}

class _TripTabs extends StatelessWidget {
  const _TripTabs({required this.data});
  final TripData data;

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: Text(data.trip.name),
          bottom: const TabBar(
            tabs: [Tab(text: '記帳'), Tab(text: '結算'), Tab(text: '成員')],
          ),
        ),
        body: TabBarView(
          children: [
            ExpensesTab(data: data),
            SettleTab(data: data),
            MembersTab(data: data),
          ],
        ),
      ),
    );
  }
}
