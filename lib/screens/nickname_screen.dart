import 'package:flutter/material.dart';

import '../main.dart';

class NicknameScreen extends StatefulWidget {
  const NicknameScreen({super.key, required this.onDone});

  final ValueChanged<String> onDone;

  @override
  State<NicknameScreen> createState() => _NicknameScreenState();
}

class _NicknameScreenState extends State<NicknameScreen> {
  final _ctrl = TextEditingController();
  bool _saving = false;

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final name = _ctrl.text.trim();
    if (name.isEmpty) return;
    setState(() => _saving = true);
    await AppScope.of(context).repo.setNickname(name);
    widget.onDone(name);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('歡迎來到同遊',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 8),
              const Text('朋友會用這個名字認出你', textAlign: TextAlign.center),
              const SizedBox(height: 24),
              TextField(
                controller: _ctrl,
                maxLength: 20,
                autofocus: true,
                decoration: const InputDecoration(labelText: '你的暱稱'),
                onSubmitted: (_) => _save(),
              ),
              const SizedBox(height: 12),
              FilledButton(
                onPressed: _saving ? null : _save,
                child: const Text('開始'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
