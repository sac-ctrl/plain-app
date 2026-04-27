package com.ismartcoding.plain.ui.page.home.games

import android.content.Context
import com.ismartcoding.plain.preferences.GamesStateJsonPreference
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.json.JSONArray

data class GameRecord(
    var best: Int = 0,
    var plays: Int = 0,
    var totalScore: Long = 0L,
    var lastPlayedAt: Long = 0L,
)

data class DailyChallenge(
    val id: String,
    val gameId: String,
    val description: String,
    val target: Int,
    val rewardCoins: Int,
    var done: Boolean = false,
    var progress: Int = 0,
)

object GamesStore {
    private val records = mutableMapOf<String, GameRecord>()
    private var coins: Int = 0
    private var streak: Int = 0
    private var lastDailyKey: String = ""
    private var dailies: MutableList<DailyChallenge> = mutableListOf()

    val state = MutableStateFlow(0L)
    private fun bump() { state.value = System.currentTimeMillis() }

    fun loadFromJson(json: String) {
        records.clear(); dailies.clear()
        coins = 0; streak = 0; lastDailyKey = ""
        if (json.isBlank() || json == "{}") { ensureDailies(); return }
        try {
            val o = JSONObject(json)
            coins = o.optInt("coins", 0)
            streak = o.optInt("streak", 0)
            lastDailyKey = o.optString("lastDailyKey", "")
            val recs = o.optJSONObject("records") ?: JSONObject()
            recs.keys().forEach { id ->
                val r = recs.getJSONObject(id)
                records[id] = GameRecord(
                    r.optInt("best"), r.optInt("plays"),
                    r.optLong("totalScore"), r.optLong("lastPlayedAt"))
            }
            val dl = o.optJSONArray("dailies") ?: JSONArray()
            for (i in 0 until dl.length()) {
                val d = dl.getJSONObject(i)
                dailies.add(DailyChallenge(
                    d.getString("id"), d.getString("gameId"),
                    d.getString("description"), d.getInt("target"),
                    d.getInt("rewardCoins"),
                    d.optBoolean("done", false),
                    d.optInt("progress", 0)
                ))
            }
        } catch (_: Exception) {}
        ensureDailies()
    }

    fun toJson(): String {
        val o = JSONObject()
        o.put("coins", coins)
        o.put("streak", streak)
        o.put("lastDailyKey", lastDailyKey)
        val recs = JSONObject()
        records.forEach { (id, r) ->
            recs.put(id, JSONObject().apply {
                put("best", r.best); put("plays", r.plays)
                put("totalScore", r.totalScore); put("lastPlayedAt", r.lastPlayedAt)
            })
        }
        o.put("records", recs)
        val dl = JSONArray()
        dailies.forEach { d ->
            dl.put(JSONObject().apply {
                put("id", d.id); put("gameId", d.gameId)
                put("description", d.description); put("target", d.target)
                put("rewardCoins", d.rewardCoins); put("done", d.done)
                put("progress", d.progress)
            })
        }
        o.put("dailies", dl)
        return o.toString()
    }

    suspend fun persist(context: Context) {
        GamesStateJsonPreference.putAsync(context, toJson())
    }

    fun record(id: String): GameRecord = records.getOrPut(id) { GameRecord() }

    fun getCoins(): Int = coins
    fun getStreak(): Int = streak
    fun getDailies(): List<DailyChallenge> = dailies.toList()

    fun lastPlayedId(): String? = records.entries
        .filter { it.value.lastPlayedAt > 0 }
        .maxByOrNull { it.value.lastPlayedAt }?.key

    fun finishRun(gameId: String, score: Int) {
        val r = record(gameId)
        r.plays += 1
        r.totalScore += score
        r.lastPlayedAt = System.currentTimeMillis()
        if (score > r.best) r.best = score
        coins += (score / 50).coerceAtLeast(1)
        streak += 1
        dailies.forEach { d ->
            if (d.done) return@forEach
            if (d.gameId == gameId || d.gameId == "any") {
                if (d.description.startsWith("Score")) {
                    if (score >= d.target) { d.done = true; coins += d.rewardCoins }
                } else {
                    d.progress += 1
                    if (d.progress >= d.target) { d.done = true; coins += d.rewardCoins }
                }
            }
        }
        bump()
    }

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    private fun ensureDailies() {
        val today = todayKey()
        if (lastDailyKey == today && dailies.isNotEmpty()) return
        lastDailyKey = today
        dailies.clear()
        val pool = listOf(
            DailyChallenge("d1", "any", "Play 3 different games today", 3, 30),
            DailyChallenge("d2", "snake", "Score 100 on Neon Snake", 100, 25),
            DailyChallenge("d3", "react", "Score 250 on Reaction (lower ms is better)", 250, 20),
            DailyChallenge("d4", "g2048", "Score 1500 on 2048", 1500, 30),
        )
        dailies.addAll(pool)
    }

    fun fakeLeaderboard(yourScore: Int): List<Pair<String, Int>> {
        val names = listOf("Alex", "Maya", "Kenji", "Priya", "Leo", "Zoe", "Aki", "Noor", "Sam", "Rin")
        val seed = yourScore.coerceAtLeast(10)
        val list = names.mapIndexed { i, n ->
            n to (seed * (1.4 - i * 0.08)).toInt().coerceAtLeast(1)
        }.toMutableList()
        list.add("You" to yourScore)
        return list.sortedByDescending { it.second }.take(10)
    }
}
