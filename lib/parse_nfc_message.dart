import 'dart:async';
import 'dart:core';

import 'package:flutter/services.dart';

import './exceptions.dart';



class NFC {
  static NDEFMessage parseNFCTag(Map tag){
      List<NDEFRecord> records = [];
      for (var record in tag["records"]) {
        NFCTypeNameFormat tnf;
        switch (record["tnf"]) {
          case "empty":
            tnf = NFCTypeNameFormat.empty;
            break;
          case "well_known":
            tnf = NFCTypeNameFormat.well_known;
            break;
          case "mime_media":
            tnf = NFCTypeNameFormat.mime_media;
            break;
          case "absolute_uri":
            tnf = NFCTypeNameFormat.absolute_uri;
            break;
          case "external_type":
            tnf = NFCTypeNameFormat.external;
            break;
          case "unchanged":
            tnf = NFCTypeNameFormat.unchanged;
            break;
          default:
            tnf = NFCTypeNameFormat.unknown;
        }

        records.add(NDEFRecord._internal(
          record["id"],
          record["payload"],
          record["type"],
          tnf,
          record["data"],
          record["languageCode"],
          record["rawPayload"],
        ));
      }

      return NDEFMessage._internal(tag["id"], tag["type"], records);
  }
}

/// NFCReaderMode is an interface for different reading modes
// The reading modes are implemented as classes instead of enums, so they could
// support options in the future without breaking changes.
abstract class NFCReaderMode {
  String get name;

  Map get _options;
}

/// NFCNormalReaderMode uses the platform's normal reading mode. This does not
/// allow reading from emulated host cards.
class NFCNormalReaderMode implements NFCReaderMode {
  String get name => "normal";

  /// noSounds tells the platform not to play any sounds when a tag has been
  /// read.
  /// Android only
  final bool noSounds;

  const NFCNormalReaderMode({
    this.noSounds = false,
  });

  @override
  Map get _options {
    return {
      "no_platform_sounds": noSounds,
    };
  }
}

/// NFCDispatchReaderMode uses the Android NFC Foreground Dispatch API to read
/// tags with.
class NFCDispatchReaderMode implements NFCReaderMode {
  String get name => "dispatch";

  @override
  Map get _options {
    return {};
  }
}

enum MessageType {
  NDEF,
}

abstract class NFCMessage {
  MessageType get messageType;
  String? get id;

  NFCTag get tag;
}

abstract class NFCTag {
  String? get id;
  bool get writable;
}

class NDEFMessage implements NFCMessage {
  final String? id;
  final String? type;
  final List<NDEFRecord> records;

  NDEFMessage.withRecords(this.records, {this.id}) : type = null;

  NDEFMessage(this.type, this.records) : id = null;

  NDEFMessage._internal(this.id, this.type, this.records);

  // payload returns the payload of the first non-empty record. If all records
  // are empty it will return null.
  String? get payload {
    for (var record in records) {
      if (record.payload != "") {
        return record.payload;
      }
    }
    return null;
  }

  bool get isEmpty {
    if (records.length == 0) {
      return true;
    }
    if (records.length == 1 && records[0].tnf == NFCTypeNameFormat.empty) {
      return true;
    }
    return false;
  }

  // data returns the contents of the first non-empty record. If all records
  // are empty it will return null.
  String? get data {
    for (var record in records) {
      if (record.data != "") {
        return record.data;
      }
    }
    return null;
  }

  @override
  MessageType get messageType => MessageType.NDEF;

  @override
  NDEFTag get tag {
    return NDEFTag._internal(id, true);
  }

  Map<String, dynamic> _toMap() {
    return {
      "id": id,
      "type": type,
      "records": records.map((record) => record._toMap()).toList(),
    };
  }
}

enum NFCTypeNameFormat {
  empty,
  well_known,
  mime_media,
  absolute_uri,
  external,
  unknown,
  unchanged,
}

class NDEFRecord {
  final String? id;
  final String payload;
  final String type;
  final String data;
  final NFCTypeNameFormat tnf;

