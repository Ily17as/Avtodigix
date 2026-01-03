package com.example.avtodigix.storage

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class ScanSnapshotConverters {
    @TypeConverter
    fun mapToString(map: Map<String, Double>): String {
        val jsonObject = JSONObject()
        map.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    @TypeConverter
    fun stringToMap(value: String): Map<String, Double> {
        if (value.isBlank()) {
            return emptyMap()
        }
        val jsonObject = JSONObject(value)
        val iterator = jsonObject.keys()
        val map = mutableMapOf<String, Double>()
        while (iterator.hasNext()) {
            val key = iterator.next()
            map[key] = jsonObject.optDouble(key)
        }
        return map
    }

    @TypeConverter
    fun listToString(values: List<String>): String {
        val jsonArray = JSONArray()
        values.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun stringToList(value: String): List<String> {
        if (value.isBlank()) {
            return emptyList()
        }
        val jsonArray = JSONArray(value)
        val list = mutableListOf<String>()
        for (index in 0 until jsonArray.length()) {
            list.add(jsonArray.optString(index))
        }
        return list
    }

    @TypeConverter
    fun formatToString(format: WifiResponseFormat): String {
        return format.name
    }

    @TypeConverter
    fun stringToFormat(value: String): WifiResponseFormat {
        return runCatching { WifiResponseFormat.valueOf(value) }
            .getOrDefault(WifiResponseFormat.Text)
    }
}
