import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart' show kIsWeb;

/// The Google account already tied to another 同遊 identity, offered so the
/// person can switch to it instead.
class AccountInUse implements Exception {
  const AccountInUse(this.credential, this.email);
  final AuthCredential? credential;
  final String? email;
}

/// Keeps a device's anonymous identity recoverable by tying it to a Google
/// account. The anonymous uid is kept when linking, so every trip and
/// expense stays put; on a new device, signing in with the same Google
/// account brings that uid back.
abstract class Account {
  /// The linked Google address, or null while the identity is anonymous only.
  String? get linkedEmail;

  /// Ties this identity to a Google account. Throws [AccountInUse] when that
  /// account already belongs to another identity.
  Future<void> linkGoogle();

  /// Signs in as the identity tied to a Google account, leaving the current
  /// anonymous one behind. The app must restart its session afterwards.
  Future<void> signInWithGoogle({AuthCredential? credential});
}

class FirebaseAccount implements Account {
  FirebaseAccount(this._auth);
  final FirebaseAuth _auth;

  GoogleAuthProvider get _provider =>
      GoogleAuthProvider()..setCustomParameters({'prompt': 'select_account'});

  @override
  String? get linkedEmail {
    final user = _auth.currentUser;
    if (user == null) return null;
    for (final p in user.providerData) {
      if (p.providerId == 'google.com') return p.email ?? user.email ?? '';
    }
    return null;
  }

  @override
  Future<void> linkGoogle() async {
    final user = _auth.currentUser!;
    try {
      if (kIsWeb) {
        await user.linkWithPopup(_provider);
      } else {
        await user.linkWithProvider(_provider);
      }
    } on FirebaseAuthException catch (e) {
      if (e.code == 'credential-already-in-use' ||
          e.code == 'email-already-in-use') {
        throw AccountInUse(e.credential, e.email);
      }
      rethrow;
    }
  }

  @override
  Future<void> signInWithGoogle({AuthCredential? credential}) async {
    if (credential != null) {
      await _auth.signInWithCredential(credential);
    } else if (kIsWeb) {
      await _auth.signInWithPopup(_provider);
    } else {
      await _auth.signInWithProvider(_provider);
    }
  }
}

/// Words for a failed Google step, for a snack bar.
String accountErrorText(Object e) {
  if (e is FirebaseAuthException) {
    switch (e.code) {
      case 'popup-closed-by-user':
      case 'cancelled-popup-request':
      case 'web-context-canceled':
      case 'canceled':
        return '已取消';
      case 'popup-blocked':
        return '瀏覽器擋下了登入視窗，請允許彈出視窗後再試';
      case 'operation-not-allowed':
        return 'Google 登入還沒在 Firebase 開啟';
      case 'network-request-failed':
        return '網路不通，請稍後再試';
    }
    return 'Google 登入失敗（${e.code}${e.message == null ? '' : '：${e.message}'}）';
  }
  return 'Google 登入失敗（$e）';
}
