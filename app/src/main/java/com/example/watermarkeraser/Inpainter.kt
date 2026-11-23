package com.example.watermarkeraser

import android.graphics.Bitmap

/**
 * Pure-Kotlin inpainting (no native libs).
 *
 * 1. Grow the mask a little so anti-aliased watermark edges are covered.
 * 2. "Onion peel": fill the masked area layer by layer from the border inwards,
 *    each pixel taking the average of its already-known neighbours.
 * 3. Smooth the filled area with a few relaxation passes so layer seams vanish.
 *
 * Works best on small/thin watermarks over smooth or softly textured backgrounds.
 * Large watermarks over detailed textures will look blurry.
 */
object Inpainter {

    private const val GROW_RADIUS = 2
    private const val SMOOTH_PASSES = 40

    fun inpaint(src: Bitmap, rawMask: BooleanArray): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val mask = grow(rawMask, w, h, GROW_RADIUS)
        val known = BooleanArray(w * h) { !mask[it] }

        if (known.none { it }) return src.copy(Bitmap.Config.ARGB_8888, true)

        // ---- Onion-peel fill ----
        var frontier = ArrayList<Int>()
        for (i in px.indices) if (mask[i] && hasKnownNeighbour(i, w, h, known)) frontier.add(i)

        while (frontier.isNotEmpty()) {
            val colors = IntArray(frontier.size)
            for ((k, idx) in frontier.withIndex()) {
                colors[k] = averageKnown(idx, w, h, known, px)
            }
            val next = ArrayList<Int>()
            for ((k, idx) in frontier.withIndex()) {
                px[idx] = colors[k]
                known[idx] = true
            }
            for (idx in frontier) {
                val x = idx % w
                val y = idx / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val n = ny * w + nx
                    if (mask[n] && !known[n]) {
                        // mark as queued by temporarily using a sentinel check below
                        next.add(n)
                    }
                }
            }
            // de-duplicate next frontier
            frontier = ArrayList(next.distinct())
        }

        // ---- Smoothing inside the filled region ----
        val idxs = mask.indices.filter { mask[it] }.toIntArray()
        repeat(SMOOTH_PASSES) {
            for (idx in idxs) {
                val x = idx % w
                val y = idx / w
                var r = 0; var g = 0; var b = 0; var n = 0
                fun add(nx: Int, ny: Int) {
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) return
                    val c = px[ny * w + nx]
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    n++
                }
                add(x - 1, y); add(x + 1, y); add(x, y - 1); add(x, y + 1)
                if (n > 0) {
                    px[idx] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun hasKnownNeighbour(idx: Int, w: Int, h: Int, known: BooleanArray): Boolean {
        val x = idx % w
        val y = idx / w
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
            if (known[ny * w + nx]) return true
        }
        return false
    }

    private fun averageKnown(idx: Int, w: Int, h: Int, known: BooleanArray, px: IntArray): Int {
        val x = idx % w
        val y = idx / w
        var r = 0; var g = 0; var b = 0; var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
            val ni = ny * w + nx
            if (!known[ni]) continue
            val c = px[ni]
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
            n++
        }
        return if (n == 0) px[idx]
        else (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    /** Square dilation done as two separable passes. */
    private fun grow(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        if (r <= 0) return mask.copyOf()
        val tmp = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!mask[y * w + x]) continue
                for (dx in -r..r) {
                    val nx = x + dx
                    if (nx in 0 until w) tmp[y * w + nx] = true
                }
            }
        }
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!tmp[y * w + x]) continue
                for (dy in -r..r) {
                    val ny = y + dy
                    if (ny in 0 until h) out[ny * w + x] = true
                }
            }
        }
        return out
    }
}
