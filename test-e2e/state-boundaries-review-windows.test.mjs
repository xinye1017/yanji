import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const { DataRefreshCoordinator, reviewWindowKey } = loadTypeScript('src/state/dataRefreshCoordinator.ts');
const settle = () => new Promise(resolve => setImmediate(resolve));
const week = (anchorDate, periodsBack = 0) => ({ scope: 'CALENDAR_WEEK', periodsBack, anchorDate });
const month = (anchorDate, periodsBack = 0) => ({ scope: 'CALENDAR_MONTH', periodsBack, anchorDate });
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const overview = (request, totalSeconds = 60) => ({
  scope: request.scope, periodsBack: request.periodsBack, label: 'window', windowDays: 7,
  days: [{ date: '2026-10-06', durationSeconds: totalSeconds, isToday: false, isFuture: false }],
  totalSeconds, dailyAverageSeconds: totalSeconds, examCount: 0, subjectDistribution: [],
});
function fixture(getReviewOverviews, Coordinator = DataRefreshCoordinator) {
  const batches = [];
  const coordinator = new Coordinator({
    getReviewOverviews: requests => {
      batches.push(requests);
      return getReviewOverviews ? getReviewOverviews(requests) : Promise.resolve(requests.map(request => overview(request)));
    },
  });
  return { coordinator, batches };
}
function clockFixture(iso, loader) {
  let now = new Date(`${iso}T12:00:00`).getTime();
  class ClockDate extends Date {
    constructor(...args) { super(...(args.length ? args : [now])); }
    static now() { return now; }
  }
  const { DataRefreshCoordinator: Coordinator } = loadTypeScript('src/state/dataRefreshCoordinator.ts', {}, { Date: ClockDate });
  return { ...fixture(loader, Coordinator), setDate: date => { now = new Date(`${date}T12:00:00`).getTime(); } };
}

test('review window identity honours Monday, month ends, leap years and periodsBack across years', () => {
  assert.equal(reviewWindowKey(week('2027-01-03')), reviewWindowKey(week('2026-12-28')));
  assert.notEqual(reviewWindowKey(week('2027-01-04')), reviewWindowKey(week('2027-01-03')));
  assert.equal(reviewWindowKey(week('2027-01-10', 1)), reviewWindowKey(week('2026-12-29')));
  assert.equal(reviewWindowKey(month('2026-03-31', 1)), reviewWindowKey(month('2026-02-01')));
  assert.equal(reviewWindowKey(month('2028-03-31', 1)), 'CALENDAR_MONTH:2028-02-01:2028-02-29');
  assert.equal(reviewWindowKey(month('2027-01-31', 1)), reviewWindowKey(month('2026-12-01')));
  assert.notEqual(reviewWindowKey(month('2026-10-31')), reviewWindowKey(month('2026-11-01')));
  assert.equal(reviewWindowKey(week('2026-10-06', -2)), reviewWindowKey(week('2026-10-08')));
  assert.equal(reviewWindowKey({ scope: 'TODAY', anchorDate: '2026-10-06', periodsBack: 9 }),
    reviewWindowKey({ scope: 'TODAY', anchorDate: '2026-10-06', periodsBack: 0 }));
});

test('equivalent week/month anchors share one read each and project stable request metadata', async () => {
  const { coordinator, batches } = fixture();
  const requests = [week('2026-10-06'), week('2026-10-07'), week('2026-10-08'), week('2026-10-13', 1),
    month('2026-10-01'), month('2026-10-31'), month('2026-11-30', 1)];
  const before = JSON.stringify(requests);
  await coordinator.prefetchReviewOverviews(requests);
  assert.equal(batches.length, 1);
  assert.equal(batches[0].length, 2, 'seven raw requests reduce to two native windows');
  const first = coordinator.getReviewOverview(requests[0]);
  for (const request of requests.slice(0, 4)) {
    const value = coordinator.getReviewOverview(request);
    assert.equal(value.periodsBack, request.periodsBack);
    assert.equal(value.scope, request.scope);
    assert.equal(value.days, first.days, 'the heavy arrays are shared');
    assert.equal(coordinator.getReviewOverview(request), value, 'external-store snapshots have stable identity');
  }
  const last = coordinator.getReviewOverview(requests[6]);
  assert.equal(last.periodsBack, 1, 'a next-month/previous-period alias keeps its own display metadata');
  assert.equal(last.days, coordinator.getReviewOverview(requests[4]).days);
  assert.equal(JSON.stringify(requests), before, 'normalizing the cache never rewrites display anchors');
  coordinator.setReviewReader('review', requests);
  await coordinator.prefetchReviewOverviews([week('2026-10-09'), month('2026-10-15')]);
  await settle();
  assert.equal(batches.length, 1, 'both visible reads and later prefetches hit the shared warm cache');
});

