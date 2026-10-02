package com.example.finalproject_demo.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.example.finalproject_demo.net.Server
import java.io.ByteArrayOutputStream

/*
 * 「오또가 대신 그려 주기」(#32) — 아이가 고른 조각 하나를 서버로 보내 오또 그림을 받는다.
 *
 * 보내는 것은 **그 조각의 선만** 투명 배경 PNG 로 그린 것이다. 화이트보드 전체도, 다른 조각도 안 간다.
 * 우리 서버까지만 가고 외부 업체로는 안 간다(안전 검사에도 원본은 안 보낸다) · 서버는 쓰고 바로 지운다
 * (차별점 1 완화 · #33). 책의 기본은 늘 원본이고, 아이가 오또 그림을 고를 때만 바뀐다.
 */

/** 보낼 그림의 긴 변 — 조각 하나라 작아도 된다. 서버는 640² 로 다시 맞춘다 */
private const val REDRAW_SIDE = 512

/**
 * 조각의 선만 투명 배경에 그린 PNG — 그린 부분만 잘라서, 긴 변 [side] px.
 * [aspect] 는 화이트보드의 폭/높이다(선 좌표가 판 크기의 비율이라 가로를 되돌려야 찌그러지지 않는다).
 */
fun pieceToPng(piece: DiaryPiece, aspect: Float, side: Int = REDRAW_SIDE): ByteArray? {
    val a = aspect.takeIf { it > 0f } ?: 1f
    // 여백은 판 높이의 2% — 가로는 폭 기준 좌표라 aspect 로 나눠야 위아래와 같은 두께가 된다
    val raw = boxOf(piece.strokes) ?: return null
    val pad = 0.02f
    val b = BoardBox(raw.left - pad / a, raw.top - pad, raw.right + pad / a, raw.bottom + pad)
    // 판 높이 = 1, 폭 = aspect 인 단위로 바꿔 긴 변을 side 에 맞춘다
    val wUnits = maxOf(b.width * a, 0.01f)
    val hUnits = maxOf(b.height, 0.01f)
    val scale = side / maxOf(wUnits, hUnits)
    val w = (wUnits * scale).toInt().coerceAtLeast(8)
    val h = (hUnits * scale).toInt().coerceAtLeast(8)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    piece.strokes.forEach { s ->
        if (s.pts.size < 2) return@forEach
        val p = Path()
        s.pts.forEachIndexed { i, o ->
            val x = (o.x - b.left) * a * scale
            val y = (o.y - b.top) * scale
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        paint.color = s.color.toArgb()
        paint.strokeWidth = (s.w * a * scale).coerceAtLeast(2f)     // 선 굵기는 판 폭에 대한 비율
        canvas.drawPath(p, paint)
    }
    return ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        .also { bmp.recycle() }
}

/**
 * 오또 그림을 부르는 곳 — 테스트가 서버 없이 바꿔 끼운다. null = 원본 그대로(검사에 걸림 · 늦음 · 실패).
 * [description] 은 이름을 가린 조각 이름이다(규칙 6).
 */
internal var requestRedraw: suspend (png: ByteArray, description: String) -> ByteArray? = { png, description ->
    val t0 = System.currentTimeMillis()
    Server.redraw(png, description, mode = "diary").also { out ->
        // 실기기에서 「지금 보낸 것에 지금 온 그림」인지 맞춰 보는 기록 — 가린 이름 · 크기 · 시간 · 지문만(그림은 남기지 않는다)
        runCatching {
            android.util.Log.i(
                "Diary",
                "redraw 「$description」 sent ${png.size}B #${fingerprint(png)} → " +
                    (out?.let { "got ${it.size}B #${fingerprint(it)}" } ?: "nothing (preset · failed)") +
                    " in ${System.currentTimeMillis() - t0}ms",
            )
        }
    }
}

/** 그림 지문 — 같은 그림이 두 번 오는지 · 어느 요청의 답인지 맞춰 볼 때만 쓴다 */
internal fun fingerprint(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-1").digest(bytes).take(5).joinToString("") { "%02x".format(it) }
