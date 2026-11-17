import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../widgets/ui.dart';
import 'scenes.dart';

/// The destination's picture: a still backdrop with a few parts that move
/// gently — clouds drift, the sun bobs, a train/bus/boat/plane crosses.
/// Holds still when the system asks for reduced motion.
class TripScene extends StatefulWidget {
  const TripScene({super.key, required this.tripName});
  final String tripName;

  @override
  State<TripScene> createState() => _TripSceneState();
}

class _TripSceneState extends State<TripScene>
    with SingleTickerProviderStateMixin {
  // One slow clock drives everything; each part reads its own phase.
  late final _clock = AnimationController(
      vsync: this, duration: const Duration(seconds: 14));

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (reduceMotion(context)) {
      _clock
        ..stop()
        ..value = 0.45;
    } else if (!_clock.isAnimating) {
      _clock.repeat();
    }
  }

  @override
  void dispose() {
    _clock.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = themeFor(widget.tripName);
    final lane = vehicleLane(theme);
    return Semantics(
      label: '旅程插圖',
      image: true,
      child: AspectRatio(
        aspectRatio: 2,
        child: LayoutBuilder(builder: (context, box) {
          final k = box.maxWidth / 360;
          return AnimatedBuilder(
            animation: _clock,
            builder: (context, _) {
              final t = _clock.value;
              final wave = math.sin(t * 2 * math.pi);
              final vx = -lane.width + (360 + lane.width) * t;
              return Stack(
                clipBehavior: Clip.hardEdge,
                children: [
                  Positioned.fill(child: SvgPicture.string(backdrop(theme))),
                  _at(k, 292, 14 + 3 * wave, 48, 48, sun),
                  _at(k, 30 + 14 * wave, 22, 76, 36, cloud),
                  _at(k, 196 - 10 * wave, 40, 60, 28, cloud),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(18 * k),
                    child: Stack(children: [
                      _at(k, vx, lane.top + (theme == SceneTheme.island ? 2 * wave : 0),
                          lane.width, lane.height, vehicle(theme)),
                    ]),
                  ),
                ],
              );
            },
          );
        }),
      ),
    );
  }

  Widget _at(double k, double x, double y, double w, double h, String svg) =>
      Positioned(
        left: x * k,
        top: y * k,
        width: w * k,
        height: h * k,
        child: SvgPicture.string(svg),
      );
}
