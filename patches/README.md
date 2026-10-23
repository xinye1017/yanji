# Native glass material correction

`react-native-blur-overlay+3.2.1.patch` adjusts the Android lens for the app's
navigation dock. It keeps real background blur and refraction, with a quiet
centre and a narrow directional edge instead of a white outline.

`Glass.java` derives reflection strength and white lift from the tint's
brightness, so `regular` remains usable in dark mode, and reduces touch bloom.
Reflection blends toward white within the sampled colour's remaining range,
instead of adding light until the corners clip to white.
`LiveBlur.java` narrows the lens band to 10dp, limits its inward displacement
to about 14dp, and reduces dispersion and reflection. Captures, shader caching
and the library's platform support remain intact. The app's material colours
and sampling settings come from `src/theme/liquidGlass.ts`; opaque high contrast
uses the existing semantic paper surface.

`npm install` / `npm ci` applies the patch via `postinstall` and fails if it no
longer applies. When upgrading this library, check the upstream implementation
and both theme appearances before updating or removing the patch.

When regenerating on Windows, include only the edited Java sources so Gradle's
generated build paths cannot enter the patch or exceed Git's path limit:

```powershell
npx patch-package react-native-blur-overlay --include '(Glass|LiveBlur)[.]java$'
```

# Heroicons even-odd fill correction

`react-native-svg+15.15.5.patch` fixes Android path rendering for Heroicons
solid glyphs whose cut-outs rely on `fillRule="evenodd"`.

`PathView` initializes its path in the constructor, so `RenderableView.draw()`
does not enter the `computePaths` branch on the first real draw. Upstream set
the fill type only inside that branch, leaving the native path on
`Path.FillType.WINDING`; the focus clock's face and hands then collapse into a
solid dot.

The patch applies the component's fill rule whenever the path is drawn, while
leaving path construction, bounds, paint setup and caching unchanged. This
restores every Heroicons even-odd glyph across navigation and screens.

`npm install` / `npm ci` applies the patch via `postinstall`, and
`npm run test:guards` checks that the installed Android source still applies
the fill rule outside `computePaths`. When upgrading `react-native-svg`, verify
the upstream implementation before updating or removing the patch.
