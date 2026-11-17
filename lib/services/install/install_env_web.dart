import 'dart:js_interop';

import 'package:web/web.dart' as web;

import 'install_env.dart';

@JS('tripshareInstall')
external _InstallPrompt? get _installPrompt;

extension type _InstallPrompt(JSObject _) implements JSObject {
  external JSPromise<JSAny?> prompt();
  external JSPromise<_Choice> get userChoice;
}

extension type _Choice(JSObject _) implements JSObject {
  external String get outcome;
}

@JS('navigator.standalone')
external bool? get _iosStandalone;

InstallEnv currentInstallEnv() {
  final ua = web.window.navigator.userAgent;
  final platform =
      RegExp(r'iPhone|iPad|iPod').hasMatch(ua) ||
          // iPadOS asks for the desktop site and says it is a Mac.
          (ua.contains('Macintosh') && web.window.navigator.maxTouchPoints > 1)
      ? InstallPlatform.ios
      : ua.contains('Android')
      ? InstallPlatform.android
      : InstallPlatform.other;
  final standalone =
      web.window.matchMedia('(display-mode: standalone)').matches ||
      (_iosStandalone ?? false);
  final inApp = ua.contains(' Line/')
      ? 'LINE'
      : RegExp(r'FBAN|FBAV|FB_IAB').hasMatch(ua)
      ? 'Facebook'
      : ua.contains('Instagram')
      ? 'Instagram'
      : ua.contains('MicroMessenger')
      ? '微信'
      : null;
  final deferred = _installPrompt;
  return InstallEnv(
    isWeb: true,
    standalone: standalone,
    platform: platform,
    inAppBrowser: inApp,
    canPrompt: deferred != null,
    prompt: deferred == null
        ? null
        : () async {
            await deferred.prompt().toDart;
            final choice = await deferred.userChoice.toDart;
            return choice.outcome == 'accepted';
          },
    openExternal: inApp == 'LINE'
        ? () {
            // LINE opens links carrying this flag in the phone's browser.
            final url = Uri.parse(web.window.location.href);
            web.window.location.href = url
                .replace(
                  queryParameters: {
                    ...url.queryParameters,
                    'openExternalBrowser': '1',
                  },
                )
                .toString();
          }
        : null,
  );
}
