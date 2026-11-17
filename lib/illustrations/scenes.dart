/// Hand-drawn SVG scenes for the trip header, in the picture-book style:
/// flat palette fills, 3px ink outlines, rounded joins, no gradients.
///
/// Each scene is split into layers so the moving parts can animate on
/// their own. Every layer shares the same 360 × 180 coordinate space,
/// except the sprites ([sun], [cloud] and the vehicles), which are drawn
/// at their own small size and positioned by the widget.
library;

const _ink = '#3E3A39';
const _paper = '#FFFCF6';
const _coral = '#F2A497';
const _yamabuki = '#F6D27A';
const _mizu = '#A8D3E6';
const _wakatake = '#B9DAA5';
const _sky = '#E3F1F7';

String _svg(String w, String h, String body) =>
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" '
    'stroke="$_ink" stroke-width="3" stroke-linejoin="round" '
    'stroke-linecap="round">$body</svg>';

/// Where a destination is, as far as the drawing goes.
enum SceneTheme { japan, korea, island, anywhere }

/// Guesses the scene from the trip's name. Anything unrecognised gets the
/// generic countryside with a plane.
SceneTheme themeFor(String tripName) {
  bool has(List<String> words) => words.any(tripName.contains);
  if (has(['日本', '東京', '大阪', '京都', '奈良', '北海道', '札幌', '福岡',
      '名古屋', '富士', '箱根', '九州', '四國', '關西', '關東', '輕井澤',
      '金澤', '廣島', '神戶', '仙台', '東北'])) {
    return SceneTheme.japan;
  }
  if (has(['韓國', '首爾', '釜山', '濟州', '大邱', '仁川'])) {
    return SceneTheme.korea;
  }
  if (has(['沖繩', '峇里', '巴里', '關島', '帛琉', '普吉', '蘇美', '宿霧',
      '長灘', '馬爾地夫', '夏威夷', '墾丁', '澎湖', '綠島', '蘭嶼', '小琉球',
      '海島', '海邊', '沙巴', '蘭卡威', '芽莊', '峴港'])) {
    return SceneTheme.island;
  }
  return SceneTheme.anywhere;
}

/// The still part of each scene: sky, land and landmark.
String backdrop(SceneTheme t) => switch (t) {
      SceneTheme.japan => _japan,
      SceneTheme.korea => _korea,
      SceneTheme.island => _island,
      SceneTheme.anywhere => _anywhere,
    };

/// What drives across: shinkansen, bus, boat or plane.
String vehicle(SceneTheme t) => switch (t) {
      SceneTheme.japan => _shinkansen,
      SceneTheme.korea => _bus,
      SceneTheme.island => _boat,
      SceneTheme.anywhere => _plane,
    };

/// The vehicle's lane: its top edge in scene coordinates, and its size.
({double top, double width, double height}) vehicleLane(SceneTheme t) =>
    switch (t) {
      SceneTheme.japan => (top: 129, width: 120, height: 30),
      SceneTheme.korea => (top: 116, width: 64, height: 38),
      SceneTheme.island => (top: 94, width: 60, height: 44),
      SceneTheme.anywhere => (top: 30, width: 64, height: 30),
    };

final sun = _svg('48', '48',
    '<circle cx="24" cy="24" r="18" fill="$_yamabuki"/>'
    '<path d="M17 25 q3 3 6 0 M25 25 q3 3 6 0" fill="none" stroke-width="2.5"/>');

final cloud = _svg('76', '36',
    '<path d="M14 30 a10 10 0 0 1 2-19 a14 14 0 0 1 26-4 a12 12 0 0 1 22 8 '
    'a8 8 0 0 1-2 15 z" fill="white"/>');

// ---- Backdrops -------------------------------------------------------------

