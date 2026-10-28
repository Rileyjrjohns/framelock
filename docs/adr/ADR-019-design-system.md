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
