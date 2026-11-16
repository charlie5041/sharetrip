import 'package:flutter/material.dart';

import '../main.dart';
import '../models/currency.dart';
import '../widgets/dates.dart';
import 'trip_screen.dart';

class TripFormScreen extends StatefulWidget {
  const TripFormScreen({super.key, required this.nickname});
  final String nickname;

  @override
  State<TripFormScreen> createState() => _TripFormScreenState();
}

class _TripFormScreenState extends State<TripFormScreen> {
  final _name = TextEditingController();
  String _currency = 'TWD';
  DateTimeRange? _range;
  bool _saving = false;

  @override
  void dispose() {
    _name.dispose();
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
    if (_name.text.trim().isEmpty || _range == null) return;
    setState(() => _saving = true);
    final nav = Navigator.of(context);
    final id = await AppScope.of(context).repo.createTrip(
          name: _name.text,
          baseCurrency: _currency,
          startDate: ymd(_range!.start),
          endDate: ymd(_range!.end),
          nickname: widget.nickname,
        );
    nav.pushReplacement(
        MaterialPageRoute(builder: (_) => TripScreen(tripId: id)));
  }

  @override
  Widget build(BuildContext context) {
    final ready = _name.text.trim().isNotEmpty && _range != null;
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
          FilledButton(
            onPressed: ready && !_saving ? _save : null,
            child: const Text('建立'),
          ),
        ],
      ),
    );
  }
}