final _japan = _svg('360', '180', '''
<rect x="1.5" y="1.5" width="357" height="177" rx="18" fill="$_sky"/>
<path d="M60 150 L150 46 Q160 36 170 46 L262 150 Z" fill="$_mizu"/>
<path d="M131 68 L150 46 Q160 36 170 46 L189 68 L178 76 L168 66 L160 78 L150 66 L141 76 Z" fill="white"/>
<path d="M1.5 132 Q60 112 120 130 T240 128 T358.5 126 L358.5 160 L1.5 160 Z" fill="$_wakatake"/>
<g fill="$_coral">
  <rect x="276" y="96" width="8" height="46"/>
  <rect x="312" y="96" width="8" height="46"/>
  <path d="M266 88 Q298 80 330 88 L330 96 Q298 90 266 96 Z"/>
  <rect x="272" y="102" width="52" height="7"/>
</g>
<path d="M1.5 158 L358.5 158" fill="none"/>
<path d="M1.5 160 L358.5 160 L358.5 160 Q358.5 178.5 340 178.5 L20 178.5 Q1.5 178.5 1.5 160 Z" fill="$_paper"/>
<path d="M20 168 L60 168 M90 168 L130 168 M160 168 L200 168 M230 168 L270 168 M300 168 L340 168" stroke-width="2.5"/>
''');

final _korea = _svg('360', '180', '''
<rect x="1.5" y="1.5" width="357" height="177" rx="18" fill="$_sky"/>
<path d="M120 150 Q180 70 250 150 Z" fill="$_wakatake"/>
<g>
  <rect x="181" y="44" width="10" height="56" fill="white"/>
  <path d="M170 44 L202 44 L198 30 L174 30 Z" fill="$_coral"/>
  <rect x="183" y="16" width="6" height="14" fill="white"/>
  <path d="M186 16 L186 8" fill="none"/>
  <rect x="176" y="98" width="20" height="14" fill="$_yamabuki"/>
</g>
<path d="M1.5 140 Q50 120 100 138 Q112 144 118 150 L1.5 150 Z" fill="$_wakatake"/>
<path d="M250 150 Q262 140 280 134 Q320 120 358.5 132 L358.5 150 Z" fill="$_wakatake"/>
<g fill="white">
  <rect x="24" y="118" width="40" height="26"/>
  <path d="M16 120 Q44 100 72 120 Z" fill="$_coral"/>
  <rect x="290" y="114" width="44" height="30"/>
  <path d="M282 116 Q312 94 342 116 Z" fill="$_mizu"/>
</g>
<path d="M1.5 150 L358.5 150 L358.5 160 Q358.5 178.5 340 178.5 L20 178.5 Q1.5 178.5 1.5 160 Z" fill="$_paper"/>
''');

final _island = _svg('360', '180', '''
<rect x="1.5" y="1.5" width="357" height="177" rx="18" fill="$_sky"/>
<path d="M1.5 100 Q45 92 90 100 T180 100 T270 100 T358.5 100 L358.5 150 L1.5 150 Z" fill="$_mizu"/>
<path d="M30 122 q10 -6 20 0 M110 132 q10 -6 20 0 M230 118 q10 -6 20 0" fill="none" stroke-width="2.5"/>
<path d="M1.5 150 Q120 128 240 146 Q300 154 358.5 140 L358.5 160 Q358.5 178.5 340 178.5 L20 178.5 Q1.5 178.5 1.5 160 Z" fill="$_yamabuki"/>
<g transform="translate(-60 0)">
<path d="M300 150 Q296 110 308 70" fill="none" stroke-width="7"/>
<path d="M300 150 Q296 110 308 70" fill="none" stroke="#C9A36A" stroke-width="3"/>
<g fill="$_wakatake">
  <path d="M308 70 Q280 56 262 76 Q286 66 308 70 Z"/>
  <path d="M308 70 Q336 54 352 74 Q330 64 308 70 Z"/>
  <path d="M308 70 Q296 42 272 44 Q296 52 308 70 Z"/>
  <path d="M308 70 Q322 40 344 40 Q320 50 308 70 Z"/>
</g>
<circle cx="300" cy="74" r="4" fill="#C9A36A"/>
<circle cx="312" cy="76" r="4" fill="#C9A36A"/>
</g>
<path d="M60 160 q4 -10 8 0 q4 -10 8 0" fill="$_coral" stroke-width="2.5"/>
''');