test('duplicate current/week requests and equivalent reader switches share one in-flight read', async () => {
  const first = deferred();
  const { coordinator, batches } = fixture(() => first.promise);
  const tuesday = week('2026-10-06'), wednesday = week('2026-10-07');
  let notifications = 0;
  const callback = () => notifications++;
  const detachTuesday = coordinator.subscribeReviewOverview(tuesday, callback);
  const detachWednesday = coordinator.subscribeReviewOverview(wednesday, callback);
  coordinator.setReviewReader('review', [tuesday, tuesday]);
  coordinator.setReviewReader('review', [wednesday]);
  await coordinator.prefetchReviewOverviews([week('2026-10-08')]);
  detachTuesday();
  detachTuesday(); // Stale cleanup must not erase the equivalent subscriber's channel.
  assert.equal(batches.length, 1);
  assert.equal(batches[0].length, 1);
  first.resolve([overview(tuesday)]);
  await settle();
  assert.equal(coordinator.getReviewOverview(wednesday).totalSeconds, 60);
  assert.equal(notifications, 1, 'one equivalent subscriber is still attached');
  assert.equal(batches.length, 1, 'the switch did not invalidate the read before it landed');
  detachWednesday();
});

test('writes invalidate one shared window, discard stale in-flight success and retry once', async () => {
  const old = deferred(), fresh = deferred();
  const { coordinator, batches } = fixture(() => batches.length === 1 ? old.promise : fresh.promise);
  const a = week('2026-10-06'), b = week('2026-10-07');
  let notifications = 0;
  coordinator.subscribeReviewOverview(a, () => notifications++);
  coordinator.setReviewReader('one', [a]);
  coordinator.setReviewReader('two', [b]);
  coordinator.enqueueDataChanged('sessions');
  coordinator.enqueueDataChanged('subjects');
  await settle();
  old.resolve([overview(a, 60)]);
  await settle();
  assert.equal(coordinator.getReviewOverview(b), null);
  assert.equal(notifications, 0);
  assert.equal(batches.length, 2);
  assert.equal(batches[1].length, 1);
  fresh.resolve([overview(b, 180)]);
  await settle();
  assert.equal(coordinator.getReviewOverview(a).totalSeconds, 180);
  assert.equal(coordinator.getReviewOverview(b).totalSeconds, 180);
  assert.equal(notifications, 1);
});

test('failed reads and retries apply to every equivalent display request', async () => {
  const { coordinator, batches } = fixture(async requests => {
    if (batches.length === 2) throw new Error('read failed');
    return requests.map(request => overview(request, batches.length * 60));
  });
  const a = month('2026-10-01'), b = month('2026-11-30', 1);
  coordinator.subscribeReviewOverview(a, () => {});
  coordinator.setReviewReader('review', [a, b]);
  await settle();
  await coordinator.refreshReviewOverview(b);
  assert.equal(coordinator.getReviewReadFailed(a), true);
  assert.equal(coordinator.getReviewReadFailed(b), true);
  assert.equal(coordinator.getReviewOverview(a).totalSeconds, 60);
  await coordinator.refreshReviewOverview(a);
  assert.equal(coordinator.getReviewReadFailed(b), false);
  assert.equal(coordinator.getReviewOverview(b).totalSeconds, 180);
  assert.equal(coordinator.getReviewOverview(b).periodsBack, 1);
});

