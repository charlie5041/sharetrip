import 'package:flutter/material.dart';

import '../main.dart';
import 'trip_list_screen.dart';
import 'trip_screen.dart';

/// Enter a friend's invite code. Someone a friend already added by name
/// claims that name afterwards, on the members tab.
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

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  Future<void> _join() async {
    final repo = AppScope.of(context).repo;
    final nav = Navigator.of(context);
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final pending = await repo.checkInvite(_code.text);
      await repo.join(pending, nickname: widget.nickname);
      nav.pushReplacement(MaterialPageRoute(
          builder: (_) => TripScreen(tripId: pending.tripId)));
    } catch (e) {
      setState(() {
        _busy = false;
        _error = inviteErrorText(e);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('加入旅程')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('請朋友在旅程的「成員」頁產生邀請碼，24 小時內有效。\n如果朋友已經先用你的名字記帳，加入後到「成員」頁點那個名字，選「這是我」。'),
          const SizedBox(height: 16),
          TextField(
            controller: _code,
            textCapitalization: TextCapitalization.characters,
            decoration: InputDecoration(
              labelText: '邀請碼（8 碼）',
              errorText: _error,
            ),
            onSubmitted: (_) => _join(),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _busy ? null : _join,
            child: const Text('加入'),
          ),
        ],
      ),
    );
  }
}
