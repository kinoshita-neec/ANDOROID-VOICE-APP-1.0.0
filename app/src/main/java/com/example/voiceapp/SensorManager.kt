/**
 * SensorManager - センサー管理クラス
 * 
 * このクラスはAndroidのセンサーAPIをラップし、
 * 温度、湿度、照度、気圧のセンサー値を取得します。
 * 
 * 主な機能：
 * - センサーの登録と解除
 * - センサー値の取得と更新
 * - センサー値の有効性チェック
 * - センサー情報の取得（テキスト形式および読み上げ形式）
 * 
 * 使用例：
 * ```
 * val sensorManager = SensorManager(context)
 * sensorManager.registerSensors()
 * val info = sensorManager.getSensorInfo()
 * ```
 * 
 * @property context アプリケーションのContext
 */
package com.example.voiceapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class SensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var temperatureSensor: Sensor? = null
    private var humiditySensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var pressureSensor: Sensor? = null

    // 初期値を無効な値として設定
    private var temperature: Float = Float.NaN
    private var humidity: Float = Float.NaN
    private var light: Float = Float.NaN
    private var pressure: Float = Float.NaN

    // 気温センサーの値が有効か確認するフラグを追加
    private var isTemperatureValid = false

    init {
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    }

    fun registerSensors() {
        temperatureSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        humiditySensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        pressureSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                // 気温の値が正常な範囲内かチェック
                if (event.values[0] > -50f && event.values[0] < 100f) {
                    temperature = event.values[0]
                    isTemperatureValid = true
                    Log.d("SensorManager", "Valid temperature updated: $temperature")
                } else {
                    Log.d("SensorManager", "Invalid temperature value: ${event.values[0]}")
                }
            }
            Sensor.TYPE_RELATIVE_HUMIDITY -> {
                humidity = event.values[0]
                Log.d("SensorManager", "Humidity updated: $humidity")
            }
            Sensor.TYPE_LIGHT -> {
                light = event.values[0]
                Log.d("SensorManager", "Light updated: $light")
            }
            Sensor.TYPE_PRESSURE -> {
                pressure = event.values[0]
                Log.d("SensorManager", "Pressure updated: $pressure")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    fun getSensorInfo(): String {
        return buildString {
            appendLine("温度: ${if (temperatureSensor != null && isTemperatureValid) String.format("%02.1f°C", temperature) else "取得中..."}")
            appendLine("湿度: ${if (humiditySensor != null && !humidity.isNaN()) String.format("%.1f%%", humidity) else "取得中..."}")
            appendLine("照度: ${if (lightSensor != null && !light.isNaN()) String.format("%dルクス", light.toInt()) else "取得中..."}")
            appendLine("気圧: ${if (pressureSensor != null && !pressure.isNaN()) String.format("%dhPa", pressure.toInt()) else "取得中..."}")
        }
    }

    fun getSpeakableInfo(): String {
        // センサー値が有効な場合のみ読み上げ
        return buildString {
            append("現在の環境情報をお知らせします。")
            if (temperatureSensor != null && isTemperatureValid) append("気温は${String.format("%02.1f", temperature)}度、")
            if (humiditySensor != null && !humidity.isNaN()) append("湿度は${String.format("%02.1f", humidity)}パーセント、")
            if (lightSensor != null && !light.isNaN()) append("照度は${String.format("%d", light.toInt())}ルクス、")
            if (pressureSensor != null && !pressure.isNaN()) append("気圧は${String.format("%d", pressure.toInt())}ヘクトパスカルです。")
        }
    }

    // センサー値が有効かどうかを確認するメソッドを追加
    fun isDataReady(): Boolean {
        return (!temperature.isNaN() || temperatureSensor == null) &&
               (!humidity.isNaN() || humiditySensor == null) &&
               (!light.isNaN() || lightSensor == null) &&
               (!pressure.isNaN() || pressureSensor == null)
    }
}