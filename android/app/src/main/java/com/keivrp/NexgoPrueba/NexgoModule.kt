package com.keivrp.NexgoPrueba

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.nexgo.sdk.DeviceEngine  // ¡Cambia esto!
import com.nexgo.sdk.printer.Printer

class NexgoModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    @ReactMethod
    fun printText(text: String, promise: Promise) {
        try {
            val deviceEngine = DeviceEngine.getInstance(reactApplicationContext) // Corrección clave
            val printer = deviceEngine.printer
            printer.initPrinter()
            printer.appendPrnStr(text, 20, Printer.Align.CENTER, false) // Align puede ser una clase, no enum
            printer.startPrint(true, null)
            promise.resolve("Impresión exitosa")
        } catch (e: Exception) {
            promise.reject("ERROR_IMPRESORA", e.message)
        }
    }
}