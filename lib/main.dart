import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'firebase_options.dart';
import 'screens/nickname_screen.dart';
import 'screens/trip_list_screen.dart';
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
    final auth = FirebaseAuth.instance;
    final user = auth.currentUser ??
        (await auth.signInAnonymously().timeout(_timeout)).user!;
    return TripRepository(firestore: FirebaseFirestore.instance, uid: user.uid);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<TripRepository>(
      future: _ready,
      builder: (context, snap) {
        if (snap.hasData) {
          return TripShareApp(repo: snap.data!, rates: ExchangeRates());
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
  });

  final TripRepository repo;
  final ExchangeRates rates;

  static AppScope of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppScope>()!;

  @override
  bool updateShouldNotify(AppScope old) =>
      repo != old.repo || rates != old.rates;
}

class TripShareApp extends StatelessWidget {
  const TripShareApp({super.key, required this.repo, required this.rates});

  final TripRepository repo;
  final ExchangeRates rates;

  @override
  Widget build(BuildContext context) {
    return AppScope(
      repo: repo,
      rates: rates,
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
