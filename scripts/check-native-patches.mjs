import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const renderablePath = resolve(
  'node_modules/react-native-svg/android/src/main/java/com/horcrux/svg/RenderableView.java',
);
const source = await readFile(renderablePath, 'utf8');

assert.match(
  source,
  /if \(computePaths\) \{\s*mPath = getPath\(canvas, paint\);\s*\}\s*mPath\.setFillType\(fillRule\);/,
  'react-native-svg must apply fillRule to reused paths so Heroicons evenodd cut-outs render on Android',
);

console.log('Native patch guard: PASS (react-native-svg applies fillRule on every draw).');
