package com.example.finalproject_demo.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/*
 * 기울기 — 폰을 어느 쪽으로 얼마나 기울였나 (`docs/맞춤미션_설계.md` §5-1 센서 · D4 기울여 굴리기 · #101).
 *
 * 중력 센서(없으면 가속도 센서)를 **화면 방향**으로 바꿔 돌려준다: x 는 화면 오른쪽 아래로 기울면 +, y 는 화면 아래쪽으로 기울면 +.
 * 값은 대략 -1 ~ 1 (평평하면 0). 앱은 가로 화면이라 기기 축과 화면 축이 다르다 — 화면 회전으로 맞춘다.
 *
 * 센서가 없거나 못 쓰면 **null** — 미션 화면은 바로 끌기(탭 길)만 쓴다(원칙 6). 센서 값은 그 자리에서 쓰고 저장하지 않는다.
 */

/** 화면 기준 기울기, 센서가 없으면 null. [active] 가 false 면 센서를 끈다 */
@Composable
fun rememberTilt(active: Boolean): Offset? {
    val ctx = LocalContext.current
    val view = LocalView.current
    val manager = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember {
        manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    var tilt by remember { mutableStateOf(if (sensor == null) null else Offset.Zero) }
    DisposableEffect(active, sensor) {
        if (!active || sensor == null || manager == null || motionFrozen) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = SensorManager.GRAVITY_EARTH
                // 기기 축: x 오른쪽 · y 위쪽(세로 기준). 기울인 쪽으로 중력이 음수 → 화면 쪽으로 돌린다
                val dx = -e.values[0] / g
                val dy = e.values[1] / g
                val rotation = view.display?.rotation ?: Surface.ROTATION_0
                val screen = when (rotation) {
                    Surface.ROTATION_90 -> Offset(dy, -dx)
                    Surface.ROTATION_180 -> Offset(-dx, -dy)
                    Surface.ROTATION_270 -> Offset(-dy, dx)
                    else -> Offset(dx, dy)
                }
                tilt = Offset(screen.x.coerceIn(-1f, 1f), screen.y.coerceIn(-1f, 1f))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return if (active) tilt else null
}
