import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../logic/split.dart';
import '../main.dart';
import '../models/currency.dart';
import '../models/expense.dart';
import '../theme.dart';
import 'trip_screen.dart';

class ExpenseFormScreen extends StatefulWidget {
  const ExpenseFormScreen({super.key, required this.data, this.existing});

  final TripData data;
  final Expense? existing;

  @override
  State<ExpenseFormScreen> createState() => _ExpenseFormScreenState();
}

class _ExpenseFormScreenState extends State<ExpenseFormScreen> {
  final _title = TextEditingController();
  final _amount = TextEditingController();
  final _rate = TextEditingController();
  final _note = TextEditingController();
  late String _currency;
  late String _payerId;
  SplitMode _mode = SplitMode.equal;
  ExpenseCategory _category = ExpenseCategory.food;
  late DateTime _when;
  final _included = <String>{};
  final _values = <String, TextEditingController>{};
  bool _fetchingRate = false;
  String? _rateNote;
  String? _error;
  bool _saving = false;

  TripData get data => widget.data;
  String get _base => data.trip.baseCurrency;

  @override
  void initState() {
    super.initState();
    final e = widget.existing;
    for (final m in data.members) {
      _values[m.id] = TextEditingController();
    }
    if (e == null) {
      _currency = _base;
      _payerId = data.me?.id ?? data.members.first.id;
      _when = DateTime.now();
      _included.addAll(data.members.map((m) => m.id));
      _rate.text = '1';
    } else {
      final cur = Currency.of(e.currency);
      _title.text = e.title;
      _amount.text = cur.format(e.amountMinor);
      _rate.text = e.rateToBase;
      _note.text = e.note ?? '';
      _currency = e.currency;
      _payerId = e.payerId;
      _mode = e.splitMode;
      _category = e.category;
      _when = DateTime.fromMillisecondsSinceEpoch(e.occurredAt);
      _included.addAll(e.splits.keys);
      e.splits.forEach((id, v) {
        _values[id]?.text =
            _mode == SplitMode.exact ? cur.format(v) : v.toString();
      });
    }
  }

