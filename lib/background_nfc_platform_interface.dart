import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'background_nfc_method_channel.dart';

abstract class BackgroundNfcPlatform extends PlatformInterface {
  /// Constructs a BackgroundNfcPlatform.
  BackgroundNfcPlatform() : super(token: _token);

  static final Object _token = Object();

  static BackgroundNfcPlatform _instance = MethodChannelBackgroundNfc();

  /// The default instance of [BackgroundNfcPlatform] to use.
  ///
  /// Defaults to [MethodChannelBackgroundNfc].
  static BackgroundNfcPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [BackgroundNfcPlatform] when
  /// they register themselves.
  static set instance(BackgroundNfcPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
