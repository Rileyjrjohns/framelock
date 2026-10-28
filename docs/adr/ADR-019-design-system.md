# ADR-019: Shared glass design system

- Status: Accepted
- Date: 2026-10-28
- Amends: ADR-014

## Context

Live and Models duplicate glass drawing, button interactions and colours because feature UI modules cannot depend on
one another. The theme and font resources belong to the application, while dimensions and icons are spread across
features. Consolidation must preserve the existing appearance and rendering contracts.

## Decision

Add the Android Compose library `:shared:designsystem`. Only feature UI modules and `:app` may use it in production.
It may reach the kernel, never a feature. It owns the theme, Inter font files, shared vector resources, spacing,
radii, sizes, elevation, motion constants, semantic colours, detection palette and contrast calculations.

Use composition: internal bases implement glass drawing, press feedback and chip layout. Public variants are
PrimaryButton, DangerButton, GhostButton, AppIconButton, StatusChip, SelectionChip, AppCard, AppTopBar, AppDropdown,
EmptyState, AppLoader and StatusMessage. Preview each catalogue component. Interactive components enforce a 64 dp
minimum target. Resources provide preview text as well as application text.

Preserve two glass styles: video panels apply the existing dark shade and white tint, while Models panels apply only
the existing white tint to their dark gradient. Their API 29–30 fallback fills remain distinct. Sharing the drawing
implementation must not silently darken the Models screen. Both variants have JVM contrast coverage.

Screens receive immutable presentation data and event callbacks. Routes resolve ViewModels and collect flows; leaves
receive only what they render. A dedicated rendering object retains the bitmap buffers and per-frame reporting state.
The cached detection text layouts, display-slot lock and RENDER tracing remain in the live feature.

## Alternatives considered

- Component inheritance is unsuitable for composable functions and obscures which visual choices a variant makes.
- Keeping copies allows the screens to drift and makes contrast fixes easy to miss in one feature.
- Moving shared UI into the kernel would violate its pure-Kotlin boundary.

## Consequences

The shared library is an explicit Android exception to the pure shared-module rule. Module-role tests pin its reach.
Feature code keeps layout and state mapping; reusable visuals and their tokens have one owner. Visual equivalence is
still a device check: JVM tests cover contrast and calculations, not blur, font rasterization or window insets.

### Source and localization rules

Feature UI has one public top-level type or composable per matching filename, components in `ui/components`, and no
file over 400 lines. Screens have no private top-level helpers. Material primitives belong in the design system;
feature layouts use its variants and tokens, with `0.dp` the only literal dimension exception. Source rules and
negative fixtures check primitive imports (including aliases and qualified calls), declarations, file length,
literal `Text`/content descriptions and literal dimensions/colours. These are syntactic checks, not data-flow analysis:
indirect strings and arbitrary colour calculations still require review.

Strings use `<feature>_<screen>_<element>` keys with complete English and French resources, including catalogue
previews. Resource tests require matching keys and format placeholders. Enum-backed pipeline and HUD labels are
mapped explicitly; source labels refresh on configuration changes without changing the selected source. An open
source menu closes when the locale changes so it cannot retain old labels. Device names and model-supplied class
names remain data, not translated interface copy. Number formatting follows the app locale. Detection-label patterns
are read outside drawing; text formatting and measurement happen only on a cache miss, as before (ADR-012).

### Device validation (2026-10-28)

The physical API 36 check exposed a landscape-height overflow in the HUD. Use two columns in landscape while keeping
the existing portrait arrangement, colours, typography and spacing. An instrumented regression checks visibility of
every metric; it failed on the single-column layout and passes after this correction. Short import labels fit the
bottom action buttons in both languages. Results and remaining limits: [device validation](../device-validation-2026-10-28.md).
