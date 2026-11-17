import 'install_env_stub.dart'
    if (dart.library.js_interop) 'install_env_web.dart'
    as impl;

/// Where the web app is running, for the "add to home screen" guide.
enum InstallPlatform { ios, android, other }

/// What the guide needs to know about the browser it runs in.
class InstallEnv {
  const InstallEnv({
    required this.isWeb,
    required this.standalone,
    required this.platform,
    this.inAppBrowser,
    this.canPrompt = false,
    this.prompt,
    this.openExternal,
  });

  /// Running as a web page at all; the native apps never show the guide.
  final bool isWeb;

  /// Already opened from the home screen.
  final bool standalone;
  final InstallPlatform platform;

  /// The app whose built-in browser this is (LINE, Facebook, ...), which
  /// cannot add to the home screen. Null in a real browser.
  final String? inAppBrowser;

  /// Android Chrome offered its own install dialog.
  final bool canPrompt;

  /// Shows that dialog; true when the person installed.
  final Future<bool> Function()? prompt;

  /// Reopens this page in the phone's own browser, where the in-app
  /// browser supports it (LINE does).
  final void Function()? openExternal;

  bool get showGuide => isWeb && !standalone;

  static InstallEnv Function() current = impl.currentInstallEnv;
}
