import 'package:flutter/services.dart';

import 'parse_nfc_message.dart';
import 'background_nfc_platform_interface.dart';

class BackgroundNfc {
  static final EventChannel _eventChannel = const EventChannel("background_nfc/tags");
  static Stream<dynamic>? _tagStream;
  Future<String?> getPlatformVersion() {
    return BackgroundNfcPlatform.instance.getPlatformVersion();
  }
  Stream<dynamic> detectNFCStream() {
    return _eventChannel.receiveBroadcastStream().where((tag) {
      // In the future when more tag types are supported, this must be changed.
      assert(tag is Map);
      return tag["message_type"] == "ndef";
    }).map<NFCMessage>((tag) {
      return NFC.parseNFCTag(tag);
    });
  }
}
