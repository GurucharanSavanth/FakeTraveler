package cl.coders.faketraveler.aether.observatory

object ObservatoryConstants {
    /** UsageStats polling interval in milliseconds. */
    const val POLL_INTERVAL_MS = 400L

    /** Maximum time budget for a single prediction call in milliseconds. */
    const val PREDICTION_BUDGET_MS = 400L

    /** Predictions below this confidence are discarded. */
    const val MIN_CONFIDENCE_THRESHOLD = 0.1f

    /** Maximum number of alternative predictions returned alongside the top pick. */
    const val MAX_ALTERNATIVES = 5

    /** Temporal similarity weight for hour-of-day matching (0..1). */
    const val TEMPORAL_HOUR_WEIGHT = 0.7f

    /** Temporal similarity weight for day-of-week matching (0..1). */
    const val TEMPORAL_DOW_WEIGHT = 0.3f
}
