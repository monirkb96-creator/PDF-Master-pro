package com.example.core.util

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.model.PdfTextBlock
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

  /**
   * Detects printed dark-ink text lines and column phrases directly from any rendered PDF page
   * bitmap (including Bangla vector curves, scanned documents, and hybrid PDFs) while ignoring
   * pastel watermarks, QR codes, logos, and barcodes.
   */
  fun detectVisualTextLines(pageBmp: Bitmap?, pageIndex: Int): List<PdfTextBlock> {
    if (pageBmp == null || pageBmp.width < 32 || pageBmp.height < 32) return emptyList()
    val bw = pageBmp.width
    val bh = pageBmp.height
    val step = max(1, bw / 1000)
    val gw = bw / step
    val gh = bh / step
    if (gw < 16 || gh < 16) return emptyList()

    val darkGrid = BooleanArray(gw * gh)
    val rowBuf = IntArray(bw)
    val rowInkCount = IntArray(gh)

    for (gy in 0 until gh) {
      val sy = (gy * step).coerceIn(0, bh - 1)
      pageBmp.getPixels(rowBuf, 0, bw, 0, sy, bw, 1)
      var cnt = 0
      val rowOffset = gy * gw
      for (gx in 0 until gw) {
        val sx = (gx * step).coerceIn(0, bw - 1)
        val px = rowBuf[sx]
        val lum = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)) / 255f
        if (lum < 0.48f) {
          darkGrid[rowOffset + gx] = true
          cnt++
        }
      }
      rowInkCount[gy] = cnt
    }

    // Group active rows into horizontal text bands, allowing small vertical gap for Bangla vowel signs/dots
    data class RowBand(val y0: Int, val y1: Int)
    val bands = mutableListOf<RowBand>()
    val minBandH = (gh * 0.003f).roundToInt().coerceAtLeast(3)
    val maxBandH = (gh * 0.065f).roundToInt().coerceAtLeast(18)
    val maxVertGap = (gh * 0.0015f).roundToInt().coerceIn(1, 2)
    var y = 0
    while (y < gh) {
      if (rowInkCount[y] >= 2) {
        val startY = y
        var endY = y
        var emptyStreak = 0
        y++
        while (y < gh) {
          if (rowInkCount[y] >= 2) {
            endY = y
            emptyStreak = 0
          } else {
            emptyStreak++
            if (emptyStreak > maxVertGap) break
          }
          y++
        }
        val bandH = endY - startY + 1
        if (bandH in minBandH..maxBandH) {
          bands.add(RowBand(startY, endY))
        }
      } else {
        y++
      }
    }

    val blocks = mutableListOf<PdfTextBlock>()
    var seq = 0
    val colHasInk = BooleanArray(gw)
    val maxSegH = (gh * 0.055f).roundToInt().coerceAtLeast(16)
    val minSegW = (gw * 0.008f).roundToInt().coerceAtLeast(6)
    val minSegH = (gh * 0.0035f).roundToInt().coerceAtLeast(4)
    val minGapX = (gw * 0.004f).roundToInt().coerceAtLeast(3)
    val maxGapX = (gw * 0.016f).roundToInt().coerceAtLeast(12)

    for (band in bands) {
      val bandH = band.y1 - band.y0 + 1
      for (gx in 0 until gw) {
        var found = false
        for (gy in band.y0..band.y1) {
          if (darkGrid[gy * gw + gx]) {
            found = true
            break
          }
        }
        colHasInk[gx] = found
      }

      val mergeGap = (bandH * 0.72f).roundToInt().coerceIn(minGapX, maxGapX)
      var gx = 0
      while (gx < gw) {
        if (!colHasInk[gx]) {
          gx++
          continue
        }
        val segX0 = gx
        var segX1 = gx
        var gap = 0
        gx++
        while (gx < gw) {
          if (colHasInk[gx]) {
            segX1 = gx
            gap = 0
          } else {
            gap++
            if (gap > mergeGap) break
          }
          gx++
        }

        val segW = segX1 - segX0 + 1
        if (segW < minSegW) continue

        // Tighten vertical bounds specifically around this phrase segment
        var segY0 = band.y1
        var segY1 = band.y0
        var inkPixels = 0
        for (sy in band.y0..band.y1) {
          val rowOffset = sy * gw
          for (sx in segX0..segX1) {
            if (darkGrid[rowOffset + sx]) {
              if (sy < segY0) segY0 = sy
              if (sy > segY1) segY1 = sy
              inkPixels++
            }
          }
        }

        val segH = segY1 - segY0 + 1
        if (segH < minSegH || segH > maxSegH || inkPixels < 8) continue

        val fillRatio = inkPixels.toFloat() / (segW * segH).toFloat()
        if (fillRatio < 0.06f || fillRatio > 0.88f) continue

        // Filter out horizontal 1D barcodes: wide segments with alternating blank gaps and
        // solid full-height vertical bars (>= 88% dark pixels along active columns).
        if (segW > segH * 2) {
          var activeCols = 0
          var solidBarCols = 0
          for (sx in segX0..segX1) {
            var colDark = 0
            for (sy in segY0..segY1) {
              if (darkGrid[sy * gw + sx]) colDark++
            }
            if (colDark > 0) {
              activeCols++
              if (colDark >= segH * 0.88f) {
                solidBarCols++
              }
            }
          }
          val blankCols = segW - activeCols
          if (activeCols > 0 && blankCols >= 3 && solidBarCols.toFloat() / activeCols > 0.65f) {
            continue
          }
        }

        val leftNorm = ((segX0 * step - 2).coerceAtLeast(0).toFloat() / bw).coerceIn(0f, 0.98f)
        val topNorm = ((segY0 * step - 2).coerceAtLeast(0).toFloat() / bh).coerceIn(0f, 0.98f)
        val rightNorm = (((segX1 + 1) * step + 2).coerceAtMost(bw).toFloat() / bw).coerceIn(leftNorm + 0.01f, 1f)
        val bottomNorm = (((segY1 + 1) * step + 2).coerceAtMost(bh).toFloat() / bh).coerceIn(topNorm + 0.008f, 1f)
        val estFontPt = ((bottomNorm - topNorm) * 820f * 0.72f).coerceIn(8f, 36f)

        blocks.add(
          PdfTextBlock(
            id = "vis_${pageIndex}_${seq++}",
            pageIndex = pageIndex,
            text = "",
            leftNorm = leftNorm,
            topNorm = topNorm,
            rightNorm = rightNorm,
            bottomNorm = bottomNorm,
            fontSizePt = estFontPt,
          )
        )
      }
    }
    return blocks
  }

  /**
   * Detects the tight bounding box of dark ink immediately under/around a tapped normalized point
   * when no pre-scanned block matched. Returns null if the tapped spot is blank background.
   */
  fun detectInkPhraseAt(
    pageBmp: Bitmap?,
    nx: Float,
    ny: Float,
    pageIndex: Int,
  ): PdfTextBlock? {
    if (pageBmp == null || pageBmp.width < 32 || pageBmp.height < 32) return null
    val bw = pageBmp.width
    val bh = pageBmp.height
    val cx = (nx * bw).roundToInt().coerceIn(0, bw - 1)
    val cy = (ny * bh).roundToInt().coerceIn(0, bh - 1)

    fun isDarkPixel(x: Int, y: Int): Boolean {
      val px = pageBmp.getPixel(x, y)
      val lum = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)) / 255f
      return lum < 0.48f
    }

    // Search a small radius around tap point for dark ink
    var seedX = -1
    var seedY = -1
    var bestDistSq = Int.MAX_VALUE
    val searchRx = max(12, (bw * 0.014f).roundToInt())
    val searchRy = max(12, (bh * 0.010f).roundToInt())
    for (dy in -searchRy..searchRy step 2) {
      val py = cy + dy
      if (py !in 0 until bh) continue
      for (dx in -searchRx..searchRx step 2) {
        val px = cx + dx
        if (px !in 0 until bw) continue
        if (isDarkPixel(px, py)) {
          val d2 = dx * dx + dy * dy
          if (d2 < bestDistSq) {
            bestDistSq = d2
            seedX = px
            seedY = py
          }
        }
      }
    }
    if (seedX < 0 || seedY < 0) return null

    // Find vertical bounds of the inked line around seedX
    val maxHalfH = max(18, (bh * 0.028f).roundToInt())
    val bandHalfW = max(16, (bw * 0.020f).roundToInt())
    fun rowHasInkAround(y: Int, centerX: Int, halfW: Int): Boolean {
      val x0 = (centerX - halfW).coerceAtLeast(0)
      val x1 = (centerX + halfW).coerceAtMost(bw - 1)
      for (x in x0..x1 step 2) {
        if (isDarkPixel(x, y)) return true
      }
      return false
    }

    var yTop = seedY
    var emptyUp = 0
    for (y in (seedY - 1) downTo (seedY - maxHalfH).coerceAtLeast(0)) {
      if (rowHasInkAround(y, seedX, bandHalfW)) {
        yTop = y
        emptyUp = 0
      } else {
        emptyUp++
        if (emptyUp > 2) break
      }
    }

    var yBot = seedY
    var emptyDown = 0
    for (y in (seedY + 1)..(seedY + maxHalfH).coerceAtMost(bh - 1)) {
      if (rowHasInkAround(y, seedX, bandHalfW)) {
        yBot = y
        emptyDown = 0
      } else {
        emptyDown++
        if (emptyDown > 2) break
      }
    }

    val lineH = (yBot - yTop + 1).coerceAtLeast(6)
    if (lineH > (bh * 0.055f).roundToInt()) return null

    // Expand left and right until a clean inter-column whitespace gap
    val mergeGapX = (lineH * 0.70f).roundToInt().coerceIn(8, 28)
    fun colHasInkInBand(x: Int): Boolean {
      for (y in yTop..yBot step 2) {
        if (isDarkPixel(x, y)) return true
      }
      return false
    }

    var xLeft = seedX
    var gapLeft = 0
    for (x in (seedX - 1) downTo 0) {
      if (colHasInkInBand(x)) {
        xLeft = x
        gapLeft = 0
      } else {
        gapLeft++
        if (gapLeft > mergeGapX) break
      }
    }

    var xRight = seedX
    var gapRight = 0
    for (x in (seedX + 1) until bw) {
      if (colHasInkInBand(x)) {
        xRight = x
        gapRight = 0
      } else {
        gapRight++
        if (gapRight > mergeGapX) break
      }
    }

    val boxW = xRight - xLeft + 1
    if (boxW < 8 || boxW > (bw * 0.85f).roundToInt()) return null

    val leftNorm = ((xLeft - 3).coerceAtLeast(0).toFloat() / bw).coerceIn(0f, 0.98f)
    val topNorm = ((yTop - 3).coerceAtLeast(0).toFloat() / bh).coerceIn(0f, 0.98f)
    val rightNorm = ((xRight + 4).coerceAtMost(bw).toFloat() / bw).coerceIn(leftNorm + 0.01f, 1f)
    val bottomNorm = ((yBot + 4).coerceAtMost(bh).toFloat() / bh).coerceIn(topNorm + 0.008f, 1f)
    val estFontPt = ((bottomNorm - topNorm) * 820f * 0.72f).coerceIn(8f, 36f)

    return PdfTextBlock(
      id = "tap_ink_${pageIndex}_${cx}_${cy}",
      pageIndex = pageIndex,
      text = "",
      leftNorm = leftNorm,
      topNorm = topNorm,
      rightNorm = rightNorm,
      bottomNorm = bottomNorm,
      fontSizePt = estFontPt,
    )
  }
}
