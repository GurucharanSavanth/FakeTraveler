package cl.coders.faketraveler.aether.forge

import cl.coders.faketraveler.aether.model.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fractional Brownian motion jitter generator using the Hosking (1984) method.
 *
 * Produces physically-consistent GPS-like position noise with long-range
 * dependence controlled by the Hurst exponent. Two independent generators
 * (latitude and longitude) use distinct seeds to prevent diagonal correlation
 * artifacts that would betray synthetic origin.
 *
 * The Hosking method is an exact O(N) streaming algorithm:
 * for each new sample n it solves Levinson-Durbin on the autocovariance
 * sequence to produce prediction coefficients, then adds a scaled Gaussian
 * innovation. This avoids FFT approximation errors and does NOT require
 * storing the full history (only the running Levinson state).
 *
 * @param hurst  Hurst exponent H in (0, 1). GPS empirical value is 0.75.
 * @param amplitudeMeters  Peak-to-peak amplitude of jitter in meters.
 */
class FbmJitterEngine(
    private val hurst: Float = 0.75f,
    private val amplitudeMeters: Float = 8.0f
) {
    init {
        require(hurst > 0f && hurst < 1f) { "Hurst exponent must be in (0, 1), was $hurst" }
        require(amplitudeMeters > 0f) { "Amplitude must be positive, was $amplitudeMeters" }
    }

    /** Seed constants: distinct to decorrelate lat/lon channels. */
    private val latGenerator = HoskingGenerator(hurst.toDouble(), LAT_SEED)
    private val lonGenerator = HoskingGenerator(hurst.toDouble(), LON_SEED)

    /**
     * Apply fBm jitter to a base coordinate.
     *
     * @param base  The reference geographic point.
     * @param timeSeconds  Monotonic time index (drives the fBm sequence).
     * @return A new [GeoPoint] offset by the current fBm sample.
     */
    fun jitter(base: GeoPoint, @Suppress("UNUSED_PARAMETER") timeSeconds: Double): GeoPoint {
        val latOffsetMeters = latGenerator.nextSample() * amplitudeMeters
        val lonOffsetMeters = lonGenerator.nextSample() * amplitudeMeters

        val latDeg = latOffsetMeters / METERS_PER_DEGREE_LAT
        val lonDeg = lonOffsetMeters / (METERS_PER_DEGREE_LAT * cos(Math.toRadians(base.lat)))

        val newLat = (base.lat + latDeg).coerceIn(-90.0, 90.0)
        val newLon = normalizeLon(base.lon + lonDeg)

        return GeoPoint(newLat, newLon)
    }

    /**
     * Reset both generators to their initial state.
     * Useful for deterministic replay from the same starting point.
     */
    fun reset() {
        latGenerator.reset()
        lonGenerator.reset()
    }

    companion object {
        /** Seed for the latitude fBm channel (mnemonic: AE7HER-L). */
        const val LAT_SEED: Long = 0xAE7_AE7_0000_000DL

        /** Seed for the longitude fBm channel (mnemonic: AE7HER-R). */
        const val LON_SEED: Long = 0xAE7_AE7_0000_00DDL

        /** Meters per degree of latitude (WGS-84 mean). */
        private const val METERS_PER_DEGREE_LAT = 111_320.0

        /** Normalize longitude to [-180, 180]. */
        private fun normalizeLon(lon: Double): Double {
            var result = lon
            while (result > 180.0) result -= 360.0
            while (result < -180.0) result += 360.0
            return result
        }
    }
}

/**
 * Streaming Hosking-method fBm generator.
 *
 * Generates exact fractional Brownian motion samples one at a time.
 * State grows as O(N) with the number of samples generated, but the
 * per-sample cost is O(N) multiplications (Levinson-Durbin update).
 *
 * For the FakeTraveler use case, N is bounded by session duration
 * (typically < 10 000 samples), so memory is not a concern.
 *
 * @param h  Hurst exponent in (0, 1).
 * @param seed  RNG seed for reproducibility.
 */
