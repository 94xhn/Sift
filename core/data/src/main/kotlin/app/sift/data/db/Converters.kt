package app.sift.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** List<String> ↔ JSON 字符串，供 Room 持久化 keyPoints / tags。 */
class Converters {
    private val json = Json
    private val serializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromList(value: List<String>): String = json.encodeToString(serializer, value)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isBlank()) emptyList() else json.decodeFromString(serializer, value)
}
