import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:background_nfc/background_nfc_method_channel.dart';

void main() {
  MethodChannelBackgroundNfc platform = MethodChannelBackgroundNfc();
  const MethodChannel channel = MethodChannel('background_nfc');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
