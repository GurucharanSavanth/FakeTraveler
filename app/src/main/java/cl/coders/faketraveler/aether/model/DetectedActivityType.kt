package cl.coders.faketraveler.aether.model

enum class DetectedActivityType(val code: Int) {
    IN_VEHICLE(0),
    ON_BICYCLE(1),
    ON_FOOT(2),
    STILL(3),
    UNKNOWN(4),
    TILTING(5),
    WALKING(7),
    RUNNING(8);

    companion object {
        fun fromCode(code: Int): DetectedActivityType =
            entries.find { it.code == code } ?: UNKNOWN
    }
}
