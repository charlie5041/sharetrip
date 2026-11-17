import 'install_env.dart';

InstallEnv currentInstallEnv() => const InstallEnv(
  isWeb: false,
  standalone: true,
  platform: InstallPlatform.other,
);
