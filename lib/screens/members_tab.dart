import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../main.dart';
import '../models/trip.dart';
import '../services/trip_repository.dart';
import '../theme.dart';
import 'trip_screen.dart';

class MembersTab extends StatelessWidget {
  const MembersTab({super.key, required this.data});
  final TripData data;

  Future<void> _invite(BuildContext context) async {
    final repo = AppScope.of(context).repo;
    final code = await repo.createInvite(data.trip.id);
    if (!context.mounted) return;
    final shown = TripRepository.formatCode(code);
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('邀請碼'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SelectableText(shown,
                style: Theme.of(ctx).textTheme.displaySmall),
            const SizedBox(height: 8),
            const Text('24 小時內有效。朋友打開同遊，按右上角「用邀請碼加入」。'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              Clipboard.setData(ClipboardData(text: shown));
              Navigator.pop(ctx);
            },
            child: const Text('複製'),
          ),
          FilledButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('好')),
        ],
      ),
    );
  }

  Future<void> _addNameOnly(BuildContext context) async {
    final name = await _askName(context, title: '加入沒有 app 的成員');
    if (name == null || !context.mounted) return;
    await AppScope.of(context).repo.addNameOnlyMember(data.trip.id, name);
  }

  Future<void> _tap(BuildContext context, Member m) async {
    final repo = AppScope.of(context).repo;
    final me = data.me;
    final canClaim = me != null && m.id != me.id;
    final relink = m.uid != null;
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.edit),
              title: const Text('改名字'),
              onTap: () => Navigator.pop(ctx, 'rename'),
            ),
            if (canClaim)
              ListTile(
                leading: const Icon(Icons.how_to_reg),
                title: const Text('這是我'),
                subtitle: Text(relink
                    ? '換了手機或資料不見時用：把這個名字接回這台裝置'
                    : '把這個名字的帳都算到你身上'),
                onTap: () => Navigator.pop(ctx, 'claim'),
              ),
          ],
        ),
      ),
    );
    if (!context.mounted) return;
    if (action == 'rename') {
      final name = await _askName(context, title: '改名字', initial: m.name);
      if (name != null) await repo.renameMember(data.trip.id, m.id, name);
    } else if (action == 'claim') {
      if (relink && !await _confirmRelink(context, m.name)) return;
      if (!context.mounted) return;
      try {
        await repo.claimMember(data.trip.id, m.id, me!.id);
      } on StateError catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text(e.message)));
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 8),
      children: [
        for (final m in data.members)
          Card(
            child: ListTile(
              onTap: () => _tap(context, m),
              leading: CircleAvatar(
                backgroundColor:
                    m.uid == null ? Colors.white : AppColors.wakatake,
                foregroundColor: AppColors.ink,
                child: Text(m.name.characters.first),
              ),
              title: Text(m.uid == data.myUid ? '${m.name}（我）' : m.name),
              subtitle: m.uid == null
                  ? const Text('沒有 app')
                  : m.relinkedAt != null
                      ? Text('${_md(m.relinkedAt!)} 從新裝置重新連結')
                      : null,
            ),
          ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              FilledButton.icon(
                onPressed: () => _invite(context),
                icon: const Icon(Icons.qr_code),
                label: const Text('產生邀請碼'),
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => _addNameOnly(context),
                icon: const Icon(Icons.person_add_alt),
                label: const Text('加入沒有 app 的成員'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

String _md(int ms) {
  final d = DateTime.fromMillisecondsSinceEpoch(ms);
  return '${d.month}/${d.day}';
}

Future<bool> _confirmRelink(BuildContext context, String name) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text('你是「$name」嗎？'),
      content: Text(
          '只有在換了手機、刪掉 app，或網頁資料不見時才需要這樣做。'
          '確定後，「$name」的帳會接到這台裝置，原本的裝置就不再代表這個名字。'
          '其他人會在成員頁看到這次重新連結。'),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消')),
        FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('是我，接回來')),
      ],
    ),
  );
  return ok ?? false;
}

Future<String?> _askName(BuildContext context,
    {required String title, String initial = ''}) {
  final ctrl = TextEditingController(text: initial);
  return showDialog<String>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(title),
      content: TextField(
        controller: ctrl,
        autofocus: true,
        maxLength: 20,
        decoration: const InputDecoration(labelText: '名字'),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
        FilledButton(
          onPressed: () {
            final v = ctrl.text.trim();
            Navigator.pop(ctx, v.isEmpty ? null : v);
          },
          child: const Text('確定'),
        ),
      ],
    ),
  );
}
