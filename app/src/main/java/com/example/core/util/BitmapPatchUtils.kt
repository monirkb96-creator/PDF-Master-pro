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
    val ring = max(8, (bw * 0.006f).roundToInt()).coerceAtMost(14)

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

    // Determine whether page background is light or dark using outer ring median luminance
    val ringLums = mutableListOf<Float>()
    for (y in 0 until ch) {
      for (x in 0 until cw) {
        if (y !in innerStartY..innerEndY || x !in innerStartX..innerEndX) {
          ringLums.add(lums[y * cw + x])
        }
      }
    }
    val ringMedianLum = if (ringLums.isNotEmpty()) {
      val s = ringLums.sorted()
      s[s.size / 2]
    } else {
      1f
    }
    val isDarkPaper = ringMedianLum < 0.52f

    // Clean bright fallback color from outer ring (only used if a huge stroke has no clean neighbor)
    var globalBgR = 255f
    var globalBgG = 255f
    var globalBgB = 255f
    run {
      var sumR = 0f
      var sumG = 0f
      var sumB = 0f
      var cnt = 0
      for (y in 0 until ch) {
        for (x in 0 until cw) {
          if (y !in innerStartY..innerEndY || x !in innerStartX..innerEndX) {
            val idx = y * cw + x
            val lVal = lums[idx]
            val keep = if (isDarkPaper) lVal <= ringMedianLum + 0.08f else lVal >= 0.68f
            if (keep) {
              val px = pixels[idx]
              sumR += Color.red(px)
              sumG += Color.green(px)
              sumB += Color.blue(px)
              cnt++
            }
          }
        }
      }
      if (cnt > 0) {
        globalBgR = sumR / cnt
        globalBgG = sumG / cnt
        globalBgB = sumB / cnt
      }
    }

    // Step 1: Identify genuine CORE ink pixels across the entire context window.
    // Never match pastel watermarks (e.g. light green #A8D5BA or pastel pink #F4B8B8).
    val coreMask = BooleanArray(cw * ch)
    for (y in 0 until ch) {
      for (x in 0 until cw) {
        val idx = y * cw + x
        val px = pixels[idx]
        val r = Color.red(px)
        val g = Color.green(px)
        val b = Color.blue(px)
        val lum = lums[idx]
        val minCh = min(r, min(g, b))
        val maxCh = max(r, max(g, b))
        val chroma = maxCh - minCh
        val isCoreInk = if (!isDarkPaper) {
          lum < 0.52f || (lum < 0.64f && minCh < 95 && chroma > 45)
        } else {
          lum > (ringMedianLum + 0.18f) && lum > 0.58f
        }
        if (isCoreInk) {
          coreMask[idx] = true
        }
      }
    }

    // Step 2: Expand coreMask by 1px for anti-aliased edge, plus 2nd-px ONLY for neutral gray halos on high-res bitmaps.
    // Also exclude 1px around outer-ring ink from background sampling so neighbor borders never smudge in.
    val erasedMask = BooleanArray(cw * ch)
    val sampleExcludeMask = coreMask.copyOf()
    val useTwoPxHalo = bw >= 800 && !isDarkPaper
    for (y in 0 until ch) {
      for (x in 0 until cw) {
        val idx = y * cw + x
        if (!coreMask[idx]) continue
        val inInner = x in innerStartX..innerEndX && y in innerStartY..innerEndY
        val rad = if (inInner && useTwoPxHalo) 2 else 1
        for (dy in -rad..rad) {
          for (dx in -rad..rad) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until cw || ny !in 0 until ch) continue
            val nIdx = ny * cw + nx
            val distMax = max(abs(dx), abs(dy))
            if (distMax <= 1) {
              sampleExcludeMask[nIdx] = true
              if (nx in innerStartX..innerEndX && ny in innerStartY..innerEndY) {
                erasedMask[nIdx] = true
              }
            } else if (nx in innerStartX..innerEndX && ny in innerStartY..innerEndY) {
              val nPx = pixels[nIdx]
              val nr = Color.red(nPx)
              val ng = Color.green(nPx)
              val nb = Color.blue(nPx)
              val nChroma = max(nr, max(ng, nb)) - min(nr, min(ng, nb))
              if (lums[nIdx] < 0.78f && nChroma < 28) {
                erasedMask[nIdx] = true
                sampleExcludeMask[nIdx] = true
              }
            }
          }
        }
      }
    }

    // Step 3: 8-direction inverse-distance weighted reconstruction ONLY for erased stroke pixels
    val dirs = arrayOf(
      intArrayOf(0, -1),
      intArrayOf(0, 1),
      intArrayOf(-1, 0),
      intArrayOf(1, 0),
      intArrayOf(-1, -1),
      intArrayOf(1, -1),
      intArrayOf(-1, 1),
      intArrayOf(1, 1),
    )
    val outPixels = pixels.copyOf()
    val maxSearch = max(ch, 28)
    for (y in innerStartY..innerEndY) {
      for (x in innerStartX..innerEndX) {
        val idx = y * cw + x
        if (!erasedMask[idx]) continue

        var accR = 0f
        var accG = 0f
        var accB = 0f
        var accW = 0f

        for (d in dirs) {
          val ddx = d[0]
          val ddy = d[1]
          val stepLen = if (ddx != 0 && ddy != 0) 1.414f else 1.0f
          val dirBias = if (ddx == 0) 1.45f else if (ddy == 0) 0.90f else 0.80f
          val limit = if (ddy == 0) min(20, cw) else maxSearch
          for (s in 1..limit) {
            val nx = x + ddx * s
            val ny = y + ddy * s
            if (nx !in 0 until cw || ny !in 0 until ch) break
            val nIdx = ny * cw + nx
            if (!sampleExcludeMask[nIdx]) {
              val w = dirBias / (s * stepLen)
              val p = pixels[nIdx]
              accR += Color.red(p) * w
              accG += Color.green(p) * w
              accB += Color.blue(p) * w
              accW += w
              break
            }
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
            globalBgR.roundToInt().coerceIn(0, 255),
            globalBgG.roundToInt().coerceIn(0, 255),
            globalBgB.roundToInt().coerceIn(0, 255),
          )
        }
      }
    }

    // 1-pass gentle smoothing ONLY inside reconstructed stroke pixels
    val smoothed = outPixels.copyOf()
    for (y in (innerStartY + 1) until innerEndY) {
      for (x in (innerStartX + 1) until innerEndX) {
        val idx = y * cw + x
        if (!erasedMask[idx]) continue
        val p0 = outPixels[idx]
        val pL = outPixels[idx - 1]
        val pR = outPixels[idx + 1]
        val pU = outPixels[idx - cw]
        val pD = outPixels[idx + cw]
        val avgR = (Color.red(p0) * 2 + Color.red(pL) + Color.red(pR) + Color.red(pU) + Color.red(pD)) / 6
        val avgG = (Color.green(p0) * 2 + Color.green(pL) + Color.green(pR) + Color.green(pU) + Color.green(pD)) / 6
        val avgB = (Color.blue(p0) * 2 + Color.blue(pL) + Color.blue(pR) + Color.blue(pU) + Color.blue(pD)) / 6
        smoothed[idx] = Color.rgb(avgR, avgG, avgB)
      }
    }

    // Keep untouched watermark/paper pixels TRANSPARENT so zero rectangular paper patch ever appears!
    val outW = outX1 - outX0 + 1
    val outH = outY1 - outY0 + 1
    val patchBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val cropped = IntArray(outW * outH)
    for (row in 0 until outH) {
      val rowBase = (innerStartY + row) * cw + innerStartX
      val dstBase = row * outW
      for (col in 0 until outW) {
        val srcIdx = rowBase + col
        cropped[dstBase + col] = if (erasedMask[srcIdx]) smoothed[srcIdx] else Color.TRANSPARENT
      }
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
  fun detectVisualTextLines(
    pageBmp: Bitmap?,
    pageIndex: Int,
    pageHeightPt: Float = 842f,
  ): List<PdfTextBlock> {
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

        // Compute font point size from dense core stroke rows so Bangla vowel signs (ু/ী) don't skew line size
        var maxSegRowInk = 0
        val segRowCounts = IntArray(segH)
        for (sy in segY0..segY1) {
          var rCnt = 0
          val rowOffset = sy * gw
          for (sx in segX0..segX1) {
            if (darkGrid[rowOffset + sx]) rCnt++
          }
          segRowCounts[sy - segY0] = rCnt
          if (rCnt > maxSegRowInk) maxSegRowInk = rCnt
        }
        val coreThreshold = (maxSegRowInk * 0.25f).roundToInt().coerceAtLeast(2)
        var coreTop = 0
        var coreBot = segH - 1
        while (coreTop < segH && segRowCounts[coreTop] < coreThreshold) coreTop++
        while (coreBot > coreTop && segRowCounts[coreBot] < coreThreshold) coreBot--
        val coreSegH = (coreBot - coreTop + 1).coerceAtLeast((segH * 0.65f).roundToInt())

        val leftNorm = ((segX0 * step - 2).coerceAtLeast(0).toFloat() / bw).coerceIn(0f, 0.98f)
        val topNorm = ((segY0 * step - 2).coerceAtLeast(0).toFloat() / bh).coerceIn(0f, 0.98f)
        val rightNorm = (((segX1 + 1) * step + 2).coerceAtMost(bw).toFloat() / bw).coerceIn(leftNorm + 0.01f, 1f)
        val bottomNorm = (((segY1 + 1) * step + 2).coerceAtMost(bh).toFloat() / bh).coerceIn(topNorm + 0.008f, 1f)
        val maxPt = (pageHeightPt * 0.08f).coerceAtLeast(48f)
        val tightInkHeightNorm = (coreSegH * step).toFloat() / bh.coerceAtLeast(1)
        val estFontPt = (tightInkHeightNorm * pageHeightPt.coerceAtLeast(200f) * 1.15f).coerceIn(7f, maxPt)
        val isBoldGuess = fillRatio >= 0.42f

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
            isBold = isBoldGuess,
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
    pageHeightPt: Float = 842f,
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
    val tightInkHeightNorm = lineH.toFloat() / bh.coerceAtLeast(1)
    val maxPt = (pageHeightPt * 0.08f).coerceAtLeast(48f)
    val estFontPt = (tightInkHeightNorm * pageHeightPt.coerceAtLeast(200f) * 1.10f).coerceIn(7f, maxPt)

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