test('background and hidden equivalent readers retain data and defer writes until activation', async () => {
  const { coordinator, batches } = fixture();
  const a = week('2026-10-06'), b = week('2026-10-08');
  coordinator.setForeground(false);
  coordinator.setReviewReader('review', [a, b]);
  await coordinator.prefetchReviewOverviews([a, b]);
  assert.equal(batches.length, 0);
  coordinator.setForeground(true);
  await settle();
  assert.equal(batches.length, 1);
  assert.equal(batches[0].length, 1);
  const detach = coordinator.subscribeReviewOverview(b, () => {});
  coordinator.setReviewReader('review', null);
  coordinator.enqueueDataChanged('sessions');
  await settle();
  assert.equal(batches.length, 1);
  assert.equal(coordinator.getReviewOverview(a).totalSeconds, 60);
  coordinator.setReviewReader('review', [b, a]);
  await settle();
  assert.equal(batches.length, 2);
  assert.equal(batches[1].length, 1);
  detach();
});

test('warm-cache limit counts unique windows and never evicts an active prefetched window', async () => {
  const { coordinator, batches } = fixture();
  const held = week('2020-01-07');
  await coordinator.prefetchReviewOverviews([held]);
  const release = coordinator.subscribeReviewOverview(held, () => {});
  const requests = Array.from({ length: 26 }, (_, index) => {
    const day = new Date(2021, 0, 5 + index * 7, 12);
    const anchor = `${day.getFullYear()}-${`${day.getMonth() + 1}`.padStart(2, '0')}-${`${day.getDate()}`.padStart(2, '0')}`;
    return week(anchor);
  });
  for (const request of requests) {
    await coordinator.prefetchReviewOverviews([request, { ...request }, { ...request, periodsBack: -1 }]);
  }
  assert.ok(coordinator.getReviewOverview(held), 'subscribing promotes a prefetched value out of the eviction pool');
  assert.equal(coordinator.getReviewOverview(requests[0]), null);
  assert.equal(coordinator.getReviewOverview(requests[1]), null);
  for (const request of requests.slice(2)) assert.ok(coordinator.getReviewOverview(request));
  release();
  release();
  assert.ok(coordinator.getReviewOverview(held), 'repeated cleanup preserves the retained window');
  await coordinator.prefetchReviewOverviews([requests[0]]);
  assert.equal(batches.length, 28, 'the evicted window really reloads, while aliases never consumed cache slots');
});

test('today boundary revalidates current windows after midnight and keeps fully past windows warm', async () => {
  const f = clockFixture('2026-10-07');
  const current = week('2026-10-06'), past = week('2026-09-29');
  await f.coordinator.prefetchReviewOverviews([current, past]);
  f.setDate('2026-10-08');
  await f.coordinator.prefetchReviewOverviews([current, past]);
  assert.equal(f.batches.length, 2);
  assert.equal(f.batches[1].length, 1, 'only the window whose today flags/average denominator changed is stale');
  f.setDate('2026-10-12');
  await f.coordinator.prefetchReviewOverviews([current]);
  assert.equal(f.batches.length, 3, 'leaving Sunday removes the isToday flag and needs one last read');
  f.setDate('2026-10-13');
  await f.coordinator.prefetchReviewOverviews([current]);
  assert.equal(f.batches.length, 3, 'a fully past window then remains fresh across subsequent days');
});

test('month end and TODAY labels expire once when their window becomes fully past', async () => {
  const f = clockFixture('2026-10-31');
  const requests = [month('2026-10-15'), week('2026-10-31'),
    { scope: 'TODAY', periodsBack: 0, anchorDate: '2026-10-31' }];
  await f.coordinator.prefetchReviewOverviews(requests);
  f.setDate('2026-11-01');
  await f.coordinator.prefetchReviewOverviews(requests);
  assert.equal(f.batches[1].length, 3, 'October and the old today label become past; the crossing week advances today');
  f.setDate('2026-11-02');
  await f.coordinator.prefetchReviewOverviews(requests);
  assert.equal(f.batches[2].length, 1, 'only the Sunday-ending week needs its last transition to fully past');
  assert.equal(f.batches[2][0].scope, 'CALENDAR_WEEK');
});

