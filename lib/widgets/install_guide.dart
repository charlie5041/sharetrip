import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../services/install/install_env.dart';
import '../theme.dart';

const _dismissedKey = 'installGuideDismissed';

/// A card on the web app inviting the person to add 同遊 to the home
/// screen. It hides once they open it from there, or close the card.
class InstallBanner extends StatefulWidget {
  const InstallBanner({super.key, this.margin = EdgeInsets.zero});
  final EdgeInsetsGeometry margin;

  @override
  State<InstallBanner> createState() => _InstallBannerState();
}

class _InstallBannerState extends State<InstallBanner> {
  final _env = InstallEnv.current();
  bool _dismissed = true;

  @override
  void initState() {
    super.initState();
    if (_env.showGuide) _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    if (mounted) {
      setState(() => _dismissed = prefs.getBool(_dismissedKey) ?? false);
    }
  }

  Future<void> _dismiss() async {
    setState(() => _dismissed = true);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_dismissedKey, true);
  }

  @override
  Widget build(BuildContext context) {
    if (!_env.showGuide || _dismissed) return const SizedBox.shrink();
    final text = Theme.of(context).textTheme;
    final subtitle = _env.inAppBrowser != null
        ? '先用手機的瀏覽器打開，才能加到主畫面'
        : _env.platform == InstallPlatform.ios
        ? '先加到主畫面，再從主畫面打開開始用'
        : '像 App 一樣從桌面打開，沒網路也能用';
    return Padding(
      padding: widget.margin,
      child: Card(
        color: AppColors.mizu,
        margin: EdgeInsets.zero,
        child: InkWell(
          borderRadius: BorderRadius.circular(AppShape.radius),
          onTap: () => showInstallGuide(context),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 4, 8),
            child: Row(
              children: [
                const Icon(Icons.add_to_home_screen),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('把同遊加到主畫面', style: text.titleMedium),
                      Text(subtitle, style: text.bodySmall),
                    ],
                  ),
                ),
                TextButton(
                  style: TextButton.styleFrom(foregroundColor: AppColors.ink),
                  onPressed: () => showInstallGuide(context),
                  child: const Text('怎麼加'),
                ),
                IconButton(
                  tooltip: '不用了',
                  icon: const Icon(Icons.close),
                  onPressed: _dismiss,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Step-by-step instructions for this phone and browser.
Future<void> showInstallGuide(BuildContext context) {
  final env = InstallEnv.current();
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => _GuideSheet(env: env),
  );
}

class _GuideSheet extends StatelessWidget {
  const _GuideSheet({required this.env});
  final InstallEnv env;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final ios = env.platform == InstallPlatform.ios;
    final browser = ios ? 'Safari' : 'Chrome';
    final link = Uri.base.isScheme('http') || Uri.base.isScheme('https')
        ? '${Uri.base.origin}/'
        : 'https://tripshare-tw.web.app/';

    final List<Widget> body;
    final String title;
    if (env.inAppBrowser != null) {
      title = '請先用 $browser 打開';
      body = [
        Text('你現在是在 ${env.inAppBrowser} 裡面開啟同遊，這裡沒辦法加到主畫面。'),
        const SizedBox(height: 12),
        if (env.openExternal != null)
          FilledButton.icon(
            onPressed: env.openExternal,
            icon: const Icon(Icons.open_in_browser),
            label: Text('用 $browser 打開'),
          )
        else ...[
          const _Step(1, '點右上角的「⋯」或分享按鈕。'),
          _Step(2, '選「用瀏覽器開啟」或「在 $browser 中開啟」。'),
          const _Step(3, '打開後再點這張卡片，照著步驟加到主畫面。'),
        ],
        const SizedBox(height: 8),
        _CopyLink(link),
      ];
    } else if (ios) {
      title = '加到 iPhone 主畫面';
      body = const [
        _Step(1, '點 Safari 下方的「分享」按鈕（方框加向上的箭頭）。沒看到的話，先點右下角的「⋯」。'),
        _Step(2, '往下滑，點「加入主畫面」。'),
        _Step(3, '名稱留著「同遊」，按右上角的「加入」。'),
        _Step(4, '之後都從主畫面的同遊圖示打開。'),
        SizedBox(height: 8),
        _Note(
          'iPhone 會把主畫面的同遊和 Safari 裡的資料分開存。請從主畫面打開後再開始用；'
          '如果已經在 Safari 用過，從主畫面打開後按「用 Google 帳號找回我的旅程」。',
        ),
      ];
    } else if (env.platform == InstallPlatform.android) {
      title = '加到手機主畫面';
      body = [
        if (env.canPrompt && env.prompt != null) ...[
          FilledButton.icon(
            onPressed: () async {
              final installed = await env.prompt!();
              if (installed && context.mounted) Navigator.pop(context);
            },
            icon: const Icon(Icons.download),
            label: const Text('安裝同遊'),
          ),
          const SizedBox(height: 12),
          const Text('按了沒反應的話，照下面的步驟：'),
          const SizedBox(height: 8),
        ],
        const _Step(1, '點 Chrome 右上角的「⋮」。'),
        const _Step(2, '點「加到主畫面」或「安裝應用程式」。'),
        const _Step(3, '按「安裝」，主畫面就會出現同遊的圖示。'),
        const _Step(4, '之後都從主畫面的同遊圖示打開。'),
      ];
    } else {
      title = '用手機打開同遊';
      body = [
        const Text('同遊是給手機用的。用手機的瀏覽器打開這個網址，再照手機上的步驟加到主畫面：'),
        const SizedBox(height: 8),
        _CopyLink(link),
      ];
    }

    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(title, style: text.titleLarge),
            const SizedBox(height: 16),
            ...body,
          ],
        ),
      ),
    );
  }
}

class _Step extends StatelessWidget {
  const _Step(this.n, this.text);
  final int n;
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CircleAvatar(
          radius: 14,
          backgroundColor: AppColors.yamabuki,
          foregroundColor: AppColors.ink,
          child: Text('$n'),
        ),
        const SizedBox(width: 12),
        Expanded(child: Text(text)),
      ],
    ),
  );
}

class _Note extends StatelessWidget {
  const _Note(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: AppColors.coral.withValues(alpha: 0.25),
      borderRadius: BorderRadius.circular(AppShape.smallRadius),
    ),
    child: Text(text, style: Theme.of(context).textTheme.bodySmall),
  );
}

class _CopyLink extends StatefulWidget {
  const _CopyLink(this.link);
  final String link;

  @override
  State<_CopyLink> createState() => _CopyLinkState();
}

class _CopyLinkState extends State<_CopyLink> {
  bool _copied = false;

  @override
  Widget build(BuildContext context) => OutlinedButton.icon(
    onPressed: () async {
      await Clipboard.setData(ClipboardData(text: widget.link));
      if (mounted) setState(() => _copied = true);
    },
    icon: Icon(_copied ? Icons.check : Icons.link),
    label: Text(_copied ? '已複製 ${widget.link}' : '複製網址'),
  );
}
