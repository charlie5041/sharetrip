import 'package:url_launcher/url_launcher.dart';

/// A link that opens [place] in the phone's maps app (Google Maps when
/// installed, the browser otherwise), unless a specific [mapUrl] was saved.
Uri mapUri({String? place, String? mapUrl}) {
  if (mapUrl != null && mapUrl.trim().isNotEmpty) return Uri.parse(mapUrl.trim());
  return Uri.https('www.google.com', '/maps/search/', {
    'api': '1',
    'query': place ?? '',
  });
}

Future<void> openMap({String? place, String? mapUrl}) =>
    launchUrl(mapUri(place: place, mapUrl: mapUrl),
        mode: LaunchMode.externalApplication);
