package cl.coders.faketraveler.aether.model

class NoPredictionException(val packageName: String) :
    Exception("No prediction for: $packageName")

class ProviderSetupException(val provider: MockProvider, cause: Throwable? = null) :
    Exception("Failed to setup provider: $provider", cause)

class ElevationFetchException(val lat: Double, val lon: Double, cause: Throwable? = null) :
    Exception("Failed to fetch elevation at ($lat, $lon)", cause)