final _anywhere = _svg('360', '180', '''
<rect x="1.5" y="1.5" width="357" height="177" rx="18" fill="$_sky"/>
<path d="M1.5 126 Q90 96 180 122 T358.5 112 L358.5 160 Q358.5 178.5 340 178.5 L20 178.5 Q1.5 178.5 1.5 160 Z" fill="$_wakatake"/>
<path d="M1.5 150 Q120 132 240 150 T358.5 146 L358.5 160 Q358.5 178.5 340 178.5 L20 178.5 Q1.5 178.5 1.5 160 Z" fill="#A3CE8C"/>
<rect x="232" y="110" width="44" height="34" fill="white"/>
<path d="M224 112 L254 90 L284 112 Z" fill="$_coral"/>
<rect x="248" y="124" width="12" height="20" fill="$_yamabuki"/>
<path d="M84 140 L84 116" fill="none"/>
<circle cx="84" cy="108" r="14" fill="#A3CE8C"/>
<path d="M120 170 Q180 150 240 170" fill="none" stroke-dasharray="4 8" stroke-width="2.5"/>
''');

// ---- Vehicles (drawn facing right) -----------------------------------------

final _shinkansen = _svg('120', '30', '''
<path d="M4 26 L4 8 Q4 4 8 4 L82 4 Q110 4 116 22 Q117 26 112 26 Z" fill="white"/>
<path d="M4 18 L114 18" fill="none" stroke="$_mizu" stroke-width="5"/>
<path d="M4 18 L114 18 M4 15 L4 21" fill="none" stroke-width="0"/>
<g fill="$_mizu" stroke-width="2.5">
  <rect x="14" y="8" width="12" height="6" rx="2"/>
  <rect x="34" y="8" width="12" height="6" rx="2"/>
  <rect x="54" y="8" width="12" height="6" rx="2"/>
  <path d="M84 8 L96 8 Q102 10 104 14 L84 14 Z"/>
</g>
''');

final _bus = _svg('64', '38', '''
<rect x="3" y="3" width="58" height="26" rx="8" fill="$_yamabuki"/>
<g fill="white" stroke-width="2.5">
  <rect x="9" y="8" width="10" height="9" rx="2"/>
  <rect x="23" y="8" width="10" height="9" rx="2"/>
  <rect x="37" y="8" width="10" height="9" rx="2"/>
  <rect x="50" y="8" width="7" height="12" rx="2"/>
</g>
<circle cx="16" cy="30" r="5" fill="$_ink"/>
<circle cx="48" cy="30" r="5" fill="$_ink"/>
''');

final _boat = _svg('60', '44', '''
<path d="M30 4 L30 30" fill="none"/>
<path d="M32 6 L52 28 L32 28 Z" fill="white"/>
<path d="M28 10 L12 28 L28 28 Z" fill="$_coral"/>
<path d="M4 30 L56 30 L48 40 L12 40 Z" fill="$_yamabuki"/>
''');

final _plane = _svg('64', '30', '''
<path d="M6 16 Q6 10 14 10 L50 10 Q60 12 60 16 Q60 20 50 22 L14 22 Q6 22 6 16 Z" fill="white"/>
<path d="M26 12 L18 2 L26 2 L36 12 Z" fill="$_coral"/>
<path d="M26 20 L18 28 L26 28 L36 20 Z" fill="$_coral"/>
<path d="M8 12 L4 4 L10 4 L14 10 Z" fill="$_mizu"/>
<g fill="$_mizu" stroke-width="2"><circle cx="40" cy="15" r="2"/><circle cx="47" cy="15" r="2"/></g>
''');
