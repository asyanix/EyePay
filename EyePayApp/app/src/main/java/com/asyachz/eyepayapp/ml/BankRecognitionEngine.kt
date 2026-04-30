package com.asyachz.eyepayapp.ml

import android.content.Context
import org.json.JSONObject
import java.util.LinkedList

class BankRecognitionEngine(context: Context) {
    private val banksConfig: Map<String, List<String>> = loadConfig(context)
    private val history = LinkedList<Pair<Long, String>>()

    private val timeWindowMs = 5000L
    private val majorityThreshold = 3
    var lockedBankName: String? = null
        private set
    val unknownBankFallback = "Неизвестный банк"

    private fun loadConfig(context: Context): Map<String, List<String>> {
        val jsonString = context.assets.open("banks_config.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val map = mutableMapOf<String, List<String>>()

        for (key in jsonObject.keys()) {
            val aliasesArray = jsonObject.getJSONArray(key)
            val aliases = List(aliasesArray.length()) { aliasesArray.getString(it).lowercase() }
            map[key] = aliases
        }
        return map
    }

    fun processOcrText(rawText: String): String {
        lockedBankName?.let { return it }
        val words = rawText.lowercase().split(Regex("\\s+"))
        var detectedBank: String? = null

        for (word in words) {
            val cleanWord = word.replace(Regex("[^a-zа-я0-9]"), "")
            if (cleanWord.isEmpty()) continue

            detectedBank = findBankForWord(cleanWord)
            if (detectedBank != null) break
        }

        return updateBufferAndGetResult(detectedBank)
    }

    fun reset() {
        lockedBankName = null
        history.clear()
    }

    private fun findBankForWord(word: String): String? {
        for ((bankName, aliases) in banksConfig) {
            for (alias in aliases) {
                if (alias.length == 1) {
                    if (word == alias) return bankName
                } else {
                    val threshold = if (alias.length <= 4) 1 else 2
                    if (levenshtein(word, alias) <= threshold) {
                        return bankName
                    }
                }
            }
        }
        return null
    }

    private fun updateBufferAndGetResult(newDetection: String?): String {
        val currentTime = System.currentTimeMillis()

        if (newDetection != null) {
            history.add(Pair(currentTime, newDetection))
        }

        history.removeAll { (time, _) -> currentTime - time > timeWindowMs }

//        while (history.isNotEmpty() && (currentTime - history.first().first) > timeWindowMs) {
//            history.removeFirst()
//        }

        if (history.isEmpty()) return unknownBankFallback

        val counts = history.groupingBy { it.second }.eachCount()
        val (bestBank, count) = counts.maxByOrNull { it.value } ?: return unknownBankFallback

        if (count >= majorityThreshold) {
            lockedBankName = bestBank
            return bestBank
        }

        return unknownBankFallback
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        if (lhs == rhs) return 0
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length

        val lhsLength = lhs.length + 1
        val rhsLength = rhs.length + 1

        var cost = IntArray(lhsLength) { it }
        var newCost = IntArray(lhsLength)

        for (i in 1 until rhsLength) {
            newCost[0] = i
            for (j in 1 until lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength - 1]
    }
}