  @override
  void dispose() {
    for (final c in [_title, _amount, _rate, _note, ..._values.values]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _onCurrency(String code) async {
    setState(() {
      _currency = code;
      _rateNote = null;
    });
    if (code == _base) {
      _rate.text = '1';
      return;
    }
    setState(() => _fetchingRate = true);
    final r = await AppScope.of(context).rates.rate(code, _base);
    if (!mounted || _currency != code) return;
    setState(() {
      _fetchingRate = false;
      if (r != null) {
        _rate.text = r;
        _rateNote = '今日參考匯率，可以改成刷卡實際匯率';
      } else {
        _rate.text = '';
        _rateNote = '查不到匯率（沒網路？），請手動輸入';
      }
    });
  }

  /// Builds the expense from the form, or sets [_error] and returns null.
  Expense? _build() {
    final cur = Currency.of(_currency);
    final amount = cur.parse(_amount.text);
    String? err;
    if (_title.text.trim().isEmpty) err = '請填項目名稱';
    if (amount == null || amount == 0) err ??= '金額格式不正確';
    final rate = _currency == _base ? '1' : _rate.text.trim();
    if (Rate.tryParse(rate) == null) err ??= '匯率格式不正確';
    if (_included.isEmpty) err ??= '至少要有一個人分攤';

    final splits = <String, int>{};
    for (final id in _included) {
      switch (_mode) {
        case SplitMode.equal:
          splits[id] = 1;
        case SplitMode.shares:
          final v = int.tryParse(_values[id]!.text.trim());
          if (v == null || v <= 0) err ??= '份數要是正整數';
          splits[id] = v ?? 0;
        case SplitMode.exact:
          final v = cur.parse(_values[id]!.text);
          if (v == null) err ??= '請填每個人的金額';
          splits[id] = v ?? 0;
      }
    }
    if (err != null) {
      setState(() => _error = err);
      return null;
    }
    final e = Expense(
      id: widget.existing?.id ?? '',
      title: _title.text.trim(),
      category: _category,
      amountMinor: amount!,
      currency: _currency,
      rateToBase: rate,
      payerId: _payerId,
      splitMode: _mode,
      splits: splits,
      occurredAt: _when.millisecondsSinceEpoch,
      createdBy: widget.existing?.createdBy ?? '',
      updatedAt: 0,
      note: _note.text.trim().isEmpty ? null : _note.text.trim(),
    );
    try {
      sharesInOriginal(e, [for (final m in data.members) m.id]);
    } on SplitError catch (x) {
      setState(() => _error = x.message);
      return null;
    }
    return e;
  }

  Future<void> _save() async {
    final e = _build();
    if (e == null) return;
    setState(() => _saving = true);
    final nav = Navigator.of(context);
    await AppScope.of(context).repo.saveExpense(data.trip.id, e);
    nav.pop();
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('刪除這筆？'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('刪除')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    final nav = Navigator.of(context);
    await AppScope.of(context)
        .repo
        .deleteExpense(data.trip.id, widget.existing!.id);
    nav.pop();
  }

  Future<void> _pickWhen() async {
    final d = await showDatePicker(
      context: context,
      initialDate: _when,
      firstDate: DateTime(_when.year - 1),
      lastDate: DateTime(_when.year + 2),
      locale: const Locale('zh', 'TW'),
    );
    if (d == null || !mounted) return;
    final t = await showTimePicker(
        context: context, initialTime: TimeOfDay.fromDateTime(_when));
    setState(() => _when = DateTime(
        d.year, d.month, d.day, t?.hour ?? _when.hour, t?.minute ?? _when.minute));
  }

  @override
  Widget build(BuildContext context) {
    final foreign = _currency != _base;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.existing == null ? '記一筆' : '編輯'),
        actions: [
          if (widget.existing != null)
            IconButton(
                tooltip: '刪除',
                icon: const Icon(Icons.delete_outline),
                onPressed: _delete),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _title,
            decoration: const InputDecoration(labelText: '項目，例如「一蘭拉麵」'),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final c in ExpenseCategory.values)
                ChoiceChip(
                  label: Text(c.label),
                  selected: _category == c,
                  selectedColor: AppColors.coral,
                  backgroundColor: Colors.white,
                  onSelected: (_) => setState(() => _category = c),
                ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                flex: 3,
                child: TextField(
                  controller: _amount,
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: '金額'),
                  onChanged: (_) => setState(() {}),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                flex: 2,
                child: DropdownButtonFormField<String>(
                  initialValue: _currency,
                  decoration: const InputDecoration(labelText: '幣別'),
                  items: [
                    for (final c in Currency.all)
                      DropdownMenuItem(value: c.code, child: Text(c.code)),
                  ],
                  onChanged: (v) => _onCurrency(v!),
                ),
              ),
            ],
          ),
          if (foreign) ...[
            const SizedBox(height: 12),
            TextField(
              controller: _rate,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: '1 $_currency = ? $_base',
                helperText: _rateNote,
                suffixIcon: _fetchingRate
                    ? const Padding(
                        padding: EdgeInsets.all(12),
                        child: SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2)),
                      )
                    : null,
              ),
              onChanged: (_) => setState(() {}),
            ),
            _ConvertedHint(
                amount: _amount.text, from: _currency, to: _base, rate: _rate.text),
          ],
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _payerId,
            decoration: const InputDecoration(labelText: '誰付的'),
            items: [
              for (final m in data.members)
                DropdownMenuItem(value: m.id, child: Text(m.name)),
            ],
            onChanged: (v) => setState(() => _payerId = v!),
          ),
          const SizedBox(height: 16),
          SegmentedButton<SplitMode>(
            segments: const [
              ButtonSegment(value: SplitMode.equal, label: Text('平均')),
              ButtonSegment(value: SplitMode.exact, label: Text('指定金額')),
              ButtonSegment(value: SplitMode.shares, label: Text('份數')),
            ],
            selected: {_mode},
            onSelectionChanged: (s) => setState(() {
              _mode = s.first;
              if (_mode == SplitMode.shares) {
                for (final c in _values.values) {
                  if (int.tryParse(c.text) == null) c.text = '1';
                }
              }
            }),
          ),
          const SizedBox(height: 8),
          for (final m in data.members)
            Row(
              children: [
                Expanded(
                  child: CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    controlAffinity: ListTileControlAffinity.leading,
                    title: Text(m.name),
                    value: _included.contains(m.id),
                    onChanged: (v) => setState(() =>
                        v! ? _included.add(m.id) : _included.remove(m.id)),
                  ),
                ),
                if (_mode != SplitMode.equal && _included.contains(m.id))
                  SizedBox(
                    width: 110,
                    child: TextField(
                      controller: _values[m.id],
                      keyboardType: TextInputType.numberWithOptions(
                          decimal: _mode == SplitMode.exact),
                      decoration: InputDecoration(
                        isDense: true,
                        labelText: _mode == SplitMode.exact ? _currency : '份',
                      ),
                    ),
                  ),
              ],
            ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: _pickWhen,
            icon: const Icon(Icons.schedule),
            label: Text(DateFormat('yyyy/M/d HH:mm').format(_when)),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _note,
            decoration: const InputDecoration(labelText: '備註（可不填）'),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _saving ? null : _save,
            child: const Text('儲存'),
          ),
        ],
      ),
    );
  }
}

class _ConvertedHint extends StatelessWidget {
  const _ConvertedHint(
      {required this.amount, required this.from, required this.to, required this.rate});
  final String amount;
  final String from;
  final String to;
  final String rate;

  @override
  Widget build(BuildContext context) {
    final f = Currency.of(from);
    final t = Currency.of(to);
    final a = f.parse(amount);
    final r = Rate.tryParse(rate);
    if (a == null || r == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 6, left: 4),
      child: Text('約 ${t.format(convert(a, f, t, r))} $to'),
    );
  }
}
