import 'package:flutter/material.dart';

import '../main.dart';
import '../models/travel.dart';
import '../theme.dart';
import 'trip_screen.dart';

/// The three shared lists: to-do, before leaving, and packing.
class ChecklistTab extends StatefulWidget {
  const ChecklistTab({super.key, required this.data});
  final TripData data;

  @override
  State<ChecklistTab> createState() => _ChecklistTabState();
}

class _ChecklistTabState extends State<ChecklistTab> {
  ChecklistKind _kind = ChecklistKind.todo;
  final _input = TextEditingController();

  @override
  void dispose() {
    _input.dispose();
    super.dispose();
  }

  String get _tripId => widget.data.trip.id;
  String get _myName => widget.data.me?.name ?? '';

  Future<void> _add() async {
    final t = _input.text.trim();
    if (t.isEmpty) return;
    _input.clear();
    await AppScope.of(context).repo.addChecklistEntries(_tripId, _kind, [t]);
  }

  @override
  Widget build(BuildContext context) {
    final repo = AppScope.of(context).repo;
    final entries =
        widget.data.checklist.where((e) => e.kind == _kind).toList();
    final done = entries.where((e) => e.done).length;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: SegmentedButton<ChecklistKind>(
            segments: [
              for (final k in ChecklistKind.values)
                ButtonSegment(value: k, label: Text(k.label)),
            ],
            selected: {_kind},
            showSelectedIcon: false,
            onSelectionChanged: (s) => setState(() => _kind = s.first),
          ),
        ),
        if (entries.isNotEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
            child: Row(children: [
              Text('完成 $done / ${entries.length}'),
              const SizedBox(width: 12),
              Expanded(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(999),
                  child: LinearProgressIndicator(
                    value: done / entries.length,
                    minHeight: 10,
                    color: AppColors.wakatake,
                    backgroundColor: Colors.white,
                  ),
                ),
              ),
            ]),
          ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.only(bottom: 16),
            children: [
              if (entries.isEmpty && _kind.suggestions.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: OutlinedButton.icon(
                    onPressed: () => repo.addChecklistEntries(
                        _tripId, _kind, _kind.suggestions),
                    icon: const Icon(Icons.auto_awesome_outlined),
                    label: Text('加入常用的「${_kind.label}」項目'),
                  ),
                ),
              if (entries.isEmpty && _kind.suggestions.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(24),
                  child: Text('還沒有項目，在下面輸入新增', textAlign: TextAlign.center),
                ),
              for (final e in entries)
                Dismissible(
                  key: ValueKey(e.id),
                  direction: DismissDirection.endToStart,
                  background: Container(
                    alignment: Alignment.centerRight,
                    padding: const EdgeInsets.only(right: 24),
                    color: AppColors.coral,
                    child: const Icon(Icons.delete_outline),
                  ),
                  onDismissed: (_) => repo.deleteChecklistEntry(_tripId, e.id),
                  child: CheckboxListTile(
                    value: e.done,
                    controlAffinity: ListTileControlAffinity.leading,
                    activeColor: AppColors.wakatake,
                    checkColor: AppColors.ink,
                    title: Text(
                      e.text,
                      style: e.done
                          ? const TextStyle(
                              decoration: TextDecoration.lineThrough)
                          : null,
                    ),
                    subtitle: e.done && e.doneBy != null && e.doneBy!.isNotEmpty
                        ? Text('${e.doneBy} 完成')
                        : null,
                    onChanged: (v) =>
                        repo.setChecklistDone(_tripId, e.id, v!, _myName),
                  ),
                ),
            ],
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
            child: Row(children: [
              Expanded(
                child: TextField(
                  controller: _input,
                  decoration: InputDecoration(
                      labelText: '新增「${_kind.label}」項目', isDense: true),
                  onSubmitted: (_) => _add(),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                style: IconButton.styleFrom(
                    backgroundColor: AppColors.yamabuki,
                    foregroundColor: AppColors.ink),
                onPressed: _add,
                icon: const Icon(Icons.add),
              ),
            ]),
          ),
        ),
      ],
    );
  }
}
