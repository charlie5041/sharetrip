import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/foundation.dart';
import 'package:package_info_plus/package_info_plus.dart';

/// Sends crashes and the feedback people type to Firestore's
/// `errorReports`, where only the Firebase Console can read them.
/// Crashlytics would leave out the web app, which every iPhone uses.
class Reporter {
  Reporter({required this.firestore, required this.uid, required this.version});

  final FirebaseFirestore firestore;
  final String uid;

  /// "0.1.0+8 web-iOS" style, so a report says which build and phone.
  final String version;

  /// Set once the app has signed in; errors before that are kept and sent
  /// when it is.
  static Reporter? instance;

  static final List<(Object, StackTrace?)> _early = [];
  static final Set<String> _seen = {};
  static int _sent = 0;

  /// A runaway error loop must not write thousands of documents.
  static const maxErrorsPerSession = 20;
  static const maxMessage = 2000;
  static const maxStack = 4000;

  static Future<void> attach({
    required FirebaseFirestore firestore,
    required String uid,
  }) async {
    var version = '?';
    try {
      final info = await PackageInfo.fromPlatform();
      version = '${info.version}+${info.buildNumber}';
    } catch (_) {}
    instance = Reporter(
      firestore: firestore,
      uid: uid,
      version: '$version ${platformName()}',
    );
    final early = List.of(_early);
    _early.clear();
    for (final (e, s) in early) {
      recordError(e, s);
    }
  }

  static String platformName() =>
      '${kIsWeb ? 'web-' : ''}${defaultTargetPlatform.name}';

  /// Records an uncaught error once per message, up to the session limit.
  static void recordError(Object error, StackTrace? stack) {
    final reporter = instance;
    if (reporter == null) {
      if (_early.length < maxErrorsPerSession) _early.add((error, stack));
      return;
    }
    final message = _cut('$error', maxMessage);
    if (!_seen.add(message) || _sent >= maxErrorsPerSession) return;
    _sent++;
    reporter._write(
      kind: 'error',
      message: message,
      stack: _cut('$stack', maxStack),
    );
  }

  /// Sends what someone typed into 回報問題. Completes once it is queued
  /// locally, so it works offline and goes out when the phone reconnects.
  Future<void> sendFeedback(String text) =>
      _write(kind: 'feedback', message: _cut(text.trim(), maxMessage));

  Future<void> _write({
    required String kind,
    required String message,
    String? stack,
  }) {
    final doc = firestore.collection('errorReports').doc();
    final write = doc.set({
      'uid': uid,
      'kind': kind,
      'message': message,
      if (stack != null && stack != 'null') 'stack': stack,
      'version': version,
      'createdAt': FieldValue.serverTimestamp(),
    });
    // Offline the write only completes once the server has it; failing to
    // report must never become an error of its own.
    write.catchError((Object _) {});
    return Future.value();
  }

  static String _cut(String s, int max) =>
      s.length <= max ? s : '${s.substring(0, max - 1)}…';

  @visibleForTesting
  static void reset() {
    instance = null;
    _early.clear();
    _seen.clear();
    _sent = 0;
  }
}
