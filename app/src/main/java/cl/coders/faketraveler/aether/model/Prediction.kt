package cl.coders.faketraveler.aether.model

data class Prediction(
    val nextApp: String,
    val confidence: Float,
    val alternatives: List<PredictionEntry> = emptyList()
)

data class PredictionEntry(
    val packageName: String,
    val probability: Float
)
