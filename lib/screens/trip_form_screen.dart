import 'package:flutter/material.dart';

import '../main.dart';
import '../widgets/ui.dart';
import '../models/currency.dart';
import '../widgets/dates.dart';
import 'trip_screen.dart';

class TripFormScreen extends StatefulWidget {
  const TripFormScreen({super.key});

  @override
  State<TripFormScreen> createState() => _TripFormScreenState();
}

class _TripFormScreenState extends State<TripFormScreen> {
  final _name = TextEditingController();
  final _me = TextEditingController();
  final _friend = TextEditingController();
  final _friends = <String>[];
  String _currency = 'TWD';
  DateTimeRange? _range;
  bool _saving = false;
  bool _askedName = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_askedName) return;
    _askedName = true;
    // The name used last time, so a second trip needs no typing.
    AppScope.of(context).repo.loadNickname().then((n) {
      if (mounted && n != null && _me.text.isEmpty) {
        setState(() => _me.text = n);
      }
    }, onError: (Object _) {});
  }

  @override
  void dispose() {
    _name.dispose();
    _me.dispose();
    _friend.dispose();
    super.dispose();
  }

  Future<void> _pickDates() async {
    final now = DateTime.now();
    final r = await showDateRangePicker(
      context: context,
      firstDate: DateTime(now.year - 1),
      lastDate: DateTime(now.year + 3),
      initialDateRange: _range,
      locale: const Locale('zh', 'TW'),
    );
    if (r != null) setState(() => _range = r);
  }

  Future<void> _save() async {
    if (_saving || !_ready) return;
    setState(() => _saving = true);
    final nav = Navigator.of(context);
    final repo = AppScope.of(context).repo;
    final id = repo.newTripId();
    final me = _me.text.trim();
    repo.setNickname(me).catchError((Object _) {});
    writeInBackground(
      context,
      repo.createTrip(
          id: id,
          name: _name.text,
          baseCurrency: _currency,
          startDate: ymd(_range!.start),
          endDate: ymd(_range!.end),
          nickname: me,
          others: [..._friends, if (_friend.text.trim().isNotEmpty) _friend.text],
        ),
    );
    nav.pushReplacement(
        MaterialPageRoute(builder: (_) => TripScreen(tripId: id)));
  }

  void _addFriend() {
    final n = _friend.text.trim();
    if (n.isEmpty) return;
    setState(() {
      _friends.add(n);
      _friend.clear();
    });
  }

  /// Dates normally come from the platform picker, which tests can't drive.
  @visibleForTesting
  void debugSetRange(DateTimeRange r) => setState(() => _range = r);

  bool get _ready =>
      _name.text.trim().isNotEmpty &&
      _range != null &&
      _me.text.trim().isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final ready = _ready;
    return Scaffold(
      appBar: AppBar(title: const Text('新旅程')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _name,
            maxLength: 30,
            decoration: const InputDecoration(labelText: '旅程名稱，例如「東京五日」'),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: _pickDates,
            icon: const Icon(Icons.date_range),
            label: Text(_range == null
                ? '選擇日期'
                : '${shortDate(ymd(_range!.start))} – ${shortDate(ymd(_range!.end))}'),
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            initialValue: _currency,
            decoration: const InputDecoration(labelText: '結算幣別（最後大家用什麼錢互轉）'),
            items: [
              for (final c in Currency.all)
                DropdownMenuItem(value: c.code, child: Text('${c.name} ${c.code}')),
            ],
            onChanged: (v) => setState(() => _currency = v!),
          ),
          const SizedBox(height: 24),
          Text('成員名單', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          const Text('朋友用邀請碼加入時，要從這份名單選自己的名字。之後只有你能增減成員。'),
          const SizedBox(height: 8),
          TextField(
            controller: _me,
            maxLength: 20,
            decoration: const InputDecoration(labelText: '你的名字'),
            onChanged: (_) => setState(() {}),
          ),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final (i, n) in _friends.indexed)
                InputChip(
                  label: Text(n),
                  onDeleted: () => setState(() => _friends.removeAt(i)),
                ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _friend,
                  maxLength: 20,
                  decoration: const InputDecoration(labelText: '朋友的名字'),
                  onSubmitted: (_) => _addFriend(),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filledTonal(
                tooltip: '加進名單',
                onPressed: _addFriend,
                icon: const Icon(Icons.add),
              ),
            ],
          ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: ready && !_saving ? _save : null,
            child: const Text('建立'),
          ),
        ],
      ),
    );
  }
}
