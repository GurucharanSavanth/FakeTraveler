package cl.coders.faketraveler.aether.mind

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Closeable wrapper around TensorFlow Lite [Interpreter].
 *
 * Gracefully degrades to a no-op when the model file is absent:
 * [infer] returns an empty array and logs a warning rather than throwing.
 * Callers must close this via [close] or structured-use (e.g. scope cancel).
 */
class TfLiteWrapper private constructor(
    private val interpreter: Interpreter?
) : Closeable {

    /**
     * Run inference on [input]. Returns the output float array, or an empty
     * array when no model is loaded.
     */
    fun infer(input: FloatArray): Result<FloatArray> = runCatching {
        val interp = interpreter
            ?: return Result.success(FloatArray(0))

        val outputSize = interp.getOutputTensor(0).shape().let { shape ->
            shape.fold(1) { acc, dim -> acc * dim }
        }
        val output = FloatArray(outputSize)
        interp.run(arrayOf(input), arrayOf(output))
        output
    }

    override fun close() {
        try {
            interpreter?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Interpreter.close() threw", t)
        }
    }

    companion object {
        private const val TAG = "TfLiteWrapper"
        private const val DEFAULT_MODEL_NAME = "aether_mind.tflite"

        /**
         * Factory that loads from the app's files directory. Returns a no-op wrapper
         * when the model file does not exist rather than crashing.
         */
        fun create(context: Context, modelName: String = DEFAULT_MODEL_NAME): Result<TfLiteWrapper> =
            runCatching {
                val modelFile = File(context.filesDir, modelName)
                if (!modelFile.exists()) {
                    Log.w(TAG, "Model file not found: ${modelFile.absolutePath}; operating in no-op mode")
                    return Result.success(TfLiteWrapper(interpreter = null))
                }
                val mappedBuffer = loadMappedFile(modelFile)
                val options = Interpreter.Options().apply {
                    setNumThreads(2)
                }
                val interpreter = Interpreter(mappedBuffer, options)
                TfLiteWrapper(interpreter)
            }

        private fun loadMappedFile(file: File): MappedByteBuffer {
            FileInputStream(file).use { fis ->
                val channel = fis.channel
                return channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    0,
                    channel.size()
                )
            }
        }
    }
}