test('a write during a shared read never schedules background work after its obsolete result lands', async () => {
  const old = deferred();
  const { coordinator, batches } = fixture(requests => batches.length === 1
    ? old.promise : Promise.resolve(requests.map(request => overview(request, 180))));
  const request = week('2026-10-06');
  coordinator.setReviewReader('review', [request]);
  coordinator.setForeground(false);
  coordinator.enqueueDataChanged('sessions');
  await settle();
  old.resolve([overview(request, 60)]);
  await settle();
  assert.equal(batches.length, 1);
  assert.equal(coordinator.getReviewOverview(request), null);
  coordinator.setForeground(true);
  await settle();
  assert.equal(batches.length, 2);
  assert.equal(coordinator.getReviewOverview(request).totalSeconds, 180);
});

test('a read spanning midnight cannot publish yesterday\'s today flags', async () => {
  const old = deferred(), fresh = deferred();
  const f = clockFixture('2026-10-07', () => f.batches.length === 1 ? old.promise : fresh.promise);
  const request = week('2026-10-06');
  f.coordinator.setReviewReader('review', [request]);
  f.setDate('2026-10-08');
  old.resolve([overview(request, 60)]);
  await settle();
  assert.equal(f.coordinator.getReviewOverview(request), null);
  assert.equal(f.batches.length, 2);
  fresh.resolve([overview(request, 120)]);
  await settle();
  assert.equal(f.coordinator.getReviewOverview(request).totalSeconds, 120);
});

function reviewHookFixture() {
  let slot = 0;
  const effects = [], pending = [], batches = [];
  const hooks = loadTypeScript('src/state/dataRefresh.ts', {
    react: { useCallback: fn => fn, useSyncExternalStore: () => null,
      useEffect: (effect, dependencies) => {
        const index = slot++;
        const previous = effects[index];
        if (!previous || dependencies.some((value, i) => value !== previous.dependencies[i])) {
          pending.push({ index, effect, dependencies });
        }
      } },
    '../bridge': { YanjiDataNative: { getReviewOverviews: async requests => {
      batches.push(requests); return requests.map(request => overview(request));
    } } },
  });
  return {
    batches,
    render(requests, visible = true) {
      slot = 0;
      hooks.useReviewDataVisibility('review', requests, visible);
      pending.forEach(({ index }) => effects[index]?.detach?.());
      pending.splice(0).forEach(({ index, effect, dependencies }) => {
        effects[index] = { dependencies, detach: effect() };
      });
    },
    unmount() { effects.forEach(effect => effect.detach?.()); },
  };
}

test('review visibility effect does not release a window just because its display anchor changed', async () => {
  const f = reviewHookFixture();
  f.render([week('2026-10-06'), week('2026-10-06')]);
  await settle();
  f.render([week('2026-10-07')]);
  await settle();
  f.render([week('2026-10-08')]);
  await settle();
  assert.equal(f.batches.length, 1, 'Tuesday, Wednesday and Thursday cause one bridge read through the real hook');
  f.render([week('2026-10-12')]);
  await settle();
  assert.equal(f.batches.length, 2, 'crossing Monday still releases and reads the new window');
  f.unmount();
});

test('changing day windows through the real hook keeps the shared week fresh', async () => {
  const f = reviewHookFixture();
  const day = anchorDate => ({ scope: 'TODAY', periodsBack: 0, anchorDate });
  f.render([day('2026-10-06'), day('2026-10-05'), week('2026-10-06')]);
  await settle();
  f.render([day('2026-10-07'), day('2026-10-06'), week('2026-10-07')]);
  await settle();
  f.render([day('2026-10-08'), day('2026-10-07'), week('2026-10-08')]);
  await settle();
  assert.equal(f.batches.length, 3, 'each new single-day window still needs its own read');
  assert.equal(f.batches.flat().filter(request => request.scope === 'CALENDAR_WEEK').length, 1,
    'daily request updates never invalidate the week that remains visible');
  assert.equal(f.batches[1].length, 1);
  assert.equal(f.batches[2].length, 1);
  f.render([day('2026-10-08'), week('2026-10-08')], false);
  await settle();
  assert.equal(f.batches.length, 3, 'hiding retains all values without querying');
  f.unmount();
});
