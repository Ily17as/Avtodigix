package com.example.avtodigix.storage

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class ScanSnapshotConverters {
    @TypeConverter
    fun mapToString(map: Map<String, Double>?): String {
        if (map == null) return "{}"
        val jsonObject = JSONObject()
        map.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    @TypeConverter
    fun stringToMap(value: String?): Map<String, Double> {
        if (value.isNullOrBlank()) {
            return emptyMap()
        }
        return try {
            val jsonObject = JSONObject(value)
            val iterator = jsonObject.keys()
            val map = mutableMapOf<String, Double>()
            while (iterator.hasNext()) {
                val key = iterator.next()
                map[key] = jsonObject.optDouble(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun listToString(values: List<String>?): String {
        if (values == null) return "[]"
        val jsonArray = JSONArray()
        values.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun stringToList(value: String?): List<String> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val jsonArray = JSONArray(value)
            val list = mutableListOf<String>()
            for (index in 0 until jsonArray.length()) {
                list.add(jsonArray.optString(index))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun formatToString(format: WifiResponseFormat?): String {
        return format?.name ?: WifiResponseFormat.Text.name
    }

    @TypeConverter
    fun stringToFormat(value: String?): WifiResponseFormat {
        if (value == null) return WifiResponseFormat.Text
        return runCatching { WifiResponseFormat.valueOf(value) }
            .getOrDefault(WifiResponseFormat.Text)
    }
}
