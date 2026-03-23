@file:OptIn(kotlinx.serialization.InternalSerializationApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)

package com.example.myapplication.api

import kotlinx.serialization.Serializable

// ─── Request ──────────────────────────────────────────────────────────────────

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, String>
)

// ─── Response wrapper ─────────────────────────────────────────────────────────

@Serializable
data class GraphQLResponse(
    val data: ResponseData? = null
)

@Serializable
data class ResponseData(
    val allQuestionsCount: List<QuestionCount>? = null,
    val matchedUser: MatchedUser? = null
)

@Serializable
data class QuestionCount(
    val difficulty: String,
    val count: Int
)

@Serializable
data class MatchedUser(
    val username: String? = null,
    val submissionCalendar: String? = null,   // raw JSON string — lives here, not in userCalendar
    val submitStats: SubmitStats? = null,
    val profile: Profile? = null,
    val contributions: Contributions? = null
)

@Serializable
data class SubmitStats(
    val acSubmissionNum: List<SubmissionCount> = emptyList(),
    val totalSubmissionNum: List<SubmissionCount> = emptyList()
)

@Serializable
data class SubmissionCount(
    val difficulty: String,
    val count: Int,
    val submissions: Int
)

@Serializable
data class Profile(
    val ranking: Int = 0,
    val reputation: Int = 0
)

@Serializable
data class Contributions(
    val points: Int = 0
)

// ─── GraphQL query — mirrors your Spring Boot backend exactly ─────────────────

const val USER_STATS_QUERY = """
    query getUserProfile(${'$'}username: String!) {
      allQuestionsCount {
        difficulty
        count
      }
      matchedUser(username: ${'$'}username) {
        submissionCalendar
        contributions {
          points
        }
        profile {
          reputation
          ranking
        }
        submitStats {
          acSubmissionNum {
            difficulty
            count
            submissions
          }
          totalSubmissionNum {
            difficulty
            count
            submissions
          }
        }
      }
    }
"""