import 'package:flutter/material.dart';

import '../main.dart';
import '../services/account.dart';

/// Shows whether this identity is backed up to Google, and lets the person
/// back it up or switch to an identity they backed up on another device.
Future<void> showAccountSheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    builder: (_) => const _AccountSheet(),
  );
}

/// Signs in with Google from the first-launch screen, for someone who used
/// 同遊 before on another device.
Future<void> restoreWithGoogle(BuildContext context) async {
  final scope = AppScope.of(context);
  try {
    await scope.account!.signInWithGoogle();
    scope.restart();
  } catch (e) {
    if (context.mounted) await _showError(context, e);
  }
}

/// A dialog rather than a snack bar: under the bottom sheet a snack bar is
/// hidden, and a failed sign-in then looks like a button that does nothing.
Future<void> _showError(BuildContext context, Object e) => showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Google 登入沒有成功'),
        content: Text(accountErrorText(e)),
        actions: [
          FilledButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('好')),
        ],
      ),
    );

class _AccountSheet extends StatefulWidget {
  const _AccountSheet();

  @override
  State<_AccountSheet> createState() => _AccountSheetState();
}

class _AccountSheetState extends State<_AccountSheet> {
  bool _busy = false;

  Future<void> _link() async {
    final scope = AppScope.of(context);
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _busy = true);
    try {
      await scope.account!.linkGoogle();
      messenger.showSnackBar(
        const SnackBar(content: Text('已綁定，換手機時用 Google 登入就能找回')),
      );
    } on AccountInUse catch (e) {
      if (mounted) await _offerSwitch(e);
    } catch (e) {
      if (mounted) await _showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _offerSwitch(AccountInUse e) async {
    final scope = AppScope.of(context);
    final who = e.email ?? '這個 Google 帳號';
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('這個帳號已經綁定過'),
        content: Text(
          '$who 已經綁在另一個同遊身分上。要改用那個身分嗎？'
          '這台裝置目前的身分（還沒綁定）會被放掉。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('改用那個身分'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await scope.account!.signInWithGoogle(credential: e.credential);
    scope.restart();
  }

  Future<void> _switch() async {
    final scope = AppScope.of(context);
    setState(() => _busy = true);
    try {
      await scope.account!.signInWithGoogle();
      scope.restart();
    } catch (e) {
      if (mounted) {
        setState(() => _busy = false);
        await _showError(context, e);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final email = AppScope.of(context).account?.linkedEmail;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('備份我的身分', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            if (email != null)
              Text('已綁定 Google：$email\n換手機或資料不見時，用這個 Google 帳號登入就能找回所有旅程。')
            else ...[
              const Text(
                '目前沒有綁定。換手機、刪掉 app 或網頁資料被清掉時，'
                '你會看不到原本的旅程。綁定 Google 後就能找回。',
              ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: _busy ? null : _link,
                icon: const Icon(Icons.shield_outlined),
                label: const Text('綁定 Google 帳號'),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: _busy ? null : _switch,
                child: const Text('我在別的裝置綁定過，改用那個身分'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