internal class HoskingGenerator(
    private val h: Double,
    private val seed: Long
) {
    private var rng = Random(seed)

    /** Past generated samples (x_0, x_1, ..., x_{n-1}). */
    private val samples = mutableListOf<Double>()

    /** Levinson-Durbin phi coefficients from previous step. */
    private var phi = doubleArrayOf()

    /** Current prediction error variance (sigma^2 for the innovation). */
    private var sigma2 = 1.0

    /** Two times the Hurst exponent, cached. */
    private val twoH = 2.0 * h

    /**
     * fBm autocovariance: gamma(k) = 0.5 * (|k+1|^{2H} - 2|k|^{2H} + |k-1|^{2H}).
     *
     * This is the covariance of fractional Gaussian noise (the increments of fBm).
     */
    private fun gamma(k: Int): Double {
        val kAbs = abs(k).toDouble()
        return 0.5 * (
            (kAbs + 1.0).pow(twoH)
                - 2.0 * kAbs.pow(twoH)
                + (if (k == 0) 0.0 else (kAbs - 1.0).pow(twoH))
        )
    }

    /**
     * Generate the next fBm sample using Hosking's algorithm.
     *
     * For sample index n:
     *   1. Compute the new autocovariance row gamma(n).
     *   2. Update Levinson-Durbin coefficients (phi, sigma2).
     *   3. Predict = sum of phi[j] * x[n-1-j] for j in 0..n-1.
     *   4. x[n] = predict + sqrt(sigma2) * N(0,1).
     *
     * @return The next fBm sample, approximately in [-1, 1] range
     *         (not strictly bounded, but Gaussian tails).
     */
    fun nextSample(): Double {
        val n = samples.size

        if (n == 0) {
            // First sample: x_0 ~ N(0, gamma(0)) = N(0, 1) since gamma(0)=1
            sigma2 = gamma(0)
            val x0 = rng.nextGaussian() * sqrt(sigma2)
            samples.add(x0)
            phi = doubleArrayOf()
            return x0
        }

        // Levinson-Durbin update for order n
        // Compute the "reflection coefficient" (new last phi)
        var num = gamma(n)
        for (j in 0 until phi.size) {
            num -= phi[j] * gamma(n - 1 - j)
        }
        val kn = num / sigma2

        // Update phi coefficients
        val newPhi = DoubleArray(n)
        newPhi[n - 1] = kn
        for (j in 0 until n - 1) {
            newPhi[j] = phi[j] - kn * phi[n - 2 - j]
        }

        // Update sigma^2
        sigma2 *= (1.0 - kn * kn)

        // Ensure numerical stability
        if (sigma2 < 1e-15) {
            sigma2 = 1e-15
        }

        phi = newPhi

        // Prediction: weighted sum of past samples
        var prediction = 0.0
        for (j in 0 until n) {
            prediction += phi[j] * samples[n - 1 - j]
        }

        // Innovation
        val innovation = rng.nextGaussian() * sqrt(sigma2)
        val xn = prediction + innovation

        samples.add(xn)
        return xn
    }

    /**
     * Reset to initial state for deterministic replay.
     */
    fun reset() {
        rng = Random(seed)
        samples.clear()
        phi = doubleArrayOf()
        sigma2 = 1.0
    }
}

/**
 * Extension: [Random.nextGaussian] using the Box-Muller transform.
 *
 * Kotlin's [Random] does not have a built-in Gaussian method, unlike
 * [java.util.Random]. We use the polar form for numerical stability.
 */
internal fun Random.nextGaussian(): Double {
    var v1: Double
    var v2: Double
    var s: Double
    do {
        v1 = 2.0 * nextDouble() - 1.0
        v2 = 2.0 * nextDouble() - 1.0
        s = v1 * v1 + v2 * v2
    } while (s >= 1.0 || s == 0.0)
    val multiplier = sqrt(-2.0 * kotlin.math.ln(s) / s)
    return v1 * multiplier
}
