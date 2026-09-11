package com.shankaravam.festival.data.local

import androidx.room.TypeConverter

/**
 * Tags are joined with the ASCII unit separator (char 31), so commas,
 * spaces and Telugu text inside a tag pass through safely.
 */
class Converters {
    @TypeConverter
    fun tagsToString(tags: List<String>): String = tags.joinToString(SEPARATOR)

    @TypeConverter
    fun stringToTags(raw: String): List<String> =
        if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR)

    companion object {
        private val SEPARATOR = String(charArrayOf(31.toChar()))
    }
}
