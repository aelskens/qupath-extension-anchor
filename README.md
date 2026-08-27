# Anchor (qupath-extension-anchor)

A [QuPath](https://qupath.github.io) 0.7.0 extension for annotating **corresponding landmark points
across two or more whole-slide images (WSI)**, fitting spatial transforms between them, aligning and
synchronizing their viewers, and blending them into a false-color overlay. It is meant for validating
or deriving spatial transforms between serial sections, multiplexed IF panels, or different stains of
the same tissue, and for multi-annotator agreement studies.

Landmarks are always human-placed (no automatic feature detection). Each landmark is one QuPath point
annotation named `LM-xx`, colored by id, with metadata (annotator, mode, session), so the point sets
round-trip through GeoJSON and any GeoJSON-aware tool.

## Install

1. Install **QuPath 0.7.0**.
2. Build the extension jar (below) or download a release jar.
3. Drag the **`shadowJar`** jar onto the QuPath window (or copy it into QuPath's extensions
   directory), then restart. The menu appears under **`Extensions ▸ Anchor`**.

## Build

**Toolchain:** JDK **25** (QuPath 0.7.0's baseline). The bundled Gradle wrapper downloads Gradle on
first run, so no separate Gradle install is needed.

```bash
./gradlew shadowJar      # build the installable jar in build/libs/  (use this one)
./gradlew build          # compile + run unit tests (plain jar, not installable on its own)
```

Install the **`shadowJar`** output (e.g. `build/libs/qupath-extension-anchor-0.1.0-SNAPSHOT.jar`).
Most dependencies (QuPath API, JavaFX, Commons Math) are provided by QuPath at runtime, but the
nonlinear-warp library `imglib2-realtransform` is **not**, so it must be bundled: that is what
`shadowJar` does. The plain `jar` from `build` omits it and would fail at runtime for the TPS overlay.

## Quick start

Everything lives in the **Anchor control panel**: `Extensions ▸ Anchor ▸ Show Anchor panel`
(`Shortcut+Alt+A`). Every action also has a menu item and a keyboard shortcut.

1. **Open the images together.** Either open them yourself into a multi-view grid (`View ▸ Multi-view`),
   or, if they are in a QuPath project, use the panel's **Dataset** filter to open a group (below).
2. **(Optional) Pin a reference.** `Set reference to current viewer` (`Shortcut+Alt+F`) marks the image
   the others align to; otherwise the active/first image is used.
3. **Place alignment points.** `Place alignment points` (`Shortcut+Alt+P`) drops 3 draggable points;
   drag each onto the **same** feature in each image.
4. **Align & sync.** `Align & sync` (`Shortcut+Alt+Y`) fits a similarity from the chosen points and
   keeps every image parked on the same tissue location as you pan/zoom/rotate any of them.
   `Stop sync` (`Shortcut+Alt+U`) ends it.
5. **Check the alignment** with the color overlay, and **annotate** landmarks.
6. **Export** the landmark sets to GeoJSON/CSV for analysis.

## Panel reference

The panel is organized into titled sections.

### Session
Set the **annotator** id (asked once, reused for the session) and the **mode** (assisted / blind),
which only affects how prior annotations are loaded for agreement studies, not the alignment.

### Dataset
For annotating a project group by group. Type a **metadata filter** in the search-bar style:
`key=value` pairs joined by `|`, all of which must match. Examples:

- `group=2` opens every project image whose `group` metadata is `2`.
- `group=2|stain=HE` opens every image whose `group` is `2` **and** `stain` is `HE`.

The label shows how many images match. **Open group in grid** loads the matching images into a single
row of viewers (one per image), fitted. Opening a new group starts fresh: it stops any sync, clears
the reference/overlay, and resets each viewer.

### Alignment & sync
Pin the reference, choose which points feed the fit (**Fit from**: manual / all / dragged-grid),
place alignment points or add a single landmark, then **Align & sync** / **Stop sync** / **Reset
views**. The live viewer sync is always a **similarity** (rotation + uniform scale + translation),
which is all a raw viewer can reproduce; richer transforms apply to the overlay only (below).

### Landmarks
Seed a deterministic near-square grid of **N** numbered points (drag each onto its feature), clear all
Anchor landmarks, load a prior GeoJSON point set, or export. Numbering continues from the highest
existing id, so grid points seeded after 3 alignment points start at 4. Landmark names use QuPath's
native point labels (toggle with the toolbar "Show names").

### Overlay
A false-color composite of the aligned images. Each image is reduced to intensity, tinted a distinct
color, and **additively** blended, so overlaps brighten toward white; brightfield/RGB (e.g. H&E) is
auto-inverted so its background drops out, while fluorescence is used as-is. Each image has its own
**opacity** slider (its name is shown in its tint color; set opacity 0 to hide it).

- **Overlay transform:** rigid / similarity / affine / **TPS** (nonlinear, elastic). This applies to
  the overlay only; changing it re-warps the composite without moving the viewers.
- **Show overlay:** renders the composite into a resizable preview in the panel (drag the divider).
- **Show in selected viewer** (`Shortcut+Alt+O`): also draws the composite in place, in the selected
  viewer, so you can judge the true (including elastic) alignment on the image itself.

Rendering runs on a background thread over the visible region (plus a margin) and is debounced while
you move, so navigation stays responsive.

## File formats

Landmarks export to **GeoJSON** (default) or **CSV**, in full-resolution image pixel coordinates.
GeoJSON keeps each point's id, annotator, mode and session under `properties`, so it re-imports as
Anchor landmarks and is readable by any GeoJSON tool and by the Python reporting scripts. When a file
already exists, export asks **overwrite / skip** (all-views) or **overwrite / rename**
(single view).

## Keyboard shortcuts

`Shortcut` is `Ctrl` on Windows/Linux and `Cmd` on macOS.

| Action | Shortcut |
|--------|----------|
| Show Anchor panel | `Shortcut+Alt+A` |
| Set reference to current viewer | `Shortcut+Alt+F` |
| Place alignment points | `Shortcut+Alt+P` |
| Add landmark | `Shortcut+Alt+L` |
| Align & sync viewers | `Shortcut+Alt+Y` |
| Stop aligned sync | `Shortcut+Alt+U` |
| Reset views | `Shortcut+Alt+R` |
| Seed landmark grid | `Shortcut+Alt+G` |
| Export all landmarks | `Shortcut+Alt+E` |
| Toggle overlay in selected viewer | `Shortcut+Alt+O` |

## Repository layout

- `src/main/java/qupath/ext/anchor/` - the extension:
  - `model/` - landmark id / color / metadata helpers, image-name helpers.
  - `seeding/` - deterministic grid generation.
  - `transform/` - rigid / similarity / affine (Commons Math) and TPS (imglib2) fitting + validation.
  - `viewer/` - aligned N-image view sync, reference indicator.
  - `overlay/` - the false-color composite renderer (panel preview + in-viewer).
  - `commands/` - menu/panel commands (align, seed, export, open group, ...).
  - `io/` - headless load/export facade used by the batch scripts.
  - `ui/` - the Anchor control panel.
- `src/test/java/` - JUnit tests for the pure transform + seeding logic.
- `scripts/` - Groovy examples, including `batch_load_landmarks.groovy` / `batch_export_landmarks.groovy`
  for "Run for project" dataset annotation.
