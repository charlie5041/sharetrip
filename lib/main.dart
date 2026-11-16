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

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  final auth = FirebaseAuth.instance;
  final user = auth.currentUser ?? (await auth.signInAnonymously()).user!;
  final repo =
      TripRepository(firestore: FirebaseFirestore.instance, uid: user.uid);
  runApp(TripShareApp(repo: repo, rates: ExchangeRates()));
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
