import 'package:flutter/material.dart';

import '../main.dart';
import '../models/itinerary_item.dart';
import '../models/travel.dart';
import '../theme.dart';
import '../widgets/dates.dart';
import '../widgets/ui.dart';

/// Shared scaffold for the small add/edit forms: a save button, an
/// optional delete action, and a simple required-field check.
class _FormPage extends StatelessWidget {
  const _FormPage({
    required this.title,
    required this.children,
    required this.onSave,
    this.onDelete,
    this.error,
  });

  final String title;
  final List<Widget> children;
  final VoidCallback onSave;
  final VoidCallback? onDelete;
  final String? error;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(title),
          actions: [
            if (onDelete != null)
              IconButton(
                  tooltip: '刪除',
                  icon: const Icon(Icons.delete_outline),
                  onPressed: onDelete),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            for (final c in children)
              Padding(padding: const EdgeInsets.only(bottom: 12), child: c),
            if (error != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(error!,
                    style:
                        TextStyle(color: Theme.of(context).colorScheme.error)),
              ),
            FilledButton(onPressed: onSave, child: const Text('儲存')),
          ],
        ),
      );
}

TextField _field(TextEditingController c, String label,
        {TextInputType? keyboard, int maxLines = 1, VoidCallback? onChanged}) =>
    TextField(
      controller: c,
      keyboardType: keyboard,
      maxLines: maxLines,
      decoration: InputDecoration(labelText: label),
      onChanged: onChanged == null ? null : (_) => onChanged(),
    );

Future<String?> _pickTime(BuildContext context, String? current) async {
  final parts = current?.split(':');
  final t = await showTimePicker(
    context: context,
    initialTime: parts == null
        ? const TimeOfDay(hour: 9, minute: 0)
        : TimeOfDay(hour: int.parse(parts[0]), minute: int.parse(parts[1])),
  );
  if (t == null) return current;
  return '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
}

Future<String?> _pickDate(BuildContext context, String? current) async {
  final init = current == null ? DateTime.now() : parseYmd(current);
  final d = await showDatePicker(
    context: context,
    initialDate: init,
    firstDate: DateTime(init.year - 1),
    lastDate: DateTime(init.year + 2),
    locale: const Locale('zh', 'TW'),
  );
  return d == null ? current : ymd(d);
}

// ---- Itinerary item --------------------------------------------------------

class ItemFormScreen extends StatefulWidget {
  const ItemFormScreen({
    super.key,
    required this.tripId,
    required this.date,
    required this.days,
    this.existing,
  });

  final String tripId;
  final String date;
  final List<String> days;
  final ItineraryItem? existing;

  @override
  State<ItemFormScreen> createState() => _ItemFormScreenState();
}

class _ItemFormScreenState extends State<ItemFormScreen> {
  late final _title = TextEditingController(text: widget.existing?.title);
  late final _place = TextEditingController(text: widget.existing?.place);
  late final _mapUrl = TextEditingController(text: widget.existing?.mapUrl);
  late final _note = TextEditingController(text: widget.existing?.note);
  late String _date = widget.existing?.date ?? widget.date;
  late String? _time = widget.existing?.time;
  late Transport _transport = widget.existing?.transport ?? Transport.none;
  late bool _mustGo = widget.existing?.mustGo ?? false;
  String? _error;

