import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'firebase_options.dart';
import 'screens/nickname_screen.dart';
import 'screens/trip_list_screen.dart';
import 'services/account.dart';
import 'services/exchange_rates.dart';
import 'services/trip_repository.dart';
import 'theme.dart';

void main() {
  // Show something at once: signing in can take a while on a slow first
  // connection, and a blank screen reads as a crash.
  runApp(const _Boot());
}

/// Starts Firebase and signs in anonymously, with a visible loading state
/// and a retry when it fails or takes too long.
class _Boot extends StatefulWidget {
  const _Boot();

  @override
  State<_Boot> createState() => _BootState();
}

class _BootState extends State<_Boot> {
  late Future<TripRepository> _ready = _start();

  static const _timeout = Duration(seconds: 20);

  static Future<TripRepository> _start() async {
    if (Firebase.apps.isEmpty) {
      await Firebase.initializeApp(
          options: DefaultFirebaseOptions.currentPlatform);
    }
    final firestore = FirebaseFirestore.instance;
    if (kIsWeb) {
      // Off by default on the web; without it the app shows nothing offline.
      firestore.settings = const Settings(persistenceEnabled: true);
    }
    final auth = FirebaseAuth.instance;
    // On the web the saved sign-in is restored asynchronously, so
    // currentUser is still null here; signing in again would make a new
    // anonymous user who is in none of this person's trips.
    final saved = auth.currentUser ??
        await auth.authStateChanges().first.timeout(_timeout);
    final user =
        saved ?? (await auth.signInAnonymously().timeout(_timeout)).user!;
    return TripRepository(firestore: firestore, uid: user.uid);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<TripRepository>(
      future: _ready,
      builder: (context, snap) {
        if (snap.hasData) {
          return TripShareApp(
            // A new key per session, so nothing from the old identity's
            // screens survives a switch.
            key: ValueKey(snap.data!.uid),
            repo: snap.data!,
            rates: ExchangeRates(),
            account: FirebaseAccount(FirebaseAuth.instance),
            restart: () => setState(() => _ready = _start()),
          );
        }
        return MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: snap.hasError
                    ? Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text('連不上伺服器，請確認網路後再試一次',
                              textAlign: TextAlign.center),
                          const SizedBox(height: 16),
                          FilledButton(
                            onPressed: () =>
                                setState(() => _ready = _start()),
                            child: const Text('重試'),
                          ),
                        ],
                      )
                    : const Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          CircularProgressIndicator(),
                          SizedBox(height: 16),
                          Text('同遊啟動中…'),
                        ],
                      ),
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Everything the screens need, handed down once instead of through
/// globals, so tests can pass fakes.
class AppScope extends InheritedWidget {
  const AppScope({
    super.key,
    required this.repo,
    required this.rates,
    required super.child,
    this.account,
    this.restart = _noop,
  });

  final TripRepository repo;
  final ExchangeRates rates;

  /// Google backup of the identity; null in tests.
  final Account? account;

  /// Starts a new session after the signed-in user changed.
  final VoidCallback restart;

  static void _noop() {}

  static AppScope of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppScope>()!;

  @override
  bool updateShouldNotify(AppScope old) =>
      repo != old.repo || rates != old.rates || account != old.account;
}

class TripShareApp extends StatelessWidget {
  const TripShareApp({
    super.key,
    required this.repo,
    required this.rates,
    this.account,
    this.restart = AppScope._noop,
  });

  final TripRepository repo;
  final ExchangeRates rates;
  final Account? account;
  final VoidCallback restart;

  @override
  Widget build(BuildContext context) {
    return AppScope(
      repo: repo,
      rates: rates,
      account: account,
      restart: restart,
      child: MaterialApp(
        title: '同遊',
        locale: const Locale('zh', 'TW'),
        supportedLocales: const [Locale('zh', 'TW')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        theme: buildAppTheme(),
        home: const _Start(),
      ),
    );
  }
}

/// Asks for a nickname on first launch, then shows the trips.
class _Start extends StatefulWidget {
  const _Start();

  @override
  State<_Start> createState() => _StartState();
}

class _StartState extends State<_Start> {
  Future<String?>? _nickname;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _nickname ??= AppScope.of(context).repo.loadNickname();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<String?>(
      future: _nickname,
      builder: (context, snap) {
        if (snap.connectionState != ConnectionState.done) {
          return const Scaffold(
              body: Center(child: CircularProgressIndicator()));
        }
        final name = snap.data;
        if (name == null || name.isEmpty) {
          return NicknameScreen(
            onDone: (n) => setState(() {
              _nickname = Future.value(n);
            }),
          );
        }
        return TripListScreen(nickname: name);
      },
    );
  }
}
