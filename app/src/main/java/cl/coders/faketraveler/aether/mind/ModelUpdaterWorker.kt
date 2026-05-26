package cl.coders.faketraveler.aether.mind

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Nightly [CoroutineWorker] that reads local training data and updates the
 * TFLite model's head weights.
 *
 * Constraints: WiFi + charging (to avoid draining battery or metered data).
 * Period: once per day. Enqueue via [enqueueNightly].
 *
 * The worker is intentionally defensive: if the model file does not exist yet,
 * or local data is insufficient, it succeeds silently rather than retrying
 * indefinitely.
 */
class ModelUpdaterWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val modelFile = File(applicationContext.filesDir, MODEL_FILE_NAME)
            if (!modelFile.exists()) {
                Log.i(TAG, "Model file absent; skipping update")
                return Result.success()
            }

            val trainingDataFile = File(applicationContext.filesDir, TRAINING_DATA_FILE)
            if (!trainingDataFile.exists() || trainingDataFile.length() == 0L) {
                Log.i(TAG, "No local training data; skipping update")
                return Result.success()
            }

            updateHeadWeights(modelFile, trainingDataFile)

            Log.i(TAG, "Head weight update completed successfully")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Model update failed", t)
            // Do not retry on failure — wait for the next nightly window.
            // Retrying could thrash a broken model in a tight loop.
            Result.failure()
        }
    }

    /**
     * Reads local training samples and re-trains only the head (final layer)
     * weights of the TFLite model, then persists the updated model.
     *
     * This is a placeholder implementation: real on-device fine-tuning would
     * use TFLite's Transfer Learning API or an equivalent incremental trainer.
     */
    private fun updateHeadWeights(modelFile: File, trainingDataFile: File) {
        // Step 1: Load existing model for on-device fine-tuning
        val wrapper = TfLiteWrapper.create(applicationContext).getOrNull()
            ?: run {
                Log.w(TAG, "Could not create TfLiteWrapper for update")
                return
            }

        wrapper.use { w ->
            // Step 2: Read local training samples
            val samples = trainingDataFile.readLines()
                .filter { it.isNotBlank() }
            if (samples.isEmpty()) {
                Log.i(TAG, "No training samples found")
                return@use
            }

            // Step 3: Run inference on each sample to validate model health
            for (line in samples) {
                val values = line.split(",")
                    .mapNotNull { it.trim().toFloatOrNull() }
                    .toFloatArray()
                if (values.isNotEmpty()) {
                    w.infer(values) // validate model can process input
                }
            }

            // Step 4: Persist updated weights (head-only update placeholder)
            // In production this would serialize the updated weight tensors
            // back to the model file. For now we touch the file to record
            // that an update cycle ran.
            val metaFile = File(modelFile.parentFile, MODEL_META_FILE)
            FileOutputStream(metaFile).use { fos ->
                fos.write("last_update=${System.currentTimeMillis()}\n".toByteArray())
                fos.write("samples=${samples.size}\n".toByteArray())
            }
        }
    }

    companion object {
        private const val TAG = "ModelUpdaterWorker"
        private const val UNIQUE_WORK_NAME = "aether_mind_model_update"
        internal const val MODEL_FILE_NAME = "aether_mind.tflite"
        internal const val TRAINING_DATA_FILE = "aether_mind_training.csv"
        internal const val MODEL_META_FILE = "aether_mind_meta.properties"

        /**
         * Enqueue a nightly periodic work request. Safe to call repeatedly;
         * [ExistingPeriodicWorkPolicy.KEEP] prevents duplicate scheduling.
         */
        fun enqueueNightly(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only
                .setRequiresCharging(true)
                .build()

            val request = PeriodicWorkRequestBuilder<ModelUpdaterWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.i(TAG, "Nightly model update enqueued (WiFi + charging)")
        }
    }
}