  @override
  void dispose() {
    for (final c in [_title, _place, _mapUrl, _note]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (_title.text.trim().isEmpty) {
      setState(() => _error = '請填行程名稱');
      return;
    }
    final nav = Navigator.of(context);
    await AppScope.of(context).repo.saveItem(
          widget.tripId,
          ItineraryItem(
            id: widget.existing?.id ?? '',
            date: _date,
            time: _time,
            title: _title.text.trim(),
            place: trimmedOrNull(_place),
            mapUrl: trimmedOrNull(_mapUrl),
            note: trimmedOrNull(_note),
            transport: _transport,
            mustGo: _mustGo,
            order: widget.existing?.order ?? 0,
          ),
        );
    nav.pop();
  }

  Future<void> _delete() async {
    final repo = AppScope.of(context).repo;
    final nav = Navigator.of(context);
    if (await confirmDelete(context, '這個行程')) {
      await repo.deleteItem(widget.tripId, widget.existing!.id);
      nav.pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return _FormPage(
      title: widget.existing == null ? '加行程' : '編輯行程',
      onSave: _save,
      onDelete: widget.existing == null ? null : _delete,
      error: _error,
      children: [
        _field(_title, '要做什麼，例如「淺草寺」',
            onChanged: () => setState(() => _error = null)),
        DropdownButtonFormField<String>(
          initialValue: widget.days.contains(_date) ? _date : null,
          decoration: const InputDecoration(labelText: '哪一天'),
          items: [
            for (var i = 0; i < widget.days.length; i++)
              DropdownMenuItem(
                  value: widget.days[i],
                  child: Text('Day ${i + 1}　${shortDate(widget.days[i])}')),
          ],
          onChanged: (v) => setState(() => _date = v!),
        ),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: () async {
                  final t = await _pickTime(context, _time);
                  setState(() => _time = t);
                },
                icon: const Icon(Icons.schedule),
                label: Text(_time ?? '時間（可不填）'),
              ),
            ),
            if (_time != null)
              IconButton(
                  tooltip: '不指定時間',
                  onPressed: () => setState(() => _time = null),
                  icon: const Icon(Icons.close)),
          ],
        ),
        const Text('怎麼去'),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final t in Transport.values)
              ChoiceChip(
                label: Text(t.label),
                selected: _transport == t,
                selectedColor: AppColors.mizu,
                backgroundColor: Colors.white,
                onSelected: (_) => setState(() => _transport = t),
              ),
          ],
        ),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('標記為「必去」'),
          value: _mustGo,
          onChanged: (v) => setState(() => _mustGo = v),
        ),
        _field(_place, '地點（可不填）'),
        _field(_mapUrl, '地圖連結（可不填，沒填就用地點搜尋）', keyboard: TextInputType.url),
        _field(_note, '備註（可不填）', maxLines: 3),
      ],
    );
  }
}

// ---- Flight ----------------------------------------------------------------

class FlightFormScreen extends StatefulWidget {
  const FlightFormScreen({super.key, required this.tripId, this.existing});
  final String tripId;
  final Flight? existing;

  @override
  State<FlightFormScreen> createState() => _FlightFormScreenState();
}

class _FlightFormScreenState extends State<FlightFormScreen> {
  late final _no = TextEditingController(text: widget.existing?.flightNo);
  late final _airline = TextEditingController(text: widget.existing?.airline);
  late final _from = TextEditingController(text: widget.existing?.fromCode);
  late final _fromName = TextEditingController(text: widget.existing?.fromName);
  late final _to = TextEditingController(text: widget.existing?.toCode);
  late final _toName = TextEditingController(text: widget.existing?.toName);
  late final _note = TextEditingController(text: widget.existing?.note);
  late String? _departDate = widget.existing?.departDate;
  late String? _departTime = widget.existing?.departTime;
  late String? _arriveDate = widget.existing?.arriveDate;
  late String? _arriveTime = widget.existing?.arriveTime;
  String? _error;

