import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sharetrip/main.dart';
import 'package:sharetrip/theme.dart';

void main() {
  testWidgets('app uses the picture-book theme', (tester) async {
    await tester.pumpWidget(const TripShareApp());
    final ctx = tester.element(find.text('我的旅程'));
    final theme = Theme.of(ctx);
    expect(theme.scaffoldBackgroundColor, AppColors.paper);
    expect(theme.brightness, Brightness.light);
    expect(theme.textTheme.bodyMedium?.fontFamily, 'Huninn');
  });
}
