/**
 * Yanji E2E Test Suite - Test Harness & Reporter
 * Supports modular suites across Tiers 1-4 with structured output and exit codes.
 */

class TestSuiteHarness {
  constructor() {
    this.currentTier = 'Tier 1';
    this.currentSuite = null;
    this.globalBeforeEachHooks = [];
    this.globalAfterEachHooks = [];
    this.suites = [];
    this.totalTests = 0;
    this.passedTests = 0;
    this.failedTests = 0;
    this.skippedTests = 0;
    this.failures = [];
    this.startTime = 0;
  }

  setTier(tierName) {
    this.currentTier = tierName;
  }

  describe(name, fn) {
    const suite = {
      tier: this.currentTier,
      name,
      tests: [],
      beforeEachHooks: [...this.globalBeforeEachHooks],
      afterEachHooks: [...this.globalAfterEachHooks],
    };
    this.suites.push(suite);

    const prevSuite = this.currentSuite;
    this.currentSuite = suite;
    try {
      fn();
    } finally {
      this.currentSuite = prevSuite;
    }
  }

  it(name, fn) {
    if (!this.currentSuite) {
      throw new Error(`Test "${name}" must be defined inside a describe block`);
    }
    this.currentSuite.tests.push({
      name,
      fn,
      skip: false,
    });
  }

  skip(name, fn) {
    if (!this.currentSuite) {
      throw new Error(`Skipped test "${name}" must be defined inside a describe block`);
    }
    this.currentSuite.tests.push({
      name,
      fn,
      skip: true,
    });
  }

  beforeEach(fn) {
    if (this.currentSuite) {
      this.currentSuite.beforeEachHooks.push(fn);
    } else {
      this.globalBeforeEachHooks.push(fn);
    }
  }

  afterEach(fn) {
    if (this.currentSuite) {
      this.currentSuite.afterEachHooks.push(fn);
    } else {
      this.globalAfterEachHooks.push(fn);
    }
  }

  async runAll() {
    this.startTime = Date.now();
    console.log('\n======================================================================');
    console.log('   「研迹」 REACT NATIVE REFACTORING — E2E TEST SUITE RUNNER');
    console.log('   Opaque-Box Requirement-Driven Verification (Tiers 1-4)');
    console.log('======================================================================\n');

    const tierStats = {};

    for (const suite of this.suites) {
      const tierName = suite.tier;
      if (!tierStats[tierName]) {
        tierStats[tierName] = { total: 0, passed: 0, failed: 0, skipped: 0 };
        console.log(`\n------------------------------------------------------------`);
        console.log(`[SUITE CATEGORY] ${tierName.toUpperCase()}`);
        console.log(`------------------------------------------------------------`);
      }

      console.log(`\n  ▶ ${suite.name}`);

      for (const test of suite.tests) {
        this.totalTests++;
        tierStats[tierName].total++;

        if (test.skip) {
          this.skippedTests++;
          tierStats[tierName].skipped++;
          console.log(`    ○ [SKIP] ${test.name}`);
          continue;
        }

        const testStart = Date.now();
        try {
          for (const hook of suite.beforeEachHooks) {
            await hook();
          }

          await test.fn();

          for (const hook of suite.afterEachHooks) {
            await hook();
          }

          const durationMs = Date.now() - testStart;
          this.passedTests++;
          tierStats[tierName].passed++;
          console.log(`    ✔ [PASS] ${test.name} (${durationMs}ms)`);
        } catch (err) {
          const durationMs = Date.now() - testStart;
          this.failedTests++;
          tierStats[tierName].failed++;
          console.log(`    ✖ [FAIL] ${test.name} (${durationMs}ms)`);
          console.log(`       Error: ${err.message}`);
          this.failures.push({
            tier: suite.tier,
            suite: suite.name,
            test: test.name,
            error: err,
          });
        }
      }
    }

    const totalDuration = ((Date.now() - this.startTime) / 1000).toFixed(2);

    console.log('\n======================================================================');
    console.log('                       E2E TEST EXECUTION SUMMARY');
    console.log('======================================================================');
    for (const [tier, stat] of Object.entries(tierStats)) {
      const passRate = stat.total > 0 ? ((stat.passed / stat.total) * 100).toFixed(1) : '100.0';
      const icon = stat.failed === 0 ? '✔' : '✖';
      console.log(` ${icon} ${tier.padEnd(10)}: ${stat.passed}/${stat.total} passed (${passRate}%) [Failed: ${stat.failed}, Skipped: ${stat.skipped}]`);
    }
    console.log('----------------------------------------------------------------------');
    console.log(` Total Tests: ${this.totalTests}`);
    console.log(` Passed:      ${this.passedTests}`);
    console.log(` Failed:      ${this.failedTests}`);
    console.log(` Skipped:     ${this.skippedTests}`);
    console.log(` Total Time:  ${totalDuration}s`);
    console.log('======================================================================\n');

    if (this.failures.length > 0) {
      console.log('FAILURES DETAIL:');
      this.failures.forEach((fail, idx) => {
        console.log(`\n${idx + 1}) [${fail.tier}] ${fail.suite} > ${fail.test}`);
        console.log(`   ${fail.error.stack || fail.error.message}`);
      });
      console.log('\nResult: FAILED\n');
      return false;
    }

    console.log('Result: ALL TESTS PASSED (100% SUCCESS)\n');
    return true;
  }
}

export const harness = new TestSuiteHarness();
export const describe = (name, fn) => harness.describe(name, fn);
export const it = (name, fn) => harness.it(name, fn);
export const skip = (name, fn) => harness.skip(name, fn);
export const beforeEach = (fn) => harness.beforeEach(fn);
export const afterEach = (fn) => harness.afterEach(fn);
