import 'package:flutter/material.dart';

import '../services/reporter.dart';

/// Lets a friend tell Charlie what went wrong, from inside the app.
Future<void> showFeedbackDialog(BuildContext context) async {
  final text = await showDialog<String>(
      context: context, builder: (_) => const _FeedbackDialog());
  if (text == null || text.trim().isEmpty || !context.mounted) return;
  final reporter = Reporter.instance;
  if (reporter != null) await reporter.sendFeedback(text);
  if (!context.mounted) return;
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(reporter == null ? '現在送不出去，請稍後再試' : '謝謝！已經送出')));
}

class _FeedbackDialog extends StatefulWidget {
  const _FeedbackDialog();

  @override
  State<_FeedbackDialog> createState() => _FeedbackDialogState();
}

class _FeedbackDialogState extends State<_FeedbackDialog> {
  // Owned here: the dialog still draws the field while it animates away.
  final _ctrl = TextEditingController();

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: const Text('回報問題'),
        content: TextField(
          controller: _ctrl,
          autofocus: true,
          minLines: 3,
          maxLines: 6,
          maxLength: 1000,
          decoration: const InputDecoration(
            hintText: '哪裡怪怪的？在哪個畫面、做了什麼？',
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('取消')),
          FilledButton(
            onPressed: () => Navigator.pop(context, _ctrl.text),
            child: const Text('送出'),
          ),
        ],
      );
}
