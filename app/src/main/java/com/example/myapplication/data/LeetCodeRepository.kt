package com.example.myapplication.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.api.GraphQLRequest
import com.example.myapplication.api.LeetCodeApiClient
import com.example.myapplication.api.USER_STATS_QUERY
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "leetcode_prefs")

object PrefKeys {
    val USERNAME         = stringPreferencesKey("username")
    val CACHED_CALENDAR  = stringPreferencesKey("cached_calendar")
    val TOTAL_SOLVED     = intPreferencesKey("total_solved")
    val STREAK           = intPreferencesKey("streak")
    val ACTIVE_DAYS      = intPreferencesKey("active_days")
    val EASY_SOLVED      = intPreferencesKey("easy_solved")
    val MEDIUM_SOLVED    = intPreferencesKey("medium_solved")
    val HARD_SOLVED      = intPreferencesKey("hard_solved")
    val RANKING          = intPreferencesKey("ranking")
}

class LeetCodeRepository(private val context: Context) {

    suspend fun saveUsername(username: String) {
        context.dataStore.edit { it[PrefKeys.USERNAME] = username }
    }

    suspend fun getUsername(): String {
        return context.dataStore.data.first()[PrefKeys.USERNAME] ?: ""
    }

    suspend fun refreshData(username: String): Result<WidgetData> {
        return try {
            // Build a service with the username-specific Referer header
            val response = LeetCodeApiClient.buildService(username).getUserStats(
                GraphQLRequest(
                    query     = USER_STATS_QUERY,
                    variables = mapOf("username" to username)
                )
            )

            val user = response.data?.matchedUser
                ?: return Result.failure(Exception("User '$username' not found"))

            val stats          = user.submitStats
            val acSubmissions  = stats?.acSubmissionNum ?: emptyList()
            val allQuestions   = response.data.allQuestionsCount ?: emptyList()

            val totalSolved    = acSubmissions.find { it.difficulty == "All" }?.count ?: 0
            val easySolved     = acSubmissions.find { it.difficulty == "Easy" }?.count ?: 0
            val mediumSolved   = acSubmissions.find { it.difficulty == "Medium" }?.count ?: 0
            val hardSolved     = acSubmissions.find { it.difficulty == "Hard" }?.count ?: 0
            val ranking        = user.profile?.ranking ?: 0

            // Calculate acceptance rate
            val totalAcCount   = acSubmissions.find { it.difficulty == "All" }?.submissions?.toFloat() ?: 0f
            val totalSubCount  = stats?.totalSubmissionNum?.find { it.difficulty == "All" }?.submissions?.toFloat() ?: 0f
            val acceptanceRate = if (totalSubCount > 0) (totalAcCount / totalSubCount) * 100 else 0f

            // submissionCalendar is a raw JSON string directly on matchedUser
            val calendarJson   = user.submissionCalendar ?: "{}"

            // Parse active days and streak from calendar
            val calendarMap    = CalendarParser.parseCalendarJson(calendarJson)
            val activeDays     = calendarMap.values.count { it > 0 }
            val streak         = CalendarParser.calculateStreak(calendarMap)

            context.dataStore.edit { prefs ->
                prefs[PrefKeys.CACHED_CALENDAR] = calendarJson
                prefs[PrefKeys.TOTAL_SOLVED]    = totalSolved
                prefs[PrefKeys.STREAK]          = streak
                prefs[PrefKeys.ACTIVE_DAYS]     = activeDays
                prefs[PrefKeys.EASY_SOLVED]     = easySolved
                prefs[PrefKeys.MEDIUM_SOLVED]   = mediumSolved
                prefs[PrefKeys.HARD_SOLVED]     = hardSolved
                prefs[PrefKeys.RANKING]         = ranking
            }

            Result.success(
                WidgetData(
                    username     = username,
                    calendarJson = calendarJson,
                    totalSolved  = totalSolved,
                    streak       = streak,
                    activeDays   = activeDays,
                    easySolved   = easySolved,
                    mediumSolved = mediumSolved,
                    hardSolved   = hardSolved,
                    ranking      = ranking
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCachedData(): WidgetData? {
        val prefs    = context.dataStore.data.first()
        val username = prefs[PrefKeys.USERNAME] ?: return null
        val calendar = prefs[PrefKeys.CACHED_CALENDAR] ?: return null
        return WidgetData(
            username     = username,
            calendarJson = calendar,
            totalSolved  = prefs[PrefKeys.TOTAL_SOLVED]  ?: 0,
            streak       = prefs[PrefKeys.STREAK]        ?: 0,
            activeDays   = prefs[PrefKeys.ACTIVE_DAYS]   ?: 0,
            easySolved   = prefs[PrefKeys.EASY_SOLVED]   ?: 0,
            mediumSolved = prefs[PrefKeys.MEDIUM_SOLVED] ?: 0,
            hardSolved   = prefs[PrefKeys.HARD_SOLVED]   ?: 0,
            ranking      = prefs[PrefKeys.RANKING]       ?: 0
        )
    }
}

data class WidgetData(
    val username:     String,
    val calendarJson: String,
    val totalSolved:  Int,
    val streak:       Int,
    val activeDays:   Int,
    val easySolved:   Int,
    val mediumSolved: Int,
    val hardSolved:   Int,
    val ranking:      Int
)