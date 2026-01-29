package com.homedashboard.app.handwriting

import java.time.LocalDate
import java.time.LocalTime
import java.util.regex.Pattern

/**
 * Parses natural language text to extract event details.
 * Handles inputs like:
 * - "Soccer 3pm"
 * - "dinner 7pm Hotel Marquis"
 * - "Meeting at 2:30 PM with John"
 * - "Doctor appointment 10am"
 * - "Call mom 4:30"
 */
class NaturalLanguageParser {

    companion object {
        // Time patterns (order matters - more specific patterns first)
        private val TIME_PATTERNS = listOf(
            // 12-hour with minutes: "2:30pm", "2:30 pm", "2:30PM"
            Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(am|pm|AM|PM)", Pattern.CASE_INSENSITIVE),
            // 12-hour without minutes: "3pm", "3 pm", "3PM"
            Pattern.compile("(\\d{1,2})\\s*(am|pm|AM|PM)", Pattern.CASE_INSENSITIVE),
            // 24-hour: "14:30", "9:00"
            Pattern.compile("(\\d{1,2}):(\\d{2})(?!\\d)"),
            // "at X o'clock" or "at X"
            Pattern.compile("at\\s+(\\d{1,2})(?:\\s*o'?clock)?", Pattern.CASE_INSENSITIVE),
        )

        // Location indicators
        private val LOCATION_PREPOSITIONS = listOf("at", "in", "@", "location:")

        // Words that typically indicate the event title is complete
        private val TITLE_TERMINATORS = setOf("at", "in", "@", "with", "from", "to", "until")
    }

    /**
     * Parse natural language text and extract event details.
     */
    fun parse(text: String, targetDate: LocalDate): ParsedEvent {
        val cleanText = text.trim()

        // Extract time
        val timeResult = extractTime(cleanText)
        val textWithoutTime = timeResult.remainingText

        // Extract location (after time extraction)
        val locationResult = extractLocation(textWithoutTime)
        val textWithoutLocation = locationResult.remainingText

        // What remains is the title
        val title = cleanTitle(textWithoutLocation)

        return ParsedEvent(
            title = title.ifBlank { cleanText }, // Fallback to original text if parsing failed
            date = targetDate,
            startTime = timeResult.time,
            endTime = timeResult.time?.plusHours(1), // Default 1 hour duration
            location = locationResult.location,
            isAllDay = timeResult.time == null,
            originalText = cleanText
        )
    }

    /**
     * Extract time from text and return the time plus remaining text.
     */
    private fun extractTime(text: String): TimeExtractionResult {
        for (pattern in TIME_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val time = parseMatchedTime(matcher, pattern)
                if (time != null) {
                    // Remove the matched time from text
                    val remaining = text.substring(0, matcher.start()) +
                            text.substring(matcher.end())
                    return TimeExtractionResult(time, remaining.trim())
                }
            }
        }
        return TimeExtractionResult(null, text)
    }

    /**
     * Parse matched time regex groups into LocalTime.
     */
    private fun parseMatchedTime(matcher: java.util.regex.Matcher, pattern: Pattern): LocalTime? {
        return try {
            val groups = (1..matcher.groupCount()).mapNotNull { matcher.group(it) }

            when {
                // 12-hour with minutes: groups = ["2", "30", "pm"]
                groups.size == 3 && groups[2].lowercase() in listOf("am", "pm") -> {
                    var hour = groups[0].toInt()
                    val minute = groups[1].toInt()
                    val isPm = groups[2].lowercase() == "pm"

                    if (isPm && hour != 12) hour += 12
                    if (!isPm && hour == 12) hour = 0

                    LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                }
                // 12-hour without minutes: groups = ["3", "pm"]
                groups.size == 2 && groups[1].lowercase() in listOf("am", "pm") -> {
                    var hour = groups[0].toInt()
                    val isPm = groups[1].lowercase() == "pm"

                    if (isPm && hour != 12) hour += 12
                    if (!isPm && hour == 12) hour = 0

                    LocalTime.of(hour.coerceIn(0, 23), 0)
                }
                // 24-hour or "at X": groups = ["14", "30"] or ["2"]
                groups.size == 2 && groups[0].toIntOrNull() != null && groups[1].toIntOrNull() != null -> {
                    val hour = groups[0].toInt()
                    val minute = groups[1].toInt()
                    LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                }
                // "at X" pattern: groups = ["3"]
                groups.size == 1 && groups[0].toIntOrNull() != null -> {
                    val hour = groups[0].toInt()
                    // Assume PM for hours 1-6, AM for 7-11
                    val adjustedHour = if (hour in 1..6) hour + 12 else hour
                    LocalTime.of(adjustedHour.coerceIn(0, 23), 0)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract location from text.
     * Looks for patterns like "at Location", "in Place", "@ Venue"
     */
    private fun extractLocation(text: String): LocationExtractionResult {
        val words = text.split("\\s+".toRegex())

        for ((index, word) in words.withIndex()) {
            val lowerWord = word.lowercase()

            // Check if this word is a location preposition
            if (lowerWord in LOCATION_PREPOSITIONS && index < words.size - 1) {
                // Everything after the preposition is potentially the location
                val locationWords = words.subList(index + 1, words.size)

                // Take words until we hit another preposition or end
                val locationParts = mutableListOf<String>()
                for (locWord in locationWords) {
                    if (locWord.lowercase() in TITLE_TERMINATORS) break
                    locationParts.add(locWord)
                }

                if (locationParts.isNotEmpty()) {
                    val location = locationParts.joinToString(" ")
                    val remaining = words.subList(0, index).joinToString(" ")
                    return LocationExtractionResult(location, remaining.trim())
                }
            }
        }

        return LocationExtractionResult(null, text)
    }

    /**
     * Clean up extracted title by removing extra whitespace and common filler words.
     */
    private fun cleanTitle(text: String): String {
        return text
            .replace("\\s+".toRegex(), " ")
            .trim()
            .removeSuffix(" with")
            .removeSuffix(" at")
            .removeSuffix(" in")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private data class TimeExtractionResult(
        val time: LocalTime?,
        val remainingText: String
    )

    private data class LocationExtractionResult(
        val location: String?,
        val remainingText: String
    )
}

/**
 * Represents a parsed event from natural language input.
 */
data class ParsedEvent(
    val title: String,
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val location: String?,
    val isAllDay: Boolean,
    val originalText: String
) {
    /**
     * Returns a human-readable summary of the parsed event.
     */
    fun summary(): String {
        val timePart = if (isAllDay) "all day" else {
            startTime?.let { "at ${it.hour}:${it.minute.toString().padStart(2, '0')}" } ?: ""
        }
        val locationPart = location?.let { " at $it" } ?: ""
        return "$title $timePart$locationPart"
    }
}
