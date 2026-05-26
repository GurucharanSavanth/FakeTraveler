package cl.coders.faketraveler.aether.model

sealed class NavigationRoute(val route: String) {
    data object Dashboard : NavigationRoute("dashboard")
    data object ProfileEditor : NavigationRoute("profile_editor?profileId={id}") {
        fun withId(id: String) = "profile_editor?profileId=$id"
        fun newProfile() = "profile_editor?profileId="
    }
    data object Observatory : NavigationRoute("observatory")
    data object Settings : NavigationRoute("settings")
}
