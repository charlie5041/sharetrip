import 'package:flutter/material.dart';

import '../theme.dart';

class SectionHeading extends StatelessWidget {
  const SectionHeading(this.text, {super.key, this.action});
  final String text;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 12, 4),
        child: Row(
          children: [
            Expanded(
                child: Text(text,
                    style: Theme.of(context).textTheme.titleMedium)),
            ?action,
          ],
        ),
      );
}

/// A small flat label with an ink outline, e.g. a transport tag or 必去.
class Tag extends StatelessWidget {
  const Tag(this.text, {super.key, required this.color});
  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: color,
          border: Border.all(color: AppColors.ink, width: 2),
          borderRadius: BorderRadius.circular(999),
        ),
        child: Text(text, style: const TextStyle(fontSize: 12)),
      );
}

/// Asks before deleting; true when confirmed.
Future<bool> confirmDelete(BuildContext context, String what) async =>
    await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('刪除$what？'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('刪除')),
        ],
      ),
    ) ??
    false;

/// Shared text field with a label; returns trimmed text or null.
String? trimmedOrNull(TextEditingController c) =>
    c.text.trim().isEmpty ? null : c.text.trim();

/// Whether the platform asked for reduced motion.
/// Starts a Firestore write without waiting for the server. Offline, the
/// write is applied to the local cache at once and sent when the device is
/// back online, but its future only completes then; a screen that awaited
/// it would sit there with its button disabled. A rejected write (rules,
/// a deleted trip) is shown as a snack bar.
void writeInBackground(BuildContext context, Future<void> write) {
  final messenger = ScaffoldMessenger.maybeOf(context);
  write.catchError((Object e) {
    messenger?.showSnackBar(SnackBar(content: Text('沒有存成功：$e')));
  });
}

bool reduceMotion(BuildContext context) =>
    MediaQuery.maybeDisableAnimationsOf(context) ?? false;
