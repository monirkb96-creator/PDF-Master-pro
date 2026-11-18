package com.example.core.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Generates a small bitmap patch for [leftNorm..rightNorm, topNorm..bottomNorm] where dark text
 * strokes are erased and smoothly reconstructed from surrounding background and watermark pixels,
 * keeping watermarks, seals, paper texture, and light color gradients intact.
 */
object BitmapPatchUtils {

  data class PatchResult(
    val bitmap: Bitmap,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float,
  )

  fun createCleanBackgroundPatch(
    pageBmp: Bitmap?,
    leftNorm: Float,
    topNorm: Float,
    rightNorm: Float,
    bottomNorm: Float,
  ): PatchResult? {
    if (pageBmp == null || pageBmp.width < 16 || pageBmp.height < 16) return null
    val bw = pageBmp.width
    val bh = pageBmp.height

    val l = min(leftNorm, rightNorm).coerceIn(0f, 0.99f)
    val r = max(leftNorm, rightNorm).coerceIn(l + 0.005f, 1f)
    val t = min(topNorm, bottomNorm).coerceIn(0f, 0.99f)
    val b = max(topNorm, bottomNorm).coerceIn(t + 0.005f, 1f)

    val padX = max(2, (bw * 0.003f).roundToInt())
    val padY = max(2, (bh * 0.003f).roundToInt())
    val ring = 6

    val x0 = (l * bw).roundToInt().coerceIn(0, bw - 2)
    val x1 = (r * bw).roundToInt().coerceIn(x0 + 1, bw - 1)
    val y0 = (t * bh).roundToInt().coerceIn(0, bh - 2)
    val y1 = (b * bh).roundToInt().coerceIn(y0 + 1, bh - 1)

    val outX0 = (x0 - padX).coerceAtLeast(0)
    val outY0 = (y0 - padY).coerceAtLeast(0)
    val outX1 = (x1 + padX).coerceAtMost(bw - 1)
    val outY1 = (y1 + padY).coerceAtMost(bh - 1)

    val ctxX0 = (outX0 - ring).coerceAtLeast(0)
    val ctxY0 = (outY0 - ring).coerceAtLeast(0)
    val ctxX1 = (outX1 + ring).coerceAtMost(bw - 1)
    val ctxY1 = (outY1 + ring).coerceAtMost(bh - 1)

    val cw = ctxX1 - ctxX0 + 1
    val ch = ctxY1 - ctxY0 + 1
    if (cw <= 2 || ch <= 2) return null

    val pixels = IntArray(cw * ch)
    pageBmp.getPixels(pixels, 0, cw, ctxX0, ctxY0, cw, ch)

    val lums = FloatArray(cw * ch)
    for (i in pixels.indices) {
      val px = pixels[i]
      lums[i] = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)) / 255f
    }

    val innerStartX = outX0 - ctxX0
    val innerEndX = outX1 - ctxX0
    val innerStartY = outY0 - ctxY0
    val innerEndY = outY1 - ctxY0

    // Estimate per-column local background from the top and bottom outer reference ring
    val colBgR = FloatArray(cw)
    val colBgG = FloatArray(cw)
    val colBgB = FloatArray(cw)
    val colBgLum = FloatArray(cw)

    for (x in 0 until cw) {
      var sumR = 0f
      var sumG = 0f
      var sumB = 0f
      var sumLum = 0f
      var cnt = 0
      for (y in 0 until innerStartY) {
        val idx = y * cw + x
        if (lums[idx] > 0.52f) {
          val px = pixels[idx]
          sumR += Color.red(px)
          sumG += Color.green(px)
          sumB += Color.blue(px)
          sumLum += lums[idx]
          cnt++
        }
      }
      for (y in (innerEndY + 1) until ch) {
        val idx = y * cw + x
        if (lums[idx] > 0.52f) {
          val px = pixels[idx]
          sumR += Color.red(px)
          sumG += Color.green(px)
          sumB += Color.blue(px)
          sumLum += lums[idx]
          cnt++
        }
      }
      if (cnt == 0) {
        for (y in 0 until innerStartY) {
          val idx = y * cw + x
          val px = pixels[idx]
          sumR += Color.red(px)
          sumG += Color.green(px)
          sumB += Color.blue(px)
          sumLum += lums[idx]
          cnt++
        }
        for (y in (innerEndY + 1) until ch) {
          val idx = y * cw + x
          val px = pixels[idx]
          sumR += Color.red(px)
          sumG += Color.green(px)
          sumB += Color.blue(px)
          sumLum += lums[idx]
          cnt++
        }
      }
      if (cnt > 0) {
        colBgR[x] = sumR / cnt
        colBgG[x] = sumG / cnt
        colBgB[x] = sumB / cnt
        colBgLum[x] = sumLum / cnt
      } else {
        colBgR[x] = 255f
        colBgG[x] = 255f
        colBgB[x] = 255f
        colBgLum[x] = 1f
      }
    }

    val mask = BooleanArray(cw * ch)
    for (y in innerStartY..innerEndY) {
      for (x in innerStartX..innerEndX) {
        val idx = y * cw + x
        val px = pixels[idx]
        val lum = lums[idx]
        val bgLum = colBgLum[x]
        val dr = abs(Color.red(px) - colBgR[x])
        val dg = abs(Color.green(px) - colBgG[x])
        val db = abs(Color.blue(px) - colBgB[x])
        val maxDiff = max(dr, max(dg, db))
        val isDarkText = lum < (bgLum - 0.11f) || (bgLum > 0.68f && lum < 0.45f)
        val isColoredInk = lum < (bgLum - 0.05f) && maxDiff > 48f
        if (isDarkText || isColoredInk) {
          mask[idx] = true
        }
      }
    }

    // 1px dilation inside inner box to remove anti-aliased glyph halo fringes
    val dilated = mask.copyOf()
    for (y in innerStartY..innerEndY) {
      for (x in innerStartX..innerEndX) {
        val idx = y * cw + x
        if (!mask[idx]) continue
        for (dy in -1..1) {
          for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx in innerStartX..innerEndX && ny in innerStartY..innerEndY) {
              dilated[ny * cw + nx] = true
            }
          }
        }
      }
    }

    val outPixels = pixels.copyOf()
    for (y in innerStartY..innerEndY) {
      for (x in innerStartX..innerEndX) {
        val idx = y * cw + x
        if (!dilated[idx]) continue

        var accR = 0f
        var accG = 0f
        var accB = 0f
        var accW = 0f

        // Nearest unmasked pixel above
        for (dy in 1 until ch) {
          val ny = y - dy
          if (ny < 0) break
          val nIdx = ny * cw + x
          if (!dilated[nIdx]) {
            val w = 1.6f / dy
            val p = pixels[nIdx]
            accR += Color.red(p) * w
            accG += Color.green(p) * w
            accB += Color.blue(p) * w
            accW += w
            break
          }
        }

        // Nearest unmasked pixel below
        for (dy in 1 until ch) {
          val ny = y + dy
          if (ny >= ch) break
          val nIdx = ny * cw + x
          if (!dilated[nIdx]) {
            val w = 1.6f / dy
            val p = pixels[nIdx]
            accR += Color.red(p) * w
            accG += Color.green(p) * w
            accB += Color.blue(p) * w
            accW += w
            break
          }
        }

        // Nearest unmasked pixel left
        for (dx in 1..14) {
          val nx = x - dx
          if (nx < 0) break
          val nIdx = y * cw + nx
          if (!dilated[nIdx]) {
            val w = 0.8f / dx
            val p = pixels[nIdx]
            accR += Color.red(p) * w
            accG += Color.green(p) * w
            accB += Color.blue(p) * w
            accW += w
            break
          }
        }

        // Nearest unmasked pixel right
        for (dx in 1..14) {
          val nx = x + dx
          if (nx >= cw) break
          val nIdx = y * cw + nx
          if (!dilated[nIdx]) {
            val w = 0.8f / dx
            val p = pixels[nIdx]
            accR += Color.red(p) * w
            accG += Color.green(p) * w
            accB += Color.blue(p) * w
            accW += w
            break
          }
        }

        outPixels[idx] = if (accW > 0f) {
          Color.rgb(
            (accR / accW).roundToInt().coerceIn(0, 255),
            (accG / accW).roundToInt().coerceIn(0, 255),
            (accB / accW).roundToInt().coerceIn(0, 255),
          )
        } else {
          Color.rgb(
            colBgR[x].roundToInt().coerceIn(0, 255),
            colBgG[x].roundToInt().coerceIn(0, 255),
            colBgB[x].roundToInt().coerceIn(0, 255),
          )
        }
      }
    }

    val outW = outX1 - outX0 + 1
    val outH = outY1 - outY0 + 1
    val patchBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val cropped = IntArray(outW * outH)
    for (row in 0 until outH) {
      val srcStart = (innerStartY + row) * cw + innerStartX
      System.arraycopy(outPixels, srcStart, cropped, row * outW, outW)
    }
    patchBmp.setPixels(cropped, 0, outW, 0, 0, outW, outH)

    return PatchResult(
      bitmap = patchBmp,
      leftNorm = outX0.toFloat() / bw,
      topNorm = outY0.toFloat() / bh,
      rightNorm = (outX1 + 1).toFloat() / bw,
      bottomNorm = (outY1 + 1).toFloat() / bh,
    )
  }
}
