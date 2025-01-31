package com.keivrp.NexgoPrueba

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.*
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.SdkResult
import com.nexgo.oaf.apiv3.device.printer.AlignEnum
import com.nexgo.oaf.apiv3.device.printer.OnPrintListener
import com.nexgo.oaf.apiv3.device.printer.Printer

import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.device.reader.CardReader
import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
import com.nexgo.oaf.apiv3.device.reader.TypeAInfoEntity
import com.nexgo.oaf.apiv3.device.reader.TypeBInfoEntity
import com.nexgo.oaf.apiv3.device.reader.RfCardTypeEnum 

import com.nexgo.oaf.apiv3.card.ultralight.UltralightEV1CardHandler
import com.nexgo.oaf.apiv3.card.ultralight.UltralightCCardHandler
import com.nexgo.oaf.apiv3.card.mifare.M1CardHandler

import android.nfc.Tag
import android.nfc.tech.IsoDep

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.DecimalFormat
import java.io.IOException

class NexgoModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext),
  OnPrintListener {
  
  private val TAG = "NexgoDebug"
  private var deviceEngine: DeviceEngine? = null
  private var printer: Printer? = null

  override fun getName(): String {
    return "NexgoModule"
  }

  @RequiresApi(Build.VERSION_CODES.O)
  @ReactMethod
  fun printReceipt(receipt: ReadableMap, promise: Promise) {
    try {
      Log.d(TAG, "Iniciando printReceipt()")
      
      // Inicializa DeviceEngine
      Log.d(TAG, "Intentando obtener DeviceEngine")
      deviceEngine = APIProxy.getDeviceEngine(reactApplicationContext)
      
      if (deviceEngine == null) {
        Log.e(TAG, "DeviceEngine es NULL después de la inicialización")
        promise.reject("PRINT_ERROR", "DeviceEngine no pudo inicializarse")
        return
      } else {
        Log.d(TAG, "DeviceEngine inicializado correctamente")
      }

      // Inicializa la impresora
      Log.d(TAG, "Obteniendo instancia de Printer")
      printer = deviceEngine?.printer

      if (printer == null) {
        Log.e(TAG, "Printer es NULL después de la inicialización")
        promise.reject("PRINT_ERROR", "La impresora no pudo inicializarse")
        return
      } else {
        Log.d(TAG, "Printer inicializado correctamente")
      }

      // Inicializa la impresora
      Log.d(TAG, "Inicializando la impresora")
      printer?.initPrinter()

      // Obtener el estado de la impresora
      Log.d(TAG, "Obteniendo estado de la impresora")
      when (val initResult: Int? = printer?.status) {
        SdkResult.Success -> {
          Log.d(TAG, "La impresora está lista, procediendo con la impresión")
          printMerchantSummary(receipt, "")
        }
        SdkResult.Printer_PaperLack -> {
          Log.e(TAG, "La impresora no tiene papel")
          promise.reject("PRINT_ERROR", "La impresora está sin papel")
          Toast.makeText(reactApplicationContext, "Out of Paper!", Toast.LENGTH_LONG).show()
        }
        else -> {
          Log.e(TAG, "Error en la impresora: Código $initResult")
          promise.reject("PRINT_ERROR", "Error al inicializar la impresora: Código $initResult")
          Toast.makeText(reactApplicationContext, "Printer Init Misc Error: $initResult", Toast.LENGTH_LONG).show()
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error en printReceipt(): ${e.message}")
      promise.reject("PRINT_RECEIPT_ERROR", e)
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun printMerchantSummary(receipt: ReadableMap, base64encodedImage: String) {
    try {
      Log.d(TAG, "Iniciando printMerchantSummary()")
      
      // Obtener datos del recibo
      val merchant = receipt.getString("merchant") ?: ""
      val rif = receipt.getString("rif") ?: ""
      val address = receipt.getString("address") ?: ""
      val invoiceNumber = receipt.getString("invoiceNumber") ?: ""
      val date = receipt.getString("date") ?: ""
      val time = receipt.getString("time") ?: ""
      val items = receipt.getArray("items")?.toArrayList() ?: ArrayList()
      val subtotal = receipt.getDouble("subtotal")
      val tax = receipt.getDouble("tax")
      val service = receipt.getDouble("service")
      val total = receipt.getDouble("total")
  
      // Configurar formato numérico
      val numberFormat = DecimalFormat("#,##0.00")
  
      // Cabecera
      printer?.apply {
        appendPrnStr("SENIAT", 26, AlignEnum.CENTER, true)
        appendPrnStr(merchant, 24, AlignEnum.CENTER, true)
        appendPrnStr("RIF: $rif", 24, AlignEnum.CENTER, false)
        appendPrnStr(address, 24, AlignEnum.CENTER, false)
        appendPrnStr("FACTURA", 26, AlignEnum.CENTER, true)
        appendPrnStr("Fecha: $date  Hora: $time", 24, AlignEnum.CENTER, false)
        appendPrnStr("Factura N°$invoiceNumber", 24, AlignEnum.CENTER, false)
        appendPrnStr(" ".repeat(32), 24, AlignEnum.LEFT, false) // Línea separadora
        
        // Encabezados de columnas
        appendPrnStr(
          String.format("%-20s %2s %10s", "Descripción", "Cant", "Monto"), 
          24, 
          AlignEnum.LEFT, 
          true
        )
      }
  
      // Items
      items.forEach { item ->
        val map = item as HashMap<*, *>
        val desc = map["description"].toString()
        val qty = map["quantity"].toString()
        val price = numberFormat.format(map["price"] as Double)
        
        printer?.appendPrnStr(
          String.format("%-20s %2s %10s", desc, qty, price),
          24,
          AlignEnum.LEFT,
          false
        )
      }
  
      // Totales
      printer?.apply {
        appendPrnStr(" ".repeat(32), 24, AlignEnum.LEFT, false) // Separador
        appendPrnStr(String.format("%-25s %12s", "Subtotal", numberFormat.format(subtotal)), 24, AlignEnum.LEFT, true)
        appendPrnStr(String.format("%-25s %12s", "IVA 12%", numberFormat.format(tax)), 24, AlignEnum.LEFT, false)
        appendPrnStr(String.format("%-25s %12s", "Servicio 15%", numberFormat.format(service)), 24, AlignEnum.LEFT, false)
        appendPrnStr(String.format("%-25s %12s", "TOTAL", numberFormat.format(total)), 24, AlignEnum.LEFT, true)
        appendPrnStr(" ".repeat(32), 24, AlignEnum.LEFT, false) // Separador
        appendPrnStr("SENIAT", 24, AlignEnum.CENTER, false)
      }
  
      printer?.startPrint(true, this)
    } catch (e: Exception) {
      Log.e(TAG, "Error en printMerchantSummary(): ${e.message}")
    }
  }

  private fun stringToBitMap(encodedString: String?): Bitmap? {
    return try {
      Log.d(TAG, "Intentando convertir base64 a imagen")
      val encodeByte: ByteArray = Base64.decode(encodedString, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.size)
    } catch (e: Exception) {
      Log.e(TAG, "Error al convertir imagen: ${e.message}")
      null
    }
  }
  @ReactMethod
  @RequiresApi(Build.VERSION_CODES.KITKAT)
  fun readUltralightCard(promise: Promise) {
    try {
        Log.d(TAG, "Iniciando readUltralightCard()")
        val deviceEngine = APIProxy.getDeviceEngine(reactApplicationContext)
            ?: throw Exception("DeviceEngine initialization failed")
        Log.d(TAG, "DeviceEngine inicializado correctamente")

        val cardReader = deviceEngine.cardReader
            ?: throw Exception("CardReader initialization failed")
        Log.d(TAG, "CardReader inicializado correctamente")

        cardReader.searchCard(hashSetOf(CardSlotTypeEnum.RF), 30, object : OnCardInfoListener {
            @RequiresApi(Build.VERSION_CODES.KITKAT)
            override fun onCardInfo(retCode: Int, cardInfo: CardInfoEntity?) {
                Log.d(TAG, "onCardInfo() llamado con retCode: $retCode")
                if (retCode == SdkResult.Success && cardInfo != null) {
                    try {
                        Log.d(TAG, "Tarjeta detectada: ${cardInfo.cardNo}")
                        val cardData = Arguments.createMap().apply {
                            putString("cardNo", cardInfo.cardNo ?: "N/A")
                            putBoolean("isICC", cardInfo.isICC)
                            putString("rfCardType", cardInfo.rfCardType?.name ?: "N/A")
                            
                            // Agregar información específica de Type A o B
                            when (cardInfo) {
                                is TypeAInfoEntity -> {
                                    putString("cardType", "TypeA")
                                    putString("atqa", bytesToHex(cardInfo.atqa))
                                    putString("sak", cardInfo.sak.toString())
                                    putString("uid", bytesToHex(cardInfo.uid))
                                    putString("ats", bytesToHex(cardInfo.ats))
                                }
                                is TypeBInfoEntity -> {
                                    putString("cardType", "TypeB")
                                    putString("atqb", bytesToHex(cardInfo.atqb))
                                    putString("attrResp", bytesToHex(cardInfo.attrResp))
                                }
                            }
                        }

                        if (cardInfo.rfCardType == RfCardTypeEnum.ULTRALIGHT) {
                            Log.d(TAG, "Tarjeta Ultralight detectada")
                            
                            val nexgoBlocksData = readBlocksWithNexgoAPI(cardInfo)
                            cardData.putMap("nexgo_blocks", nexgoBlocksData)

                            val isoDep = getIsoDep(cardInfo)
                            isoDep?.let { isoDepInstance ->
                                try {
                                    isoDepInstance.connect()
                                    Log.d(TAG, "Conexión ISO-DEP establecida")
                                    val blocksData = readUltralightBlocks(isoDepInstance, 4, 15)
                                    cardData.putMap("isodep_blocks", blocksData)
                                } finally {
                                    isoDepInstance.close()
                                    Log.d(TAG, "Conexión ISO-DEP cerrada")
                                }
                            }
                        }

                        promise.resolve(cardData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error leyendo bloques: ${e.message}")
                        promise.reject("ULTRALIGHT_ERROR", "Error reading blocks: ${e.message}")
                    }
                } else {
                    Log.e(TAG, "Fallo en la lectura de la tarjeta: $retCode")
                    promise.reject("ULTRALIGHT_ERROR", "Card read failed: $retCode")
                }
            }

            override fun onSwipeIncorrect() {
                Log.e(TAG, "Deslizamiento incorrecto de la tarjeta")
                promise.reject("ULTRALIGHT_ERROR", "Incorrect card swipe")
            }

            override fun onMultipleCards() {
                Log.e(TAG, "Múltiples tarjetas detectadas")
                promise.reject("ULTRALIGHT_ERROR", "Multiple cards detected")
            }
        })
    } catch (e: Exception) {
        Log.e(TAG, "Error en readUltralightCard(): ${e.message}")
        promise.reject("ULTRALIGHT_ERROR", e)
    }
  }

  @RequiresApi(Build.VERSION_CODES.KITKAT)
  private fun getIsoDep(cardInfo: CardInfoEntity): IsoDep? {
      try {
          Log.d(TAG, "Intentando obtener interfaz ISO-DEP")
          
          when (cardInfo) {
              is TypeAInfoEntity -> {
                  Log.d(TAG, "Procesando tarjeta Type A")
                  logTypeAFields()
                  
                  val possibleTagFields = listOf("mTag", "tag", "nfcTag", "cardTag", "aTag")
                  for (fieldName in possibleTagFields) {
                      try {
                          val tagField = TypeAInfoEntity::class.java.getDeclaredField(fieldName)
                          tagField.isAccessible = true
                          val tag = tagField.get(cardInfo) as Tag?
                          
                          if (tag != null) {
                              Log.d(TAG, "Campo Type A encontrado: $fieldName - Tag: ${tag.id.toHexString()}")
                              return IsoDep.get(tag)
                          }
                      } catch (e: Exception) {
                          Log.d(TAG, "Error en campo $fieldName Type A: ${e.message}")
                      }
                  }
              }
              
              is TypeBInfoEntity -> {
                  Log.d(TAG, "Procesando tarjeta Type B")
                  logTypeBFields()
                  
                  val possibleTagFields = listOf("mTag", "tag", "nfcTag", "cardTag", "bTag")
                  for (fieldName in possibleTagFields) {
                      try {
                          val tagField = TypeBInfoEntity::class.java.getDeclaredField(fieldName)
                          tagField.isAccessible = true
                          val tag = tagField.get(cardInfo) as Tag?
                          
                          if (tag != null) {
                              Log.d(TAG, "Campo Type B encontrado: $fieldName - Tag: ${tag.id.toHexString()}")
                              return IsoDep.get(tag)
                          }
                      } catch (e: Exception) {
                          Log.d(TAG, "Error en campo $fieldName Type B: ${e.message}")
                      }
                  }
              }
              
              else -> {
                  Log.e(TAG, "Tipo de tarjeta no reconocido: ${cardInfo.javaClass.simpleName}")
              }
          }
          
          return null
      } catch (e: Exception) {
          Log.e(TAG, "Error crítico al obtener tag: ${e.javaClass.simpleName} - ${e.message}")
          return null
      }
  }
  
  // Función auxiliar para loguear campos de TypeB
  @RequiresApi(Build.VERSION_CODES.KITKAT)
  private fun logTypeBFields() {
      try {
          val fields = TypeBInfoEntity::class.java.declaredFields
          Log.d(TAG, "=== Campos de TypeBInfoEntity ===")
          fields.forEach { field ->
              field.isAccessible = true
              Log.d(TAG, "Nombre: ${field.name} - Tipo: ${field.type.simpleName}")
          }
      } catch (e: Exception) {
          Log.e(TAG, "Error al listar campos Type B: ${e.message}")
      }
  }

  @RequiresApi(Build.VERSION_CODES.KITKAT)
  private fun logTypeAFields() {
    try {
      val fields = TypeAInfoEntity::class.java.declaredFields
      Log.d(TAG, "=== Campos de TypeAInfoEntity ===")
      fields.forEach { field ->
        field.isAccessible = true
        Log.d(TAG, "Nombre: ${field.name} - Tipo: ${field.type.simpleName}")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error al listar campos: ${e.message}")
    }
  }

  private fun readBlocksWithNexgoAPI(cardInfo: CardInfoEntity): WritableMap {
    val blocksMap = Arguments.createMap()
    try {
        // Get ultralight handler directly from device engine
        val ultralightHandler = deviceEngine?.ultralightCCardHandler
            ?: throw Exception("Ultralight Handler not available")

        // Read blocks
        for (block in 4..15) {
            val blockData = ultralightHandler.readBlock(block.toByte())
            if (blockData != null) {
                blocksMap.putString("block_$block", bytesToHex(blockData))
            } else {
                Log.e(TAG, "Error reading block $block: null response")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error in Nexgo API: ${e.message}")
    }
    return blocksMap
}

  private fun readUltralightBlocks(isoDep: IsoDep, startBlock: Int, endBlock: Int): WritableMap {
    Log.d(TAG, "Leyendo bloques Ultralight desde $startBlock hasta $endBlock")
    val blocksMap = Arguments.createMap()
    try {
      for (block in startBlock..endBlock) {
        val command = byteArrayOf(
          0x30.toByte(), // READ Command para Ultralight
          block.toByte() // Número de bloque
        )

        val response = isoDep.transceive(command)
        val hexResponse = bytesToHex(response)
        Log.d(TAG, "Bloque $block leído: $hexResponse")
        blocksMap.putString("block_$block", hexResponse)
      }
    } catch (e: IOException) {
      Log.e(TAG, "Error leyendo bloque: ${e.message}")
      throw Exception("Error reading block: ${e.message}")
    }
    return blocksMap
  }

  private fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02X".format(it) }
  }

  fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }


  override fun onPrintResult(resultCode: Int) {
    when (resultCode) {
      SdkResult.Success -> Log.d(TAG, "Impresión completada correctamente")
      SdkResult.Printer_Print_Fail -> Log.e(TAG, "Error en impresión: $resultCode")
      SdkResult.Printer_Busy -> Log.e(TAG, "La impresora está ocupada: $resultCode")
      SdkResult.Printer_PaperLack -> Log.e(TAG, "Sin papel: $resultCode")
      SdkResult.Printer_Fault -> Log.e(TAG, "Fallo en la impresora: $resultCode")
      SdkResult.Printer_TooHot -> Log.e(TAG, "La impresora está demasiado caliente: $resultCode")
      SdkResult.Printer_UnFinished -> Log.w(TAG, "Trabajo de impresión no finalizado: $resultCode")
      SdkResult.Printer_Other_Error -> Log.e(TAG, "Otro error en la impresora: $resultCode")
      else -> Log.e(TAG, "Error desconocido en la impresión: $resultCode")
    }
  } 
}
