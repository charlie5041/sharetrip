import 'package:flutter/material.dart';

import '../main.dart';
import '../models/trip.dart';
import '../services/trip_repository.dart';
import 'trip_list_screen.dart';
import 'trip_screen.dart';

/// Enter a friend's invite code, then pick your own name from the places
/// the trip's owner set up. Someone with no place cannot join.
class JoinTripScreen extends StatefulWidget {
  const JoinTripScreen({super.key, required this.nickname});
  final String nickname;

  @override
  State<JoinTripScreen> createState() => _JoinTripScreenState();
}

class _JoinTripScreenState extends State<JoinTripScreen> {
  final _code = TextEditingController();
  String? _error;
  bool _busy = false;
  PendingJoin? _pending;
  List<Member>? _places;

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  Future<void> _check() async {
    final repo = AppScope.of(context).repo;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final pending = await repo.checkInvite(_code.text);
      final places = await repo.openPlaces(pending);
      setState(() {
        _busy = false;
        _pending = pending;
        _places = places;
      });
    } catch (e) {
      setState(() {
        _busy = false;
        _error = inviteErrorText(e);
      });
    }
  }

  Future<void> _take(Member m) async {
    final repo = AppScope.of(context).repo;
    final nav = Navigator.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('你是「${m.name}」嗎？'),
        content: const Text('選了之後，記在這個名字的帳都算你的。'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('不是')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('是我')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    setState(() => _busy = true);
    try {
      await repo.join(_pending!, m.id);
      nav.pushReplacement(MaterialPageRoute(
          builder: (_) => TripScreen(tripId: _pending!.tripId)));
    } catch (e) {
      // Someone else took the place a moment earlier, or the owner
      // removed it: show what is left.
      final places = await repo.openPlaces(_pending!);
      if (!mounted) return;
      setState(() {
        _busy = false;
        _places = places;
      });
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('這個名字剛被選走或移除了，請再選一次')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final places = _places;
    return Scaffold(
      appBar: AppBar(title: const Text('加入旅程')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: places == null
            ? [
                const Text('請朋友在旅程的「成員」頁產生邀請碼，24 小時內有效。'),
                const SizedBox(height: 16),
                TextField(
                  controller: _code,
                  textCapitalization: TextCapitalization.characters,
                  decoration: InputDecoration(
                    labelText: '邀請碼（8 碼）',
                    errorText: _error,
                  ),
                  onSubmitted: (_) => _check(),
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: _busy ? null : _check,
                  child: const Text('下一步'),
                ),
              ]
            : places.isEmpty
                ? [
                    const Text('名單上沒有空著的名字。請群主先在「成員」頁把你加進名單，再用同一組邀請碼加入。'),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: _busy ? null : _check,
                      child: const Text('重新整理'),
                    ),
                  ]
                : [
                    Text('你是哪一位？',
                        style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 4),
                    const Text('沒有你的名字的話，請群主先把你加進名單。'),
                    const SizedBox(height: 12),
                    for (final m in places)
                      Card(
                        child: ListTile(
                          enabled: !_busy,
                          title: Text(m.name),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () => _take(m),
                        ),
                      ),
                  ],
      ),
    );
  }
}
