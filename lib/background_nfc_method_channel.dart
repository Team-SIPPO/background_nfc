import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'background_nfc_platform_interface.dart';

/// An implementation of [BackgroundNfcPlatform] that uses method channels.
class MethodChannelBackgroundNfc extends BackgroundNfcPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('background_nfc');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

}
