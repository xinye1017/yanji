# Native glass material correction

`react-native-blur-overlay+3.2.1.patch` fixes the Android glass shader's bright
white corner reflections on dark materials. Upstream adds specular light after
the body tint, so darkening `glassTint` alone cannot correct the rim.

The patch derives reflection strength from that same tint's brightness and
opacity, attenuating the rim on dark glass while preserving light and untinted
glass. It changes only the cached `uSpecular` uniform; live blur, refraction,
geometry and shader caching remain intact. The app's material colours still
come from `src/theme/liquidGlass.ts`.

`npm install` / `npm ci` applies the patch via `postinstall` and fails if it no
longer applies. When upgrading this library, check the upstream implementation
and both theme appearances before updating or removing the patch.
