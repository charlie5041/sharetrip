import 'package:flutter/material.dart';

/// The picture-book look: washi-white paper, thick ink outlines, big
/// rounded corners and flat fills — no gradients, no shadows, and no dark
/// mode (the style only works on a light background).
abstract final class AppColors {
  static const paper = Color(0xFFFFFCF6); // 和紙白
  static const ink = Color(0xFF3E3A39); // 墨灰
  static const coral = Color(0xFFF2A497); // 珊瑚粉
  static const yamabuki = Color(0xFFF6D27A); // 山吹黃
  static const mizu = Color(0xFFA8D3E6); // 水色
  static const wakatake = Color(0xFFB9DAA5); // 若竹綠
}

abstract final class AppShape {
  static const borderWidth = 3.0;
  static const radius = 20.0;
  static const smallRadius = 14.0;

  static const outline = BorderSide(color: AppColors.ink, width: borderWidth);

  static RoundedRectangleBorder rounded([double r = radius]) =>
      RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(r)),
        side: outline,
      );
}

ThemeData buildAppTheme() {
  const scheme = ColorScheme(
    brightness: Brightness.light,
    primary: AppColors.coral,
    onPrimary: AppColors.ink,
    secondary: AppColors.mizu,
    onSecondary: AppColors.ink,
    tertiary: AppColors.wakatake,
    onTertiary: AppColors.ink,
    error: Color(0xFFD9534F),
    onError: AppColors.paper,
    surface: AppColors.paper,
    onSurface: AppColors.ink,
    outline: AppColors.ink,
  );

  final base = ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    fontFamily: 'Huninn',
    scaffoldBackgroundColor: AppColors.paper,
  );

  return base.copyWith(
    textTheme: base.textTheme.apply(
      bodyColor: AppColors.ink,
      displayColor: AppColors.ink,
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.paper,
      foregroundColor: AppColors.ink,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: true,
      shape: Border(bottom: AppShape.outline),
    ),
    cardTheme: CardThemeData(
      color: Colors.white,
      elevation: 0,
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      shape: AppShape.rounded(),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.yamabuki,
        foregroundColor: AppColors.ink,
        shape: AppShape.rounded(AppShape.smallRadius),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: AppColors.ink,
        side: AppShape.outline,
        shape: AppShape.rounded(AppShape.smallRadius),
      ),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: AppColors.coral,
      foregroundColor: AppColors.ink,
      elevation: 0,
      focusElevation: 0,
      hoverElevation: 0,
      highlightElevation: 0,
      shape: AppShape.rounded(),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: AppColors.mizu,
      labelStyle: const TextStyle(color: AppColors.ink, fontFamily: 'Huninn'),
      side: const BorderSide(color: AppColors.ink, width: 2),
      shape: const StadiumBorder(),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(AppShape.smallRadius),
        borderSide: AppShape.outline,
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(AppShape.smallRadius),
        borderSide: AppShape.outline,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(AppShape.smallRadius),
        borderSide: const BorderSide(color: AppColors.coral, width: 3),
      ),
    ),
    dividerTheme: const DividerThemeData(color: AppColors.ink, thickness: 2),
    tabBarTheme: const TabBarThemeData(
      labelColor: AppColors.ink,
      unselectedLabelColor: AppColors.ink,
      indicatorColor: AppColors.coral,
      indicatorSize: TabBarIndicatorSize.label,
      dividerColor: AppColors.ink,
    ),
    navigationBarTheme: const NavigationBarThemeData(
      backgroundColor: AppColors.paper,
      indicatorColor: AppColors.mizu,
      elevation: 0,
    ),
  );
}
