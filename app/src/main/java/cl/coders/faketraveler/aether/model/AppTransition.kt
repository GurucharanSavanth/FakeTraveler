package cl.coders.faketraveler.aether.model

import kotlinx.datetime.Instant

data class AppTransition(
    val packageName: String,
    val timestamp: Instant
)
