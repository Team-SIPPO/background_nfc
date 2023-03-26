import 'package:flutter_test/flutter_test.dart';
import 'package:background_nfc/background_nfc.dart';
import 'package:background_nfc/background_nfc_platform_interface.dart';
import 'package:background_nfc/background_nfc_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockBackgroundNfcPlatform
    with MockPlatformInterfaceMixin
    implements BackgroundNfcPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final BackgroundNfcPlatform initialPlatform = BackgroundNfcPlatform.instance;

  test('$MethodChannelBackgroundNfc is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelBackgroundNfc>());
  });

  test('getPlatformVersion', () async {
    BackgroundNfc backgroundNfcPlugin = BackgroundNfc();
    MockBackgroundNfcPlatform fakePlatform = MockBackgroundNfcPlatform();
    BackgroundNfcPlatform.instance = fakePlatform;

    expect(await backgroundNfcPlugin.getPlatformVersion(), '42');
  });
}
