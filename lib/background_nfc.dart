
import 'background_nfc_platform_interface.dart';

class BackgroundNfc {
  Future<String?> getPlatformVersion() {
    return BackgroundNfcPlatform.instance.getPlatformVersion();
  }
}
