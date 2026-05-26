package cl.coders.faketraveler.aether.sync

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import java.io.Closeable

/**
 * Dedicated high-priority [HandlerThread] wrapped as a [CoroutineDispatcher].
 *
 * All location-injection work runs on this single thread to guarantee ordering
 * and minimize scheduling jitter between providers. Implements [Closeable] so
 * callers can release the thread deterministically.
 */
class QuantumDispatcher : Closeable {

    private val thread: HandlerThread =
        HandlerThread("aether.quantum", Process.THREAD_PRIORITY_URGENT_DISPLAY).also {
            it.start()
        }

    private val handler: Handler = Handler(thread.looper)

    /** Coroutine dispatcher backed by the dedicated handler thread. */
    val dispatcher: CoroutineDispatcher = handler.asCoroutineDispatcher("aether.quantum")

    /**
     * Gracefully shuts down the handler thread.
     *
     * Pending messages are allowed to complete before the looper exits.
     * Safe to call multiple times.
     */
    override fun close() {
        thread.quitSafely()
    }
}
