import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../main.dart';
import '../models/trip.dart';
import '../services/trip_repository.dart';
import '../theme.dart';
import '../widgets/ui.dart';
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

  bool get _isOwner => data.trip.ownerUid == data.myUid;

  Future<void> _add(BuildContext context) async {
    final name = await _askName(context, title: '加入成員');
    if (name == null || !context.mounted) return;
    writeInBackground(
        context, AppScope.of(context).repo.addMember(data.trip.id, name));
  }

  Future<void> _tap(BuildContext context, Member m) async {
    final repo = AppScope.of(context).repo;
    final isMe = m.uid != null && m.uid == data.myUid;
    final canRename = _isOwner || isMe;
    final canRemove = _isOwner && !isMe;
    final canUnbind = _isOwner && !isMe && m.uid != null;
    if (!canRename && !canRemove) return;
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (canRename)
              ListTile(
                leading: const Icon(Icons.edit),
                title: const Text('改名字'),
                onTap: () => Navigator.pop(ctx, 'rename'),
              ),
            if (canUnbind)
              ListTile(
                leading: const Icon(Icons.phonelink_erase),
                title: const Text('解除綁定'),
                subtitle: const Text('他換手機或資料不見時用：名字和帳都留著，'
                    '他再用邀請碼加入、選回這個名字就好'),
                onTap: () => Navigator.pop(ctx, 'unbind'),
              ),
            if (canRemove)
              ListTile(
                leading: const Icon(Icons.person_remove_outlined),
                title: const Text('移除成員'),
                onTap: () => Navigator.pop(ctx, 'remove'),
              ),
          ],
        ),
      ),
    );
    if (!context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      switch (action) {
        case 'rename':
          final name = await _askName(context, title: '改名字', initial: m.name);
          if (name != null) await repo.renameMember(data.trip.id, m.id, name);
        case 'unbind':
          if (await confirmAction(context,
              title: '解除「${m.name}」的綁定？',
              body: '他目前的手機會看不到這趟旅程。名字和帳都會留著，'
                  '他用新的邀請碼加入、選「${m.name}」就能接回來。',
              confirm: '解除綁定')) {
            await repo.unbindMember(data.trip.id, m);
          }
        case 'remove':
          if (await confirmAction(context,
              title: '移除「${m.name}」？',
              body: m.uid == null
                  ? '名單上不會再有這個名字。'
                  : '他會看不到這趟旅程，名單上也不會再有這個名字。',
              confirm: '移除')) {
            await repo.removeMember(data.trip.id, m);
          }
      }
    } on StateError catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _dissolve(BuildContext context) async {
    final repo = AppScope.of(context).repo;
    final nav = Navigator.of(context);
    final ok = await confirmAction(context,
        title: '解散「${data.trip.name}」？',
        body: '所有人的行程、記帳、清單都會一起刪除，無法復原。',
        confirm: '解散');
    if (!ok) return;
    nav.popUntil((r) => r.isFirst);
    await repo.dissolveTrip(data.trip.id);
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
                  ? const Text('還沒加入')
                  : m.uid == data.trip.ownerUid
                      ? const Text('群主')
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
              const SizedBox(height: 8),
              Text(
                _isOwner
                    ? '朋友加入時，要從「還沒加入」的名字裡選自己。名單上沒有的人進不來，請先加進名單。'
                    : '名單由群主管理。要加人或刪人，請找群主。',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              if (_isOwner) ...[
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: () => _add(context),
                  icon: const Icon(Icons.person_add_alt),
                  label: const Text('加入成員'),
                ),
                const SizedBox(height: 24),
                TextButton(
                  onPressed: () => _dissolve(context),
                  style: TextButton.styleFrom(
                      foregroundColor: Theme.of(context).colorScheme.error),
                  child: const Text('解散旅程'),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
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
