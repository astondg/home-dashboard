package com.homedashboard.app.handwriting

import android.content.Context
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.WritingArea
import com.google.mlkit.vision.digitalink.RecognitionContext as MlKitRecognitionContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handles handwriting recognition using ML Kit Digital Ink Recognition.
 * Converts stylus strokes to text for creating calendar events.
 */
class HandwritingRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "HandwritingRecognizer"
        private const val DEFAULT_LANGUAGE = "en-US"
    }

    private var recognizer: DigitalInkRecognizer? = null
    private var modelIdentifier: DigitalInkRecognitionModelIdentifier? = null
    private var isModelDownloaded = false

    /**
     * Initialize the recognizer with the specified language model.
     * Downloads the model if not already available.
     */
    suspend fun initialize(languageTag: String = DEFAULT_LANGUAGE): Boolean {
        return try {
            // Get the model identifier for the language
            modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)

            if (modelIdentifier == null) {
                Log.e(TAG, "No model found for language: $languageTag")
                return false
            }

            val model = DigitalInkRecognitionModel.builder(modelIdentifier!!).build()

            // Check if model is already downloaded
            isModelDownloaded = checkModelDownloaded(model)

            if (!isModelDownloaded) {
                // Download the model
                isModelDownloaded = downloadModel(model)
            }

            if (isModelDownloaded) {
                // Create the recognizer
                recognizer = DigitalInkRecognition.getClient(
                    DigitalInkRecognizerOptions.builder(model).build()
                )
                Log.d(TAG, "Recognizer initialized successfully")
                true
            } else {
                Log.e(TAG, "Failed to download model")
                false
            }
        } catch (e: MlKitException) {
            Log.e(TAG, "Failed to initialize recognizer", e)
            false
        }
    }

    private suspend fun checkModelDownloaded(model: DigitalInkRecognitionModel): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val remoteModelManager = RemoteModelManager.getInstance()
            remoteModelManager.isModelDownloaded(model)
                .addOnSuccessListener { isDownloaded ->
                    continuation.resume(isDownloaded)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking model download status", e)
                    continuation.resume(false)
                }
        }
    }

    private suspend fun downloadModel(model: DigitalInkRecognitionModel): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val remoteModelManager = RemoteModelManager.getInstance()
            remoteModelManager.download(model, DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    Log.d(TAG, "Model downloaded successfully")
                    continuation.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error downloading model", e)
                    continuation.resume(false)
                }
        }
    }

    /**
     * Recognize handwritten strokes and return the recognized text.
     * Returns a list of recognition candidates ordered by confidence.
     *
     * @param ink The ink strokes to recognize
     * @param writingAreaWidth Width of the writing area in pixels (for recognition context)
     * @param writingAreaHeight Height of the writing area in pixels (for recognition context)
     */
    suspend fun recognize(
        ink: Ink,
        writingAreaWidth: Float = 0f,
        writingAreaHeight: Float = 0f
    ): RecognitionResult {
        val currentRecognizer = recognizer
            ?: return RecognitionResult.Error("Recognizer not initialized")

        if (ink.strokes.isEmpty()) {
            return RecognitionResult.Error("No strokes to recognize")
        }

        return suspendCancellableCoroutine { continuation ->
            val task = if (writingAreaWidth > 0f && writingAreaHeight > 0f) {
                val writingArea = WritingArea(writingAreaWidth, writingAreaHeight)
                val recognitionContext = MlKitRecognitionContext.builder()
                    .setPreContext("")
                    .setWritingArea(writingArea)
                    .build()
                currentRecognizer.recognize(ink, recognitionContext)
            } else {
                currentRecognizer.recognize(ink)
            }
            task.addOnSuccessListener { mlResult ->
                    val candidates = mlResult.candidates.map { it.text }
                    if (candidates.isNotEmpty()) {
                        continuation.resume(RecognitionResult.Success(candidates))
                    } else {
                        continuation.resume(RecognitionResult.Error("No text recognized"))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Recognition failed", e)
                    continuation.resume(RecognitionResult.Error(e.message ?: "Recognition failed"))
                }
        }
    }

    /**
     * Check if the recognizer is ready to use.
     */
    fun isReady(): Boolean = recognizer != null && isModelDownloaded

    /**
     * Release resources.
     */
    fun close() {
        recognizer?.close()
        recognizer = null
    }
}

/**
 * Result of handwriting recognition.
 */
sealed class RecognitionResult {
    /**
     * Recognition succeeded with a list of candidate texts.
     * The first candidate is the most likely match.
     */
    data class Success(val candidates: List<String>) : RecognitionResult() {
        val bestMatch: String get() = candidates.firstOrNull() ?: ""
    }

    /**
     * Recognition failed with an error message.
     */
    data class Error(val message: String) : RecognitionResult()
}

/**
 * Builder class for creating ML Kit Ink objects from touch events.
 */
class InkBuilder {
    private var strokeBuilder = Ink.Stroke.builder()
    private val strokes = mutableListOf<Ink.Stroke>()
    private var currentStrokeHasPoints = false

    /**
     * Add a point to the current stroke.
     */
    fun addPoint(x: Float, y: Float, timestamp: Long) {
        strokeBuilder.addPoint(Ink.Point.create(x, y, timestamp))
        currentStrokeHasPoints = true
    }

    /**
     * Complete the current stroke and start a new one.
     */
    fun finishStroke() {
        if (currentStrokeHasPoints) {
            strokes.add(strokeBuilder.build())
            strokeBuilder = Ink.Stroke.builder()
            currentStrokeHasPoints = false
        }
    }

    /**
     * Build the final Ink object from all strokes.
     */
    fun build(): Ink {
        // Finish any pending stroke
        finishStroke()

        val inkBuilder = Ink.builder()
        strokes.forEach { inkBuilder.addStroke(it) }
        return inkBuilder.build()
    }

    /**
     * Clear all strokes and start fresh.
     */
    fun clear() {
        strokes.clear()
        strokeBuilder = Ink.Stroke.builder()
        currentStrokeHasPoints = false
    }

    /**
     * Check if there are any strokes recorded.
     */
    fun hasStrokes(): Boolean = strokes.isNotEmpty() || currentStrokeHasPoints
}
