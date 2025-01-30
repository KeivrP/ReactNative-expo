package com.keivrp.NexgoPrueba

import android.os.Build
import android.os.Bundle
import android.util.Log
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import expo.modules.ReactActivityDelegateWrapper

class MainActivity : ReactActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    try {
      Log.d("NexgoDebug", "Iniciando MainActivity onCreate")
      setTheme(R.style.AppTheme)
      super.onCreate(null)
      Log.d("NexgoDebug", "MainActivity onCreate completado")
    } catch (e: Exception) {
      Log.e("NexgoDebug", "Error en MainActivity onCreate: ${e.message}")
      e.printStackTrace()
    }
  }

  override fun getMainComponentName(): String = "main"

  override fun createReactActivityDelegate(): ReactActivityDelegate {
    return try {
      ReactActivityDelegateWrapper(
        this,
        BuildConfig.IS_NEW_ARCHITECTURE_ENABLED,
        object : DefaultReactActivityDelegate(
          this,
          mainComponentName,
          fabricEnabled
        ){}
      )
    } catch (e: Exception) {
      Log.e("NexgoDebug", "Error al crear ReactActivityDelegate: ${e.message}")
      e.printStackTrace()
      throw e
    }
  }
}