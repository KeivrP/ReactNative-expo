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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.DecimalFormat

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
  fun readNFC(promise: Promise) {
    try {
      Log.d(TAG, "Iniciando readNFC()")
  
      // Inicializa el lector de tarjetas
      Log.d(TAG, "Obteniendo instancia de CardReader")
      val cardReader: CardReader? = deviceEngine?.cardReader
  
      if (cardReader == null) {
        Log.e(TAG, "CardReader es NULL")
        promise.reject("NFC_ERROR", "El lector de tarjetas NFC no pudo inicializarse")
        return
      } else {
        Log.d(TAG, "CardReader inicializado correctamente")
      }
  
      // Busca tarjetas NFC
      Log.d(TAG, "Buscando tarjetas NFC")
      val slotTypes = hashSetOf(CardSlotTypeEnum.RF)
      val timeout = 60 // Tiempo de espera en segundos
      val result = cardReader.searchCard(slotTypes, timeout, object : OnCardInfoListener {
        override fun onCardInfo(retCode: Int, cardInfo: CardInfoEntity?) {
            Log.d(TAG, "onCardInfo() llamado con retCode: $retCode")
            if (retCode == SdkResult.Success && cardInfo != null) {
                Log.d(TAG, "Tarjeta NFC detectada: ${cardInfo.cardNo}")
            } else {
                Log.e(TAG, "Error al leer la tarjeta NFC: $retCode")
            }
        }
    
        override fun onSwipeIncorrect() {
            Log.e(TAG, "Deslizamiento incorrecto de tarjeta")
        }
    
        override fun onMultipleCards() {
            Log.e(TAG, "Se detectaron múltiples tarjetas, por favor intente de nuevo.")
        }
    })
    
  
      if (result != SdkResult.Success) {
        Log.e(TAG, "Error al iniciar la búsqueda de tarjetas NFC: ${result}")
        promise.reject("NFC_ERROR", "Error al iniciar la búsqueda de tarjetas NFC: ${result}")
      } else {
        Log.d(TAG, "Búsqueda de tarjetas NFC iniciada correctamente")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error en readNFC(): ${e.message}")
      promise.reject("NFC_ERROR", e)
    }
  }

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
