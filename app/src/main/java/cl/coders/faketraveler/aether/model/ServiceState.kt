package cl.coders.faketraveler.aether.model

sealed interface ServiceState {
    data object Idle : ServiceState
    data object Starting : ServiceState
    data class Active(
        val profileName: String,
        val location: AetherLocation,
        val ticksDone: Int,
        val ticksTotal: Int
    ) : ServiceState
    data class Error(val message: String) : ServiceState
}