  @override
  void dispose() {
    for (final c in [_no, _airline, _from, _fromName, _to, _toName, _note]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (_no.text.trim().isEmpty ||
        _from.text.trim().isEmpty ||
        _to.text.trim().isEmpty ||
        _departDate == null ||
        _departTime == null) {
      setState(() => _error = '請填航班編號、出發與抵達機場、出發日期和時間');
      return;
    }
    final nav = Navigator.of(context);
    await AppScope.of(context).repo.saveFlight(
          widget.tripId,
          Flight(
            id: widget.existing?.id ?? '',
            flightNo: _no.text.trim().toUpperCase(),
            airline: trimmedOrNull(_airline),
            fromCode: _from.text.trim().toUpperCase(),
            fromName: trimmedOrNull(_fromName),
            toCode: _to.text.trim().toUpperCase(),
            toName: trimmedOrNull(_toName),
            departDate: _departDate!,
            departTime: _departTime!,
            arriveDate: _arriveDate,
            arriveTime: _arriveTime,
            note: trimmedOrNull(_note),
          ),
        );
    nav.pop();
  }

  Future<void> _delete() async {
    final repo = AppScope.of(context).repo;
    final nav = Navigator.of(context);
    if (await confirmDelete(context, '這個航班')) {
      await repo.deleteFlight(widget.tripId, widget.existing!.id);
      nav.pop();
    }
  }

  Widget _when(String label, String? date, String? time,
          ValueChanged<String?> setDate, ValueChanged<String?> setTime) =>
      Row(
        children: [
          Expanded(
            child: OutlinedButton(
              onPressed: () async => setDate(await _pickDate(context, date)),
              child: Text(date == null ? '$label日期' : shortDate(date)),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: OutlinedButton(
              onPressed: () async => setTime(await _pickTime(context, time)),
              child: Text(time ?? '$label時間'),
            ),
          ),
        ],
      );

  @override
  Widget build(BuildContext context) {
    void clear() => setState(() => _error = null);
    return _FormPage(
      title: widget.existing == null ? '新增航班' : '編輯航班',
      onSave: _save,
      onDelete: widget.existing == null ? null : _delete,
      error: _error,
      children: [
        Row(children: [
          Expanded(child: _field(_no, '航班編號，例如 BR198', onChanged: clear)),
          const SizedBox(width: 8),
          Expanded(child: _field(_airline, '航空公司（可不填）')),
        ]),
        Row(children: [
          Expanded(child: _field(_from, '出發機場代碼 TPE', onChanged: clear)),
          const SizedBox(width: 8),
          Expanded(child: _field(_fromName, '名稱（可不填）')),
        ]),
        Row(children: [
          Expanded(child: _field(_to, '抵達機場代碼 NRT', onChanged: clear)),
          const SizedBox(width: 8),
          Expanded(child: _field(_toName, '名稱（可不填）')),
        ]),
        const Text('出發（當地時間）'),
        _when('出發', _departDate, _departTime,
            (v) => setState(() {
                  _departDate = v;
                  _error = null;
                }),
            (v) => setState(() {
                  _departTime = v;
                  _error = null;
                })),
        const Text('抵達（當地時間，可不填）'),
        _when('抵達', _arriveDate, _arriveTime,
            (v) => setState(() => _arriveDate = v),
            (v) => setState(() => _arriveTime = v)),
        _field(_note, '備註，例如航廈、行李額度（可不填）', maxLines: 2),
      ],
    );
  }
}

// ---- Lodging ---------------------------------------------------------------

class LodgingFormScreen extends StatefulWidget {
  const LodgingFormScreen({super.key, required this.tripId, this.existing});
  final String tripId;
  final Lodging? existing;

  @override
  State<LodgingFormScreen> createState() => _LodgingFormScreenState();
}

class _LodgingFormScreenState extends State<LodgingFormScreen> {
  late final _name = TextEditingController(text: widget.existing?.name);
  late final _address = TextEditingController(text: widget.existing?.address);
  late final _mapUrl = TextEditingController(text: widget.existing?.mapUrl);
  late final _conf = TextEditingController(text: widget.existing?.confirmation);
  late final _note = TextEditingController(text: widget.existing?.note);
  late String? _in = widget.existing?.checkIn;
  late String? _out = widget.existing?.checkOut;
  String? _error;

  @override
  void dispose() {
    for (final c in [_name, _address, _mapUrl, _conf, _note]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (_name.text.trim().isEmpty || _in == null || _out == null) {
      setState(() => _error = '請填住宿名稱、入住和退房日期');
      return;
    }
    if (_out!.compareTo(_in!) <= 0) {
      setState(() => _error = '退房要在入住之後');
      return;
    }
    final nav = Navigator.of(context);
    await AppScope.of(context).repo.saveLodging(
          widget.tripId,
          Lodging(
            id: widget.existing?.id ?? '',
            name: _name.text.trim(),
            checkIn: _in!,
            checkOut: _out!,
            address: trimmedOrNull(_address),
            mapUrl: trimmedOrNull(_mapUrl),
            confirmation: trimmedOrNull(_conf),
            note: trimmedOrNull(_note),
          ),
        );
    nav.pop();
  }

  Future<void> _delete() async {
    final repo = AppScope.of(context).repo;
    final nav = Navigator.of(context);
    if (await confirmDelete(context, '這個住宿')) {
      await repo.deleteLodging(widget.tripId, widget.existing!.id);
      nav.pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return _FormPage(
      title: widget.existing == null ? '新增住宿' : '編輯住宿',
      onSave: _save,
      onDelete: widget.existing == null ? null : _delete,
      error: _error,
      children: [
        _field(_name, '住宿名稱',
            onChanged: () => setState(() => _error = null)),
        Row(children: [
          Expanded(
            child: OutlinedButton(
              onPressed: () async {
                final d = await _pickDate(context, _in);
                setState(() {
                  _in = d;
                  _error = null;
                });
              },
              child: Text(_in == null ? '入住日期' : '入住 ${shortDate(_in!)}'),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: OutlinedButton(
              onPressed: () async {
                final d = await _pickDate(context, _out ?? _in);
                setState(() {
                  _out = d;
                  _error = null;
                });
              },
              child: Text(_out == null ? '退房日期' : '退房 ${shortDate(_out!)}'),
            ),
          ),
        ]),
        _field(_address, '地址（可不填）'),
        _field(_mapUrl, '地圖連結（可不填）', keyboard: TextInputType.url),
        _field(_conf, '訂房編號（可不填）'),
        _field(_note, '備註，例如入住時間、門禁密碼（可不填）', maxLines: 3),
      ],
    );
  }
}
