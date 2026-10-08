/**
 * Yanji E2E Test Suite - Assertions Framework
 * Strict, opaque-box assertion utilities with rich error diagnostics.
 */

export class AssertionError extends Error {
  constructor(message, actual, expected) {
    super(message);
    this.name = 'AssertionError';
    this.actual = actual;
    this.expected = expected;
  }
}

export function assert(condition, message = 'Assertion failed') {
  if (!condition) {
    throw new AssertionError(message, condition, true);
  }
}

export function assertEqual(actual, expected, message) {
  if (actual !== expected) {
    const msg = message || `Expected ${JSON.stringify(expected)}, but got ${JSON.stringify(actual)}`;
    throw new AssertionError(msg, actual, expected);
  }
}

export function assertNotEqual(actual, expected, message) {
  if (actual === expected) {
    const msg = message || `Expected value NOT to equal ${JSON.stringify(expected)}`;
    throw new AssertionError(msg, actual, expected);
  }
}

export function assertDeepEqual(actual, expected, message) {
  const actualStr = JSON.stringify(sortKeys(actual));
  const expectedStr = JSON.stringify(sortKeys(expected));
  if (actualStr !== expectedStr) {
    const msg = message || `Deep equality mismatch:\nExpected: ${expectedStr}\nActual:   ${actualStr}`;
    throw new AssertionError(msg, actual, expected);
  }
}

export function assertMatches(string, regex, message) {
  if (!regex.test(string)) {
    const msg = message || `Expected "${string}" to match pattern ${regex}`;
    throw new AssertionError(msg, string, regex.toString());
  }
}

export function assertInRange(value, min, max, message) {
  if (typeof value !== 'number' || value < min || value > max) {
    const msg = message || `Expected ${value} to be in range [${min}, ${max}]`;
    throw new AssertionError(msg, value, `[${min}, ${max}]`);
  }
}

export async function assertRejects(asyncFn, errorPattern, message) {
  let threw = false;
  let caughtError = null;
  try {
    await asyncFn();
  } catch (err) {
    threw = true;
    caughtError = err;
  }

  if (!threw) {
    const msg = message || `Expected function to reject, but it resolved successfully`;
    throw new AssertionError(msg, 'resolved', 'rejected');
  }

  if (errorPattern) {
    const errMessage = caughtError?.message || String(caughtError);
    if (errorPattern instanceof RegExp) {
      if (!errorPattern.test(errMessage)) {
        throw new AssertionError(
          message || `Error message "${errMessage}" did not match pattern ${errorPattern}`,
          errMessage,
          errorPattern.toString()
        );
      }
    } else if (typeof errorPattern === 'string') {
      if (!errMessage.includes(errorPattern)) {
        throw new AssertionError(
          message || `Error message "${errMessage}" did not contain "${errorPattern}"`,
          errMessage,
          errorPattern
        );
      }
    }
  }
}

function sortKeys(obj) {
  if (obj === null || typeof obj !== 'object') {
    return obj;
  }
  if (Array.isArray(obj)) {
    return obj.map(sortKeys);
  }
  const sorted = {};
  for (const key of Object.keys(obj).sort()) {
    sorted[key] = sortKeys(obj[key]);
  }
  return sorted;
}
