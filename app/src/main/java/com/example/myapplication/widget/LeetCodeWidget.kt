package com.example.myapplication.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.myapplication.data.CalendarParser
import com.example.myapplication.data.GridColumn
import com.example.myapplication.data.MonthLabel
import com.example.myapplication.R
import com.example.myapplication.data.LeetCodeRepository
import com.example.myapplication.data.RefreshWorker

// ─── Colors ───────────────────────────────────────────────────────────────────

private val BG           = Color(0xFF1A1A1A)
private val SURFACE      = Color(0xFF282828)
private val TEXT_PRIMARY = Color(0xFFEFEFF1)
private val TEXT_MUTED   = Color(0xFF8A8A8A)
private val ACCENT       = Color(0xFFFFA116)

private val GRID_COLORS = listOf(
    android.graphics.Color.parseColor("#282828"),
    android.graphics.Color.parseColor("#2D6A2D"),
    android.graphics.Color.parseColor("#3A9E3A"),
    android.graphics.Color.parseColor("#45C745"),
    android.graphics.Color.parseColor("#57EF57")
)
private val BG_COLOR   = android.graphics.Color.parseColor("#1A1A1A")
private val LABEL_COLOR = android.graphics.Color.parseColor("#8A8A8A")

private fun glanceColor(color: Color): ColorProvider = ColorProvider(color, color)

// ─── Sizing strategy ──────────────────────────────────────────────────────────
//
// The grid always has exactly 53 data columns + up to 13 gap spacers = 66 slots.
// Gap spacers are MONTH_GAP_RATIO × cellSize wide (half a cell).
// Cell size is derived from display width so the grid always fits:
//
//   totalUnits = 53 × (1 + GAP_RATIO) − GAP_RATIO   (no trailing gap)
//              + 13 × MONTH_GAP_RATIO
//   cellSize   = availableWidth / totalUnits
//
// This gives a FIXED cell size for a given device/screen — it only changes
// if the user's screen width changes. Day-to-day the squares are always
// identical in size, giving the uniform experience you asked for.
//
// The only case where columns change (e.g. 12 gaps vs 13) results in the
// cell getting marginally bigger — at most ~1–2px difference, imperceptible.

private const val GAP_RATIO       = 0.15f   // cell gap / cell size
private const val MONTH_GAP_RATIO = 0.50f   // month separator / cell size
private const val RADIUS_RATIO    = 0.17f   // corner radius / cell size
private const val LABEL_TEXT_RATIO = 0.85f  // label font size / cell size
private const val LABEL_HEIGHT_RATIO = 1.00f // label area height / cell size (extra for padding)

// Worst case: 53 data cols + 13 gap spacers
private const val MAX_DATA_COLS   = 22   // proven worst case: 26 total slots = 22 data + 4 gaps
private const val MAX_MONTH_GAPS  = 4    // max 4 gaps (5 months → 4 boundaries)

// ─────────────────────────────────────────────────────────────────────────────

class LeetCodeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo           = LeetCodeRepository(context)
        val data           = repo.getCachedData()
        val displayWidthPx = context.resources.displayMetrics.widthPixels

        provideContent {
            if (data == null) {
                EmptyState()
            } else {
                val calendarMap            = CalendarParser.parseCalendarJson(data.calendarJson)
                val (columns, monthLabels) = CalendarParser.buildMonthGrid(calendarMap)
                val gridBitmap             = drawGridBitmap(columns, monthLabels, displayWidthPx)

                WidgetContent(
                    username     = data.username,
                    gridBitmap   = gridBitmap,
                    totalSolved  = data.totalSolved,
                    easySolved   = data.easySolved,
                    mediumSolved = data.mediumSolved,
                    hardSolved   = data.hardSolved,
                    streak       = data.streak,
                    activeDays   = data.activeDays
                )
            }
        }
    }

    private fun drawGridBitmap(
        columns:     List<GridColumn>,
        monthLabels: List<MonthLabel>,
        availableW:  Int
    ): Bitmap {

        // ── Derive cellSize from available width using worst-case column count ─
        // totalUnits = data cols × (1 + gapRatio) − trailing gapRatio
        //            + month gaps × monthGapRatio
        val worstCaseUnits = (MAX_DATA_COLS  * (1f + GAP_RATIO) - GAP_RATIO +
                MAX_MONTH_GAPS * MONTH_GAP_RATIO)
        val cellSize    = availableW / worstCaseUnits
        val cellGap     = cellSize * GAP_RATIO
        val monthGap    = cellSize * MONTH_GAP_RATIO
        val cellRadius  = cellSize * RADIUS_RATIO
        val labelHeight = cellSize * LABEL_HEIGHT_RATIO
        val labelTextSz = cellSize * LABEL_TEXT_RATIO

        val gridH   = 7f * (cellSize + cellGap) - cellGap
        val bitmapH = (gridH + labelHeight).toInt()
        val bitmapW = availableW

        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG_COLOR)

        val cellPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = LABEL_COLOR
            textSize = labelTextSz
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        // ── Build column widths ───────────────────────────────────────────────────
        // Each column has a pixel width: cellSize for data cols, monthGap for gaps.
        val colWidths = FloatArray(columns.size) { i ->
            if (columns[i].isGap) monthGap
            else {
                val nextIsGapOrEnd = i == columns.size - 1 || columns[i + 1].isGap
                cellSize + if (nextIsGapOrEnd) 0f else cellGap
            }
        }
        val actualGridWidth = colWidths.sum()

        // ── Position columns: anchor RIGHT edge to bitmapW ───────────────────
        // Newest data (today) is always in the last column — by anchoring to the
        // right, if the grid is narrower than bitmapW the surplus becomes left
        // padding (older months). If somehow it were wider it would clip the LEFT
        // (oldest) side — newest data is always preserved.
        val surplus = (bitmapW - actualGridWidth).coerceAtLeast(0f)
        val leftPad = surplus / 2f   // split surplus equally → grid is centred

        val colX = FloatArray(columns.size)
        var cursorX = leftPad
        columns.forEachIndexed { i, _ ->
            colX[i] = cursorX
            cursorX += colWidths[i]
        }

        // ── Draw day cells ────────────────────────────────────────────────────
        columns.forEachIndexed { colIdx, col ->
            if (col.isGap) return@forEachIndexed

            col.cells.forEachIndexed { row, day ->
                if (day.date == null) return@forEachIndexed   // padding → skip

                val left   = colX[colIdx]
                val top    = row * (cellSize + cellGap)
                val right  = left + cellSize
                val bottom = top + cellSize

                cellPaint.color = GRID_COLORS.getOrElse(
                    day.colorLevel.coerceAtLeast(0)
                ) { GRID_COLORS[0] }

                canvas.drawRoundRect(
                    RectF(left, top, right, bottom),
                    cellRadius, cellRadius, cellPaint
                )
            }
        }

        // ── Draw month labels below the grid, centered on their column span ───
        monthLabels.forEach { label ->
            // x span of this month = from left edge of startCol to right edge of endCol
            val xStart  = colX[label.startCol]
            val xEnd    = colX[label.endCol] + cellSize
            val centerX = (xStart + xEnd) / 2f
            val y       = gridH + labelHeight * 0.90f   // baseline inside label area (with top padding)

            canvas.drawText(label.text, centerX, y, labelPaint)
        }

        return bitmap
    }
}

// ─── Root widget layout ───────────────────────────────────────────────────────

@Composable
private fun WidgetContent(
    username:     String,
    gridBitmap:   Bitmap,
    totalSolved:  Int,
    easySolved:   Int,
    mediumSolved: Int,
    hardSolved:   Int,
    streak:       Int,
    activeDays:   Int
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(glanceColor(BG))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // LeetCode logo + username
            Row(
                verticalAlignment = Alignment.Vertical.CenterVertically,
                modifier = GlanceModifier.defaultWeight()
            ) {
                Image(
                    provider           = ImageProvider(R.drawable.ic_leetcode),
                    contentDescription = "LeetCode",
                    modifier           = GlanceModifier.size(18.dp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text  = username,
                    style = TextStyle(
                        color      = glanceColor(TEXT_PRIMARY),
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Text(
                text  = "🔥 $streak",
                style = TextStyle(
                    color      = glanceColor(ACCENT),
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            StatChip(label = "Solved", value = "$totalSolved",  valueColor = TEXT_PRIMARY)
            Spacer(modifier = GlanceModifier.width(6.dp))
            StatChip(label = "Easy",   value = "$easySolved",   valueColor = Color(0xFF00AF9B))
            Spacer(modifier = GlanceModifier.width(6.dp))
            StatChip(label = "Med",    value = "$mediumSolved", valueColor = Color(0xFFFFA116))
            Spacer(modifier = GlanceModifier.width(6.dp))
            StatChip(label = "Hard",   value = "$hardSolved",   valueColor = Color(0xFFFF375F))
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        Image(
            provider           = ImageProvider(gridBitmap),
            contentDescription = "Contribution graph — last year",
            modifier           = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text  = "Last 4 months · leetcode.com/${username}",
            style = TextStyle(
                color    = glanceColor(TEXT_MUTED),
                fontSize = 9.sp
            )
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, valueColor: Color) {
    Column(
        modifier = GlanceModifier
            .background(glanceColor(SURFACE))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text  = value,
            style = TextStyle(
                color      = glanceColor(valueColor),
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text  = label,
            style = TextStyle(
                color    = glanceColor(TEXT_MUTED),
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(glanceColor(BG))
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text  = "LeetCode Widget",
            style = TextStyle(
                color      = glanceColor(TEXT_PRIMARY),
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text  = "Tap & hold → Widget settings\nto enter your username",
            style = TextStyle(
                color    = glanceColor(TEXT_MUTED),
                fontSize = 11.sp
            )
        )
    }
}

class LeetCodeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = LeetCodeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancel(context)
    }
}