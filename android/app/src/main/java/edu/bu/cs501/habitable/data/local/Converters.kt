package edu.bu.cs501.habitable.data.local

import edu.bu.cs501.habitable.domain.model.ReadingFlag

/**
 * Small helpers for the string-encoded columns. Kept outside Room's
 * @TypeConverter machinery so they are trivially unit-testable.
 */
object Converters {

    fun flagsToString(flags: Set<ReadingFlag>): String =
        flags.sortedBy { it.ordinal }.joinToString(",") { it.name }

    fun stringToFlags(value: String): Set<ReadingFlag> =
        if (value.isBlank()) emptySet()
        else value.split(',').mapNotNull { name ->
            runCatching { ReadingFlag.valueOf(name.trim()) }.getOrNull()
        }.toSet()

    inline fun <reified E : Enum<E>> enumOrDefault(value: String, default: E): E =
        runCatching { enumValueOf<E>(value) }.getOrDefault(default)
}
