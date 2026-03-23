package com.example.myapplication.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneOffset

data class DayCell(
    val date: LocalDate?,
    val count: Int,
    val colorLevel: Int     // -1 = not drawn, 0–4 = intensity
)

data class GridColumn(
    val cells: List<DayCell>,
    val isGap: Boolean = false
)

data class MonthLabel(
    val text: String,
    val startCol: Int,
    val endCol: Int
)

object CalendarParser {

    fun countToLevel(count: Int): Int = when {
        count <= 0  -> 0
        count <= 3  -> 1
        count <= 7  -> 2
        count <= 15 -> 3
        else        -> 4
    }

    fun parseCalendarJson(json: String): Map<LocalDate, Int> {
        if (json.isBlank()) return emptyMap()
        return try {
            Json.parseToJsonElement(json).jsonObject.entries.associate { (tsStr, countEl) ->
                val date = LocalDate.ofEpochDay(tsStr.trim().toLong() / 86400)
                date to countEl.jsonPrimitive.int
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun calculateStreak(calendarMap: Map<LocalDate, Int>): Int {
        val today = LocalDate.now(ZoneOffset.UTC)
        var streak = 0
        var day = today
        while (true) {
            if ((calendarMap[day] ?: 0) > 0) {
                streak++
                day = day.minusDays(1)
            } else {
                if (day == today) day = day.minusDays(1) else break
            }
        }
        return streak
    }

    /**
     * Build a ~4-month grid ending on today, matching LeetCode's rendering:
     *
     * Key insight: we DON'T use "owner month" voting. Instead:
     *  - Each week column is split at month boundaries.
     *  - If a week contains days from two months (e.g. Nov 30 + Dec 1–6),
     *    it becomes TWO columns separated by a gap:
     *      Col A: only Nov 30 in row 0 (Sun), rows 1–6 empty
     *      gap
     *      Col B: Dec 1–6 in rows 1–6, row 0 empty
     *  - This way every cell is either drawn (its own month) or blank (empty space).
     *  - No day is ever suppressed due to majority voting.
     */
    fun buildMonthGrid(calendarMap: Map<LocalDate, Int>): Pair<List<GridColumn>, List<MonthLabel>> {
        val today    = LocalDate.now(ZoneOffset.UTC)
        val startRaw = today.minusDays(119)  // 120 days inclusive of today

        // Snap to Sunday on or before startRaw
        val startDate = startRaw.minusDays((startRaw.dayOfWeek.value % 7).toLong())

        val finalColumns = mutableListOf<GridColumn>()

        var weekStart = startDate
        var prevColMonth: Int? = null   // owner month of the last added non-gap column
        var prevColYear:  Int? = null

        while (!weekStart.isAfter(today)) {
            // Build all 7 cells for this raw week
            val allCells = (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                when {
                    date.isBefore(startRaw) || date.isAfter(today) ->
                        DayCell(null, -1, -1)
                    else -> {
                        val count = calendarMap[date] ?: 0
                        DayCell(date, count, countToLevel(count))
                    }
                }
            }

            // Find the distinct months present in REAL (non-null) cells
            val months = allCells
                .filter { it.date != null }
                .map { it.date!!.monthValue to it.date.year }
                .distinct()
                .sortedWith(compareBy({ it.second }, { it.first }))

            if (months.isEmpty()) {
                // Entire column is padding — skip it
                weekStart = weekStart.plusWeeks(1)
                continue
            }

            if (months.size == 1) {
                // ── Simple case: all real days belong to one month ────────────
                val (m, y) = months[0]

                // Insert gap if month changed
                if (prevColMonth != null && (prevColMonth != m || prevColYear != y)) {
                    finalColumns.add(GridColumn(List(7) { DayCell(null,-1,-1) }, isGap = true))
                }

                finalColumns.add(GridColumn(cells = allCells, isGap = false))
                prevColMonth = m
                prevColYear  = y

            } else {
                // ── Split case: week spans two months ─────────────────────────
                // Column A: only cells belonging to months[0], rest blank
                // gap
                // Column B: only cells belonging to months[1], rest blank
                val (m1, y1) = months[0]
                val (m2, y2) = months[1]

                val colA = allCells.map { cell ->
                    if (cell.date != null &&
                        (cell.date.monthValue != m1 || cell.date.year != y1))
                        DayCell(null, -1, -1)
                    else cell
                }
                val colB = allCells.map { cell ->
                    if (cell.date != null &&
                        (cell.date.monthValue != m2 || cell.date.year != y2))
                        DayCell(null, -1, -1)
                    else cell
                }

                // Gap before colA if month changed from previous column
                if (prevColMonth != null && (prevColMonth != m1 || prevColYear != y1)) {
                    finalColumns.add(GridColumn(List(7) { DayCell(null,-1,-1) }, isGap = true))
                }

                finalColumns.add(GridColumn(cells = colA, isGap = false))

                // Gap between colA and colB (always — different months)
                finalColumns.add(GridColumn(List(7) { DayCell(null,-1,-1) }, isGap = true))

                finalColumns.add(GridColumn(cells = colB, isGap = false))
                prevColMonth = m2
                prevColYear  = y2
            }

            weekStart = weekStart.plusWeeks(1)
        }

        // ── Compute month label spans ─────────────────────────────────────────
        // Walk finalColumns, track which col indices each month occupies
        data class MonthSpan(val month: Int, val year: Int, var startCol: Int, var endCol: Int)
        val spans = mutableListOf<MonthSpan>()
        var realColCount = 0  // count only non-gap columns for span tracking

        finalColumns.forEachIndexed { colIdx, col ->
            if (col.isGap) return@forEachIndexed
            // Determine this column's month from its first real cell
            val date = col.cells.firstOrNull { it.date != null }?.date
                ?: run { realColCount++; return@forEachIndexed }
            val m = date.monthValue
            val y = date.year
            val last = spans.lastOrNull()
            if (last != null && last.month == m && last.year == y) {
                last.endCol = colIdx
            } else {
                spans.add(MonthSpan(m, y, colIdx, colIdx))
            }
            realColCount++
        }

        val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec")
        val monthLabels = spans
            .filter { it.endCol - it.startCol + 1 >= 2 }
            .map { span ->
                MonthLabel(
                    text     = monthNames[span.month - 1],
                    startCol = span.startCol,
                    endCol   = span.endCol
                )
            }

        return Pair(finalColumns, monthLabels)
    }
}