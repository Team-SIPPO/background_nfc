package io.github.team_sippo.background_nfc.background_nfc

import android.app.Activity
import android.content.Intent
import android.nfc.*
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Handler
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.flutter.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.ArrayList


/** BackgroundNfcPlugin */
@RequiresApi(Build.VERSION_CODES.KITKAT)
class BackgroundNfcPlugin: FlutterPlugin, EventChannel.StreamHandler, MethodCallHandler, PluginRegistry.NewIntentListener,
        NfcAdapter.ReaderCallback, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var tagChannel: EventChannel
  private val LOG_TAG = "BackgroundNfcPlugin"
  private val NORMAL_READER_MODE = "normal"
  private val DISPATCH_READER_MODE = "dispatch"
  private val DEFAULT_READER_FLAGS =
    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V

  private var activity: Activity? = null
  private val adapter: NfcAdapter? = null
  private var events: EventSink? = null
  private var binding: ActivityPluginBinding? = null

  private val currentReaderMode: String? = null
  private var lastTag: Tag? = null
  private var resultBuffer: MutableList<Map<*, *>> = mutableListOf();

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "background_nfc")
    channel.setMethodCallHandler(this)
    tagChannel = EventChannel(flutterPluginBinding.binaryMessenger, "background_nfc/tags")
    tagChannel.setStreamHandler(this)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    Log.d(LOG_TAG, "version check.")
    if (call.method == "getPlatformVersion") {
      result.success("Android ${android.os.Build.VERSION.RELEASE}")
    } else {
      result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  private fun handleNDEFTagFromIntent(tag: Tag) {
    val ndef: Ndef = Ndef.get(tag)
//    val formatable: NdefFormatable = NdefFormatable.get(tag)
    val result: Map<*, *>
    val message: NdefMessage = ndef.getCachedNdefMessage()
    try {
      ndef.close()
    } catch (e: IOException) {
      Log.e(LOG_TAG, "close NDEF tag error: " + e.message)
    }
    result = formatNDEFMessageToResult(ndef, message)!!
    Log.d(LOG_TAG, "---------------")
    Log.d(LOG_TAG, result.toString())
    val record = result["records"] as List<Map<String, *>>
    Log.d(LOG_TAG, record[0]["payload"].toString())
    Log.d(LOG_TAG, "---------------")
    if (this.events == null){
      resultBuffer.add(result)
    } else{
      eventSuccess(result)
    }
  }

  private fun eventSuccess(result: Any?) {
    val mainThread = Handler(activity!!.mainLooper)
    val runnable = Runnable {
      if (events != null) {
        // Event stream must be handled on main/ui thread
        events!!.success(result)
      }
    }
    mainThread.post(runnable)
  }

  private fun eventError(code: String, message: String, details: Any?) {
    val mainThread = Handler(activity!!.mainLooper)
    val runnable = Runnable {
      events?.error(code, message, details)
    }
    mainThread.post(runnable)
  }
  private fun formatEmptyWritableNDEFMessage(): MutableMap<String, Any> {
    val result: MutableMap<String, Any> = HashMap()
    result["id"] = ""
    result["message_type"] = "ndef"
    result["type"] = ""
    result["writable"] = true
    val records: MutableList<Map<String, String>> = ArrayList()
    val emptyRecord: MutableMap<String, String> = HashMap()
    emptyRecord["tnf"] = "empty"
    emptyRecord["id"] = ""
    emptyRecord["type"] = ""
    emptyRecord["payload"] = ""
    emptyRecord["data"] = ""
    emptyRecord["languageCode"] = ""
    records.add(emptyRecord)
    result["records"] = records
    return result
  }


  private fun formatNDEFMessageToResult(ndef: Ndef, message: NdefMessage): Map<String, Any>? {
    val result: MutableMap<String, Any> = HashMap()
    val records: MutableList<Map<String, Any>> = ArrayList()
    for (record in message.getRecords()) {
      val recordMap: MutableMap<String, Any> = HashMap()
      val recordPayload: ByteArray = record.getPayload()
      var charset: Charset = StandardCharsets.UTF_8
      val tnf: Short = record.getTnf()
      val type: ByteArray = record.getType()
      if (tnf == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(type, NdefRecord.RTD_TEXT)) {
        charset =
          if (recordPayload[0].toInt() and 128 == 0) StandardCharsets.UTF_8 else StandardCharsets.UTF_16
      }
      recordMap.put("rawPayload", recordPayload)

      // If the record's tnf is well known and the RTD is set to URI,
      // the URL prefix should be added to the payload
      if (tnf == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(type, NdefRecord.RTD_URI)) {
        recordMap.put("data", String(recordPayload, 1, recordPayload.size - 1, charset))
        var url = ""
        val prefixByte = recordPayload[0]
        when (prefixByte) {
          (0x01).toByte() -> url = "http://www."
          (0x02).toByte() -> url = "https://www."
          (0x03).toByte() -> url = "http://"
          (0x04).toByte() -> url = "https://"
          (0x05).toByte() -> url = "tel:"
          (0x06).toByte() -> url = "mailto:"
          (0x07).toByte() -> url = "ftp://anonymous:anonymous@"
          (0x08).toByte() -> url = "ftp://ftp."
          (0x09).toByte() -> url = "ftps://"
          (0x0A).toByte() -> url = "sftp://"
          (0x0B).toByte() -> url = "smb://"
          (0x0C).toByte() -> url = "nfs://"
          (0x0D).toByte() -> url = "ftp://"
          (0x0E).toByte() -> url = "dav://"
          (0x0F).toByte() -> url = "news:"
          (0x10).toByte() -> url = "telnet://"
          (0x11).toByte() -> url = "imap:"
          (0x12).toByte() -> url = "rtsp://"
          (0x13).toByte() -> url = "urn:"
          (0x14).toByte() -> url = "pop:"
          (0x15).toByte() -> url = "sip:"
          (0x16).toByte() -> url = "sips"
          (0x17).toByte() -> url = "tftp:"
          (0x18).toByte() -> url = "btspp://"
          (0x19).toByte() -> url = "btl2cap://"
          (0x1A).toByte() -> url = "btgoep://"
          (0x1B).toByte() -> url = "btgoep://"
          (0x1C).toByte() -> url = "irdaobex://"
          (0x1D).toByte() -> url = "file://"
          (0x1E).toByte() -> url = "urn:epc:id:"
          (0x1F).toByte() -> url = "urn:epc:tag:"
          (0x20).toByte() -> url = "urn:epc:pat:"
          (0x21).toByte() -> url = "urn:epc:raw:"
          (0x22).toByte() -> url = "urn:epc:"
          (0x23).toByte() -> url = "urn:nfc:"
        }
        recordMap.put("payload", url + String(recordPayload, 1, recordPayload.size - 1, charset))
      } else if (tnf == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(type, NdefRecord.RTD_TEXT)) {
        val languageCodeLength = (recordPayload[0].toInt() and 0x3f) + 1
        recordMap.put("payload", String(recordPayload, 1, recordPayload.size - 1, charset))
        recordMap.put("languageCode", String(recordPayload, 1, languageCodeLength - 1, charset))
        recordMap.put(
          "data",
          String(
            recordPayload,
            languageCodeLength,
            recordPayload.size - languageCodeLength,
            charset
          )
        )
      } else {
        recordMap.put("payload", String(recordPayload, charset))
        recordMap.put("data", String(recordPayload, charset))
      }
      recordMap["id"] = String(record.getId(), StandardCharsets.UTF_8)
      recordMap["type"] = String(record.getType(), StandardCharsets.UTF_8)
      val tnfValue: String = when (tnf) {
        NdefRecord.TNF_EMPTY -> "empty"
        NdefRecord.TNF_WELL_KNOWN -> "well_known"
        NdefRecord.TNF_MIME_MEDIA -> "mime_media"
        NdefRecord.TNF_ABSOLUTE_URI -> "absolute_uri"
        NdefRecord.TNF_EXTERNAL_TYPE -> "external_type"
        NdefRecord.TNF_UNCHANGED -> "unchanged"
        else -> "unknown"
      }
      recordMap.put("tnf", tnfValue)
      records.add(recordMap)
    }
    result["id"] = getNDEFTagID(ndef)
    result["message_type"] = "ndef"
    result["type"] = ndef.type
    result["records"] = records
    result["writable"] = ndef.isWritable
    return result
  }

  @kotlin.Throws(java.lang.IllegalArgumentException::class)
  private fun formatMapToNDEFMessage(map: Map<*, *>): NdefMessage? {
    val mapRecordsObj = map.get("records")
    if (mapRecordsObj == null) {
      throw java.lang.IllegalArgumentException("missing records")
    } else if (mapRecordsObj !is List<*>) {
      throw java.lang.IllegalArgumentException("map key 'records' is not a list")
    }
    val mapRecords = mapRecordsObj
    val amountOfRecords = mapRecords.size
    val records: Array<NdefRecord?> = arrayOfNulls<NdefRecord>(amountOfRecords)
    for (i in 0 until amountOfRecords) {
      val mapRecordObj = mapRecords[i]!! as? Map<*, *>
        ?: throw java.lang.IllegalArgumentException("record is not a map")
      val mapRecord = mapRecordObj
      var id = mapRecord.get("id") as String?
      if (id == null) {
        id = ""
      }
      var type = mapRecord.get("type") as String?
      if (type == null) {
        type = ""
      }
      var languageCode = mapRecord.get("languageCode") as String
      var payload = mapRecord.get("payload") as String?
      if (payload == null) {
        payload = ""
      }
      val tnf = mapRecord.get("tnf") as String?
        ?: throw java.lang.IllegalArgumentException("record tnf is null")
      var idBytes: ByteArray? = id.toByteArray()
      var typeBytes: ByteArray? = type.toByteArray()
      val languageCodeBytes: ByteArray = languageCode.toByteArray(StandardCharsets.US_ASCII)
      var payloadBytes: ByteArray? = payload.toByteArray()
      var tnfValue: Short
      when (tnf) {
        "empty" -> {
          // Empty records are not allowed to have a ID, type or payload.
          tnfValue = NdefRecord.TNF_EMPTY
          idBytes = null
          typeBytes = null
          payloadBytes = null
        }
        "well_known" -> {
          tnfValue = NdefRecord.TNF_WELL_KNOWN
          if (Arrays.equals(typeBytes, NdefRecord.RTD_TEXT)) {
            // The following code basically constructs a text record like NdefRecord.createTextRecord() does,
            // however NdefRecord.createTextRecord() is only available in SDK 21+ while nfc_in_flutter
            // goes down to SDK 19.
            val buffer: ByteBuffer =
              ByteBuffer.allocate(1 + languageCodeBytes.size + payloadBytes!!.size)
            val status = (languageCodeBytes.size and 0xFF).toByte()
            buffer.put(status)
            buffer.put(languageCodeBytes)
            buffer.put(payloadBytes)
            payloadBytes = buffer.array()
          } else if (Arrays.equals(typeBytes, NdefRecord.RTD_URI)) {
            // Instead of manually constructing a URI payload with the correct prefix and
            // everything, create a record using NdefRecord.createUri and copy it's payload.
            val uriRecord: NdefRecord = NdefRecord.createUri(payload)
            payloadBytes = uriRecord.getPayload()
          }
        }
        "mime_media" -> tnfValue = NdefRecord.TNF_MIME_MEDIA
        "absolute_uri" -> tnfValue = NdefRecord.TNF_ABSOLUTE_URI
        "external_type" -> tnfValue = NdefRecord.TNF_EXTERNAL_TYPE
        "unchanged" -> throw java.lang.IllegalArgumentException("records are not allowed to have their TNF set to UNCHANGED")
        else -> {
          tnfValue = NdefRecord.TNF_UNKNOWN
          typeBytes = null
        }
      }
      records[i] = NdefRecord(tnfValue, typeBytes, idBytes, payloadBytes)
    }
    return NdefMessage(records)
  }

  private fun getNDEFTagID(ndef: Ndef): String {
    val idByteArray = ndef.tag.id
    // Fancy string formatting snippet is from
    // https://gist.github.com/luixal/5768921#gistcomment-1788815
    return java.lang.String.format("%0" + idByteArray.size * 2 + "X", BigInteger(1, idByteArray))
  }

  override fun onNewIntent(intent: Intent): Boolean {
    Log.d(LOG_TAG, "nfc read.")
    val action: String? = intent.getAction()
    if (NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
      val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
      lastTag = tag
      Log.d(LOG_TAG, tag.toString())
      handleNDEFTagFromIntent(tag!!)
      return true
    }
    return false
  }

  override fun onTagDiscovered(tag: Tag?) {
    lastTag = tag
    val ndef = Ndef.get(tag)
    val formatable = NdefFormatable.get(tag)
    if (ndef != null) {
      var closed = false
      try {
        ndef.connect()
        val message = ndef.ndefMessage
        if (message == null) {
          eventSuccess(this.formatEmptyNDEFMessage(ndef))
          return
        }
        try {
          ndef.close()
          closed = true
        } catch (e: IOException) {
          Log.e(LOG_TAG, "close NDEF tag error: " + e.message)
        }
        eventSuccess(formatNDEFMessageToResult(ndef, message)!!)
      } catch (e: IOException) {
        val details: MutableMap<String, Any> = HashMap()
        details["fatal"] = true
        eventError("IOError", e.message!!, details)
      } catch (e: FormatException) {
        eventError("NDEFBadFormatError", e.message!!, null)
      } finally {
        // Close if the tag connection if it isn't already
        if (!closed) {
          try {
            ndef.close()
          } catch (e: IOException) {
            Log.e(LOG_TAG, "close NDEF tag error: " + e.message)
          }
        }
      }
    } else if (formatable != null) {
      eventSuccess(formatEmptyWritableNDEFMessage())
    }
  }

  override fun onListen(arguments: Any?, eventSink: EventSink?) {
    val flag = events == null
    this.events = eventSink
    if(flag){
      for(result in resultBuffer){
        eventSuccess(result)
      }
    }
  }

  private fun formatEmptyNDEFMessage(ndef: Ndef): Map<String, Any?> {
    val result: MutableMap<String, Any> = formatEmptyWritableNDEFMessage()
    result.put("id", getNDEFTagID(ndef))
    result.put("writable", ndef.isWritable)
    return result
  }

  override fun onCancel(arguments: Any?) {
    if (adapter != null) {
      when (currentReaderMode) {
        NORMAL_READER_MODE -> adapter.disableReaderMode(activity)
        DISPATCH_READER_MODE -> adapter.disableForegroundDispatch(activity)
        else -> Log.e(LOG_TAG, "unknown reader mode: $currentReaderMode")
      }
    }
    events = null
  }

  private fun handleIntent(intent: Intent, initial: Boolean) {
    onNewIntent(intent);
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    Log.d(LOG_TAG, "onAttachedToActivity")
    this.binding = binding
    this.activity = this.binding!!.activity
    binding.addOnNewIntentListener(this)
    handleIntent(binding.activity.intent, true)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    binding?.removeOnNewIntentListener(this)
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    this.binding = binding
    binding.addOnNewIntentListener(this)
  }

  override fun onDetachedFromActivity() {
    binding?.removeOnNewIntentListener(this)
  }

}
