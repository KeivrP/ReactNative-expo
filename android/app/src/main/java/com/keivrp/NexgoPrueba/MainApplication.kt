package com.keivrp.NexgoPrueba

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.ReactHost
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.soloader.SoLoader
import com.keivrp.NexgoPrueba.NexgoPackage
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.DeviceEngine

import expo.modules.ApplicationLifecycleDispatcher
import expo.modules.ReactNativeHostWrapper

class MainApplication : Application(), ReactApplication {

    var deviceEngine: DeviceEngine? = null

    override val reactNativeHost: ReactNativeHost = ReactNativeHostWrapper(
        this,
        object : DefaultReactNativeHost(this) {
            override fun getPackages(): List<ReactPackage> {
                try {
                    Log.d("NexgoDebug", "Inicializando paquetes")
                    return PackageList(this).packages.apply {
                        add(NexgoPackage())
                    }
                } catch (e: Exception) {
                    Log.e("NexgoDebug", "Error al crear paquetes: ${e.message}")
                    throw e
                }
            }

            override fun getJSMainModuleName(): String = ".expo/.virtual-metro-entry"

            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
        }
    )

    override val reactHost: ReactHost
        get() = ReactNativeHostWrapper.createReactHost(applicationContext, reactNativeHost)

   override fun onCreate() {
    super.onCreate()
    try {
        Log.d("NexgoDebug", "Iniciando MainApplication onCreate")

        SoLoader.init(this, false)

        Log.d("NexgoDebug", "La inicialización de DeviceEngine ocurrirá en MainActivity")

        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            load()
        }
        ApplicationLifecycleDispatcher.onApplicationCreate(this)
        Log.d("NexgoDebug", "MainApplication onCreate completado")
    } catch (e: Exception) {
        Log.e("NexgoDebug", "Error en onCreate: ${e.message}")
        e.printStackTrace()
    }
}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ApplicationLifecycleDispatcher.onConfigurationChanged(this, newConfig)
    }
}