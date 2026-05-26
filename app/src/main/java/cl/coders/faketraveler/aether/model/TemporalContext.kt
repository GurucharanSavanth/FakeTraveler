package cl.coders.faketraveler.aether.model

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class TemporalContext(
    val sinHour: Float,
    val cosHour: Float,
    val dayOfWeek: Int
) {
    companion object {
        fun now(): TemporalContext {
            val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val hourFraction = local.hour + local.minute / 60.0
            val angle = 2.0 * PI * hourFraction / 24.0
            return TemporalContext(
                sinHour = sin(angle).toFloat(),
                cosHour = cos(angle).toFloat(),
                dayOfWeek = local.dayOfWeek.ordinal
            )
        }
    }
}
