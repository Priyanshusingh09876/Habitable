package edu.bu.cs501.habitable.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import edu.bu.cs501.habitable.data.photos.PhotoStore
import edu.bu.cs501.habitable.domain.usecase.ChartData
import edu.bu.cs501.habitable.domain.usecase.EvidenceReport
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lays an [EvidenceReport] out as a PDF with Android's built-in PdfDocument
 * (backlog US-7). No library and no network: the packet is made on the phone.
 *
 * Every word and number comes from the report, which is built and unit-tested
 * in pure Kotlin; this class only decides where things go on the page. Pages
 * are US Letter (612 x 792 pt). Call off the main thread.
 */
class PdfEvidenceWriter(private val zone: ZoneId = ZoneId.of("America/New_York")) {

    /**
     * Writes the packet to [out]. [photoFile] resolves a photo's file name to the
     * file on disk (null or missing files are listed without a thumbnail).
     */
    fun write(report: EvidenceReport, out: File, photoFile: (String) -> File?) {
        val doc = PdfDocument()
        val page = Pager(doc)
        try {
            page.title(report.title)
            page.small(report.generatedLine)
            page.gap(10f)
            report.facts.forEach { page.fact(it.label, it.value) }
            if (report.description.isNotBlank()) {
                page.gap(6f)
                page.body(report.description)
            }

            if (report.rule.isNotBlank()) {
                page.heading("Legal standard")
                page.body(report.rule)
            }
            if (report.summary.isNotEmpty()) {
                page.heading("Summary")
                report.summary.forEach { page.fact(it.label, it.value) }
            }
            report.chart?.let { chart ->
                page.heading("Temperature record")
                page.chart(chart, zone)
                page.small(report.chartCaption)
            }

            page.heading("Timeline (${report.timeline.size})")
            if (report.timeline.isEmpty()) page.body("No events recorded yet.")
            report.timeline.forEach { row ->
                page.fact(row.time, row.label + if (row.note.isBlank()) "" else ": ${row.note}", labelWidth = 150f)
            }

            if (report.photos.isNotEmpty()) {
                page.heading("Photos (${report.photos.size})")
                report.photos.forEach { photo ->
                    val bitmap = photoFile(photo.fileName)?.let { PhotoStore.decodeScaled(it, 480) }
                    page.photo(photo, bitmap)
                    bitmap?.recycle()
                }
            }

            if (report.dataNotes.isNotEmpty()) {
                page.heading("About this data")
                report.dataNotes.forEach { page.body("•  $it") }
            }

            page.finish(report.disclaimer)
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Keeps a cursor down the page and starts a new page when the next block will not fit. */
    private class Pager(private val doc: PdfDocument) {
        private var pageNumber = 0
        private var current: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = 0f
        private var footer = ""

        private val titlePaint = text(20f, NAVY, bold = true)
        private val headingPaint = text(13f, EMBER, bold = true)
        private val bodyPaint = text(10.5f, INK)
        private val labelPaint = text(10.5f, MUTED, bold = true)
        private val smallPaint = text(8.5f, MUTED)
        private val monoPaint = text(8f, INK).apply { typeface = Typeface.MONOSPACE }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE; strokeWidth = 0.7f; style = Paint.Style.STROKE }

        init { newPage() }

        private fun newPage() {
            current?.let { closePage(it) }
            pageNumber++
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            current = page
            canvas = page.canvas
            y = MARGIN
        }

        private fun closePage(page: PdfDocument.Page) {
            canvas.drawLine(MARGIN, PAGE_H - MARGIN + 6f, PAGE_W - MARGIN, PAGE_H - MARGIN + 6f, linePaint)
            canvas.drawText(footer.ifBlank { EvidenceReport.DISCLAIMER }, MARGIN, PAGE_H - MARGIN + 20f, smallPaint)
            val label = "Page $pageNumber"
            canvas.drawText(label, PAGE_W - MARGIN - smallPaint.measureText(label), PAGE_H - MARGIN + 20f, smallPaint)
            doc.finishPage(page)
        }

        /** Start a new page unless [needed] points still fit above the footer. */
        private fun ensure(needed: Float) {
            if (y + needed > PAGE_H - MARGIN) newPage()
        }

        fun gap(points: Float) { y += points }

        fun title(text: String) {
            wrap(text, titlePaint, CONTENT_W).forEach { line ->
                ensure(26f)
                y += 22f
                canvas.drawText(line, MARGIN, y, titlePaint)
            }
            y += 4f
        }

        fun heading(text: String) {
            ensure(60f) // never leave a heading alone at the bottom of a page
            y += 20f
            canvas.drawText(text.uppercase(Locale.US), MARGIN, y, headingPaint)
            y += 5f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
            y += 6f
        }

        fun body(text: String) = paragraph(text, bodyPaint, MARGIN, CONTENT_W, 14f)

        fun small(text: String) = paragraph(text, smallPaint, MARGIN, CONTENT_W, 11.5f)

        /** A label in the left column with a wrapped value beside it. */
        fun fact(label: String, value: String, labelWidth: Float = 130f) {
            val lines = wrap(value, bodyPaint, CONTENT_W - labelWidth)
            ensure(14f * maxOf(1, lines.size))
            val top = y
            canvas.drawText(label, MARGIN, top + 11f, labelPaint)
            lines.forEachIndexed { i, line -> canvas.drawText(line, MARGIN + labelWidth, top + 11f + i * 14f, bodyPaint) }
            y = top + 14f * maxOf(1, lines.size) + 2f
        }

        fun photo(row: EvidenceReport.PhotoRow, bitmap: Bitmap?) {
            val thumbW = 150f
            val thumbH = 112f
            ensure(thumbH + 12f)
            val top = y + 4f
            val frame = RectF(MARGIN, top, MARGIN + thumbW, top + thumbH)
            if (bitmap != null) {
                // Fit inside the frame, keeping the photo's shape.
                val scale = minOf(thumbW / bitmap.width, thumbH / bitmap.height)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                val left = frame.left + (thumbW - w) / 2f
                val topIn = frame.top + (thumbH - h) / 2f
                canvas.drawBitmap(bitmap, null, RectF(left, topIn, left + w, topIn + h), Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                canvas.drawText("Photo file not found", frame.left + 8f, frame.centerY(), smallPaint)
            }
            canvas.drawRect(frame, linePaint)

            val textX = MARGIN + thumbW + 14f
            var ty = top + 12f
            canvas.drawText("Taken ${row.taken}", textX, ty, bodyPaint); ty += 16f
            canvas.drawText("SHA-256 fingerprint", textX, ty, labelPaint); ty += 12f
            // 64 hex characters in two lines of 32, so they can be compared by eye.
            row.sha256.chunked(32).forEach { canvas.drawText(it, textX, ty, monoPaint); ty += 11f }
            ty += 5f
            wrap(row.anchored, smallPaint, CONTENT_W - thumbW - 14f).forEach { canvas.drawText(it, textX, ty, smallPaint); ty += 11f }
            canvas.drawText(row.fileName, textX, ty, smallPaint)
            y = top + thumbH + 10f
        }

        /** Indoor line, stepped legal limit, dashed outdoor line and shading where indoor is below the limit. */
        fun chart(data: ChartData, zone: ZoneId) {
            val height = 190f
            ensure(height + 48f)
            val left = MARGIN + 30f
            val top = y + 6f
            val right = PAGE_W - MARGIN
            val bottom = top + height
            val span = (data.toMillis - data.fromMillis).coerceAtLeast(1L).toFloat()
            val range = (data.maxF - data.minF).coerceAtLeast(1.0)
            fun x(t: Long) = left + (t - data.fromMillis) / span * (right - left)
            fun yOf(v: Double) = (top + (data.maxF - v) / range * (bottom - top)).toFloat()

            // Grid every 10°F with labels.
            val grid = Paint(linePaint).apply { pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f) }
            var tick = (Math.ceil(data.minF / 10.0) * 10).toInt()
            while (tick <= data.maxF) {
                val gy = yOf(tick.toDouble())
                canvas.drawLine(left, gy, right, gy, grid)
                canvas.drawText("$tick°", MARGIN, gy + 3f, smallPaint)
                tick += 10
            }
            canvas.drawRect(left, top, right, bottom, linePaint)

            // The limit in force at time t: the last limit point at or before it.
            fun limitAt(t: Long): Double? = data.limit.lastOrNull { it.timeMillis <= t }?.valueF ?: data.limit.firstOrNull()?.valueF

            // Shade between the limit and the indoor line, only inside the cold spans.
            val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SHADE; style = Paint.Style.FILL }
            data.violationSpans.forEach { (start, end) ->
                val inside = data.indoor.filter { it.timeMillis in start..end }
                if (inside.size >= 2) {
                    val path = Path()
                    inside.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.timeMillis), yOf(p.valueF)) else path.lineTo(x(p.timeMillis), yOf(p.valueF)) }
                    inside.asReversed().forEach { p -> limitAt(p.timeMillis)?.let { path.lineTo(x(p.timeMillis), yOf(it)) } }
                    path.close()
                    canvas.drawPath(path, shade)
                }
            }

            fun line(points: List<ChartData.Pt>, paint: Paint) {
                if (points.size < 2) return
                val path = Path()
                points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.timeMillis), yOf(p.valueF)) else path.lineTo(x(p.timeMillis), yOf(p.valueF)) }
                canvas.save()
                canvas.clipRect(left, top, right, bottom)
                canvas.drawPath(path, paint)
                canvas.restore()
            }
            line(data.outdoor, stroke(COLD, 1.2f).apply { pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f) })
            line(data.limit, stroke(EMBER, 1.6f))
            line(data.indoor, stroke(NAVY, 2f))

            // Time labels: start, middle, end.
            val fmt = DateTimeFormatter.ofPattern("MMM d, h a", Locale.US).withZone(zone)
            val mid = data.fromMillis + (data.toMillis - data.fromMillis) / 2
            listOf(data.fromMillis to left, mid to (left + right) / 2f, data.toMillis to right).forEachIndexed { i, (t, px) ->
                val label = fmt.format(Instant.ofEpochMilli(t))
                val w = smallPaint.measureText(label)
                val lx = when (i) { 0 -> px; 1 -> px - w / 2f; else -> px - w }
                canvas.drawText(label, lx, bottom + 12f, smallPaint)
            }

            // Legend.
            var lx = left
            val ly = bottom + 26f
            listOf("Indoor" to NAVY, "Legal limit" to EMBER, "Outdoor" to COLD, "Below the limit" to SHADE).forEach { (name, color) ->
                canvas.drawRect(lx, ly - 7f, lx + 10f, ly + 1f, Paint().apply { this.color = color })
                canvas.drawText(name, lx + 14f, ly, smallPaint)
                lx += 24f + smallPaint.measureText(name) + 10f
            }
            y = ly + 8f
        }

        fun finish(disclaimer: String) {
            footer = disclaimer
            current?.let { closePage(it) }
            current = null
        }

        private fun paragraph(text: String, paint: Paint, x: Float, width: Float, lineHeight: Float) {
            wrap(text, paint, width).forEach { line ->
                ensure(lineHeight)
                y += lineHeight
                canvas.drawText(line, x, y - 3f, paint)
            }
            y += 2f
        }
    }

    companion object {
        const val PAGE_W = 612
        const val PAGE_H = 792
        const val MARGIN = 48f
        const val CONTENT_W = PAGE_W - 2 * MARGIN

        private val NAVY = Color.rgb(15, 27, 45)
        private val EMBER = Color.rgb(184, 67, 31)
        private val COLD = Color.rgb(58, 134, 200)
        private val INK = Color.rgb(20, 27, 38)
        private val MUTED = Color.rgb(74, 85, 104)
        private val LINE = Color.rgb(203, 211, 222)
        private val SHADE = Color.argb(70, 228, 87, 46)

        private fun text(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        }

        private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = width
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }

        /**
         * Greedy word wrap: as many words per line as fit in [width]. A single word
         * wider than the line (a long address or URL) is split by characters.
         */
        fun wrap(text: String, paint: Paint, width: Float): List<String> {
            val lines = ArrayList<String>()
            text.split('\n').forEach { paragraph ->
                var line = StringBuilder()
                paragraph.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                    var w = word
                    val candidate = if (line.isEmpty()) w else "$line $w"
                    if (paint.measureText(candidate) <= width) {
                        line = StringBuilder(candidate)
                    } else {
                        if (line.isNotEmpty()) { lines += line.toString(); line = StringBuilder() }
                        while (paint.measureText(w) > width && w.length > 1) {
                            val n = paint.breakText(w, true, width, null).coerceAtLeast(1)
                            lines += w.substring(0, n)
                            w = w.substring(n)
                        }
                        line = StringBuilder(w)
                    }
                }
                lines += line.toString()
            }
            return lines
        }
    }
}
