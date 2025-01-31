package com.keivrp.NexgoPrueba

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.facebook.react.bridge.*
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.SdkResult
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.device.reader.CardReader
import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
import com.nexgo.oaf.apiv3.device.reader.RfCardTypeEnum
import com.nexgo.oaf.apiv3.device.reader.TypeAInfoEntity
import com.nexgo.oaf.apiv3.device.reader.TypeBInfoEntity
import java.io.IOException

class CardReaderModule(reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "NexgoDebug-Reader"
    
    override fun getName(): String = "CardReaderModule"

    @ReactMethod
    fun readUltralightCard(promise: Promise) {
        try {
            val deviceEngine = APIProxy.getDeviceEngine(reactApplicationContext)
                ?: throw Exception("DeviceEngine initialization failed")
            
            val cardReader = deviceEngine.cardReader
                ?: throw Exception("CardReader initialization failed")

            cardReader.searchCard(hashSetOf(CardSlotTypeEnum.RF), 30, object : OnCardInfoListener {
                override fun onCardInfo(retCode: Int, cardInfo: CardInfoEntity?) {
                    handleCardResponse(retCode, cardInfo, deviceEngine, promise)
                }

                override fun onSwipeIncorrect() {
                    promise.reject("CARD_ERROR", "Incorrect swipe")
                }

                override fun onMultipleCards() {
                    promise.reject("CARD_ERROR", "Multiple cards detected")
                }
            })
        } catch (e: Exception) {
            promise.reject("CARD_ERROR", e)
        }
    }

    private fun handleCardResponse(
        retCode: Int,
        cardInfo: CardInfoEntity?,
        deviceEngine: DeviceEngine,
        promise: Promise
    ) {
        if (retCode != SdkResult.Success || cardInfo == null) {
            promise.reject("CARD_ERROR", "Read failed: $retCode")
            return
        }

        try {
            val cardData = Arguments.createMap().apply {
                putString("cardNo", cardInfo.cardNo ?: "N/A")
                putString("type", cardInfo.rfCardType?.name)
            }

            if (cardInfo.rfCardType == RfCardTypeEnum.ULTRALIGHT) {
                handleUltralightCard(cardInfo, deviceEngine, cardData)
            }

            promise.resolve(cardData)
        } catch (e: Exception) {
            promise.reject("CARD_ERROR", e)
        }
    }

    private fun handleUltralightCard(
        cardInfo: CardInfoEntity,
        deviceEngine: DeviceEngine,
        cardData: WritableMap
    ) {
        val isoDep = getIsoDep(cardInfo)
        isoDep?.use { 
            it.connect()
            val blocks = readUltralightBlocks(it, 4, 15)
            cardData.putMap("blocks", blocks)
        }

        val nexgoBlocks = readBlocksWithNexgoAPI(deviceEngine, cardInfo)
        cardData.putMap("nexgo_blocks", nexgoBlocks)
    }

    private fun readBlocksWithNexgoAPI(
        deviceEngine: DeviceEngine,
        cardInfo: CardInfoEntity
    ): WritableMap {
        val blocksMap = Arguments.createMap()
        try {
            deviceEngine.ultralightCCardHandler?.let { handler ->
                (4..15).forEach { block ->
                    handler.readBlock(block.toByte())?.let {
                        blocksMap.putString("block_$block", bytesToHex(it))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error lectura bloques Nexgo: ${e.message}")
        }
        return blocksMap
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
    