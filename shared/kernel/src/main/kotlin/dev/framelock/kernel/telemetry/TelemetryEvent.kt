package dev.framelock.kernel.telemetry

/**
 * Metrics-only telemetry: primitives, enums and bounded tokens; never images, never free text (ADR-004).
 * Each feature defines its own sealed family; the telemetry feature accepts only the families it knows (ADR-014).
 */
interface TelemetryEvent

/** Screens that can be tracked. Tracking a new one is a reviewed code change (ADR-004). */
enum class ScreenId { LIVE, MODELS }

/** UI elements that can be tracked. */
enum class ElementId { START, STOP, TOGGLE_HUD, IMPORT_MODEL, ACTIVATE_MODEL, ROLLBACK_MODEL }

/** App usage, emitted by the app and the UI modules. */
sealed interface UsageTelemetry : TelemetryEvent {
    data object AppSession : UsageTelemetry

    data class ScreenViewed(
        val screen: ScreenId,
    ) : UsageTelemetry

    data class UiInteraction(
        val element: ElementId,
    ) : UsageTelemetry
}