  /// languageCode will be the language code of a well known text record. If the
  /// record is not created with the well known TNF and Text RTD, this will be
  /// null.
  final String? languageCode;

  /// rawPayload contains the raw payload provided by the reader.
  /// It will only be set when reading NDEF tags. Otherwise it will be null.
  final Uint8List? rawPayload;

  NDEFRecord.empty()
      : id = null,
        type = "",
        payload = "",
        data = "",
        tnf = NFCTypeNameFormat.empty,
        languageCode = null,
        rawPayload = null;

  NDEFRecord.plain(String data)
      : id = null,
        type = "text/plain",
        payload = data,
        this.data = data,
        tnf = NFCTypeNameFormat.mime_media,
        languageCode = null,
        rawPayload = null;

  NDEFRecord.type(this.type, String payload)
      : id = null,
        this.payload = payload,
        data = payload,
        tnf = NFCTypeNameFormat.mime_media,
        languageCode = null,
        rawPayload = null;

  NDEFRecord.text(String message, {languageCode = "en"})
      : id = null,
        data = message,
        payload = message,
        type = "T",
        tnf = NFCTypeNameFormat.well_known,
        this.languageCode = languageCode,
        rawPayload = null;

  NDEFRecord.uri(Uri uri)
      : id = null,
        data = uri.toString(),
        payload = uri.toString(),
        type = "U",
        tnf = NFCTypeNameFormat.well_known,
        languageCode = null,
        rawPayload = null;

  NDEFRecord.absoluteUri(Uri uri)
      : id = null,
        data = uri.toString(),
        payload = uri.toString(),
        type = "",
        tnf = NFCTypeNameFormat.absolute_uri,
        languageCode = null,
        rawPayload = null;

  NDEFRecord.external(this.type, String payload)
      : id = null,
        data = payload,
        this.payload = payload,
        tnf = NFCTypeNameFormat.external,
        languageCode = null,
        rawPayload = null;

  NDEFRecord.custom({
    this.id,
    this.payload = "",
    this.type = "",
    this.tnf = NFCTypeNameFormat.unknown,
    this.languageCode,
  })  : data = payload,
        rawPayload = null;

  NDEFRecord._internal(
    this.id,
    this.payload,
    this.type,
    this.tnf,
    this.data,
    this.languageCode,
    this.rawPayload,
  );

  Map<String, dynamic> _toMap() {
    String tnf;
    switch (this.tnf) {
      case NFCTypeNameFormat.empty:
        tnf = "empty";
        break;
      case NFCTypeNameFormat.well_known:
        tnf = "well_known";
        break;
      case NFCTypeNameFormat.mime_media:
        tnf = "mime_media";
        break;
      case NFCTypeNameFormat.absolute_uri:
        tnf = "absolute_uri";
        break;
      case NFCTypeNameFormat.external:
        tnf = "external_type";
        break;
      case NFCTypeNameFormat.unchanged:
        tnf = "unchanged";
        break;
      default:
        tnf = "unknown";
    }

    return {
      "id": id ?? "",
      "payload": payload,
      "type": type,
      "tnf": tnf,
      "languageCode": languageCode,
    };
  }
}

class NDEFTag implements NFCTag {
  final String? id;
  final bool writable;

  NDEFTag._internal(this.id, this.writable);
}

Exception _mapException(dynamic error) {
  if (error is PlatformException) {
    switch (error.code) {
      case "NDEFUnsupportedFeatureError":
        error = NDEFReadingUnsupportedException();
        break;
      case "UserCanceledSessionError":
        error = NFCUserCanceledSessionException();
        break;
      case "SessionTimeoutError":
        error = NFCSessionTimeoutException();
        break;
      case "SessionTerminatedUnexpectedlyErorr":
        error = NFCSessionTerminatedUnexpectedlyException(error.message);
        break;
      case "SystemIsBusyError":
        error = NFCSystemIsBusyException(error.message);
        break;
      case "IOError":
        error = NFCIOException(error.message);
        break;
      case "NDEFBadFormatError":
        error = NDEFBadFormatException(error.message);
        break;
    }
  }
  return error;
}