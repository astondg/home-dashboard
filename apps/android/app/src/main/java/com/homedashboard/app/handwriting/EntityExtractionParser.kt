package com.homedashboard.app.handwriting

import android.util.Log
import com.google.mlkit.nl.entityextraction.*
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Wraps ML Kit Entity Extraction to parse handwritten text into structured events.
 * Extracts DateTime and Address entities, falls back to regex-based NaturalLanguageParser.
 */
class EntityExtractionParser {

    companion object {
        private const val TAG = "EntityExtractionParser"
    }

    private var extractor: EntityExtractor? = null
    private var isModelReady = false
    private val fallbackParser = NaturalLanguageParser()

    /**
     * Download the English entity extraction model.
     * Safe to call multiple times — no-ops if already ready.
     */
    suspend fun initialize(): Boolean {
        return try {
            val client = EntityExtraction.getClient(
                EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
                    .build()
            )
            client.downloadModelIfNeeded().await()
            extractor = client
            isModelReady = true
            Log.d(TAG, "Entity extraction model ready")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download entity extraction model", e)
            false
        }
    }

    /**
     * Parse text into a ParsedEvent using ML Kit Entity Extraction.
     * Falls back to NaturalLanguageParser if the model isn't ready or finds nothing useful.
     */
    suspend fun parse(text: String, targetDate: LocalDate): ParsedEvent {
        val ext = extractor
        if (!isModelReady || ext == null) {
            Log.d(TAG, "Model not ready, using fallback parser")
            return fallbackParser.parse(text, targetDate)
        }

        return try {
            val params = EntityExtractionParams.Builder(text)
                .setEntityTypesFilter(
                    setOf(Entity.TYPE_DATE_TIME, Entity.TYPE_ADDRESS)
                )
                .build()

            val annotations = ext.annotate(params).await()

            if (annotations.isEmpty()) {
                Log.d(TAG, "No entities found, using fallback parser")
                return fallbackParser.parse(text, targetDate)
            }

            var extractedTime: LocalTime? = null
            var extractedDate: LocalDate = targetDate
            var extractedLocation: String? = null

            // Track which character ranges to strip from the original text
            val stripRanges = mutableListOf<IntRange>()

            for (annotation in annotations) {
                for (entity in annotation.entities) {
                    when {
                        entity is DateTimeEntity && extractedTime == null -> {
                            val millis = entity.timestampMillis
                            val instant = Instant.ofEpochMilli(millis)
                            val zonedDt = instant.atZone(ZoneId.systemDefault())
                            extractedTime = zonedDt.toLocalTime()

                            // Use extracted date if it differs from target
                            val parsedDate = zonedDt.toLocalDate()
                            if (parsedDate != LocalDate.now()) {
                                extractedDate = parsedDate
                            }

                            stripRanges.add(annotation.start..annotation.end - 1)
                            Log.d(TAG, "Extracted time: $extractedTime from '${text.substring(annotation.start, annotation.end)}'")
                        }
                        entity.type == Entity.TYPE_ADDRESS && extractedLocation == null -> {
                            extractedLocation = text.substring(annotation.start, annotation.end)
                            stripRanges.add(annotation.start..annotation.end - 1)
                            Log.d(TAG, "Extracted address: $extractedLocation")
                        }
                    }
                }
            }

            // If we didn't get anything useful from ML Kit, fall back
            if (extractedTime == null && extractedLocation == null) {
                Log.d(TAG, "No useful entities extracted, using fallback parser")
                return fallbackParser.parse(text, targetDate)
            }

            // Build title from text with extracted entities stripped out
            val title = buildTitle(text, stripRanges)

            // If ML Kit found a location but not a time (or vice versa),
            // let the fallback parser try to fill in the gaps
            if (extractedTime == null) {
                val fallback = fallbackParser.parse(text, targetDate)
                extractedTime = fallback.startTime
            }
            if (extractedLocation == null) {
                val fallback = fallbackParser.parse(text, targetDate)
                extractedLocation = fallback.location
            }

            ParsedEvent(
                title = title.ifBlank { text.trim() },
                date = extractedDate,
                startTime = extractedTime,
                endTime = extractedTime?.plusHours(1),
                location = extractedLocation,
                isAllDay = extractedTime == null,
                originalText = text.trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Entity extraction failed, using fallback", e)
            fallbackParser.parse(text, targetDate)
        }
    }

    /**
     * Remove extracted entity spans from text to derive the event title.
     */
    private fun buildTitle(text: String, stripRanges: List<IntRange>): String {
        if (stripRanges.isEmpty()) return text.trim()

        val sb = StringBuilder()
        var i = 0
        val sorted = stripRanges.sortedBy { it.first }

        for (range in sorted) {
            if (i < range.first) {
                sb.append(text, i, range.first)
            }
            i = range.last + 1
        }
        if (i < text.length) {
            sb.append(text, i, text.length)
        }

        return sb.toString()
            .replace("\\s+".toRegex(), " ")
            .trim()
            .removeSuffix(" at")
            .removeSuffix(" in")
            .removeSuffix(" with")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun close() {
        extractor?.close()
        extractor = null
        isModelReady = false
    }
}
