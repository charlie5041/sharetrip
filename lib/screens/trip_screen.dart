import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models/expense.dart';
import '../models/itinerary_item.dart';
import '../models/travel.dart';
import '../models/trip.dart';
import 'checklist_tab.dart';
import 'expenses_tab.dart';
import 'itinerary_tab.dart';
import 'members_tab.dart';
import 'overview_tab.dart';
import 'settle_tab.dart';

/// Everything a trip's screens read, gathered from the live streams.
class TripData {
  const TripData({
    required this.trip,
    required this.members,
    required this.expenses,
    required this.settlements,
    required this.myUid,
    this.items = const [],
    this.flights = const [],
    this.lodgings = const [],
    this.checklist = const [],
  });

  final Trip trip;
  final List<Member> members;
  final List<Expense> expenses;
  final List<Settlement> settlements;
  final List<ItineraryItem> items;
  final List<Flight> flights;
  final List<Lodging> lodgings;
  final List<ChecklistEntry> checklist;
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

/// Subscribes to every collection of one trip and rebuilds the tabs with
/// the latest of each.
class TripScreen extends StatefulWidget {
  const TripScreen({super.key, required this.tripId, this.initialTab = 0});
  final String tripId;
  final int initialTab;

  @override
  State<TripScreen> createState() => _TripScreenState();
}

class _TripScreenState extends State<TripScreen> {
  final _subs = <StreamSubscription<dynamic>>[];
  Trip? _trip;
  List<Member>? _members;
  List<Expense>? _expenses;
  List<Settlement>? _settlements;
  List<ItineraryItem>? _items;
  List<Flight>? _flights;
  List<Lodging>? _lodgings;
  List<ChecklistEntry>? _checklist;
  late int _tab = widget.initialTab;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_subs.isNotEmpty) return;
    final repo = AppScope.of(context).repo;
    final id = widget.tripId;
    void on<T>(Stream<T> s, void Function(T) set) =>
        _subs.add(s.listen((v) => setState(() => set(v))));
    on(repo.watchTrip(id), (v) => _trip = v);
    on(repo.watchMembers(id), (v) => _members = v);
    on(repo.watchExpenses(id), (v) => _expenses = v);
    on(repo.watchSettlements(id), (v) => _settlements = v);
    on(repo.watchItems(id), (v) => _items = v);
    on(repo.watchFlights(id), (v) => _flights = v);
    on(repo.watchLodgings(id), (v) => _lodgings = v);
    on(repo.watchChecklist(id), (v) => _checklist = v);
  }

  @override
  void dispose() {
    for (final s in _subs) {
      s.cancel();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if ([_trip, _members, _expenses, _settlements, _items, _flights,
        _lodgings, _checklist].contains(null)) {
      return Scaffold(
        appBar: AppBar(),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    final data = TripData(
      trip: _trip!,
      members: _members!,
      expenses: _expenses!,
      settlements: _settlements!,
      items: _items!,
      flights: _flights!,
      lodgings: _lodgings!,
      checklist: _checklist!,
      myUid: AppScope.of(context).repo.uid,
    );
    final pages = <Widget>[
      OverviewTab(data: data),
      ItineraryTab(data: data),
      ChecklistTab(data: data),
      _MoneyTabs(data: data),
      MembersTab(data: data),
    ];
    return Scaffold(
      appBar: AppBar(title: Text(data.trip.name)),
      body: IndexedStack(index: _tab, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), label: '首頁'),
          NavigationDestination(icon: Icon(Icons.route_outlined), label: '行程'),
          NavigationDestination(
              icon: Icon(Icons.checklist_outlined), label: '清單'),
          NavigationDestination(
              icon: Icon(Icons.payments_outlined), label: '記帳'),
          NavigationDestination(icon: Icon(Icons.group_outlined), label: '成員'),
        ],
      ),
    );
  }
}

class _MoneyTabs extends StatelessWidget {
  const _MoneyTabs({required this.data});
  final TripData data;

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Column(
        children: [
          const TabBar(tabs: [Tab(text: '支出'), Tab(text: '結算')]),
          Expanded(
            child: TabBarView(
              children: [ExpensesTab(data: data), SettleTab(data: data)],
            ),
          ),
        ],
      ),
    );
  }
}
