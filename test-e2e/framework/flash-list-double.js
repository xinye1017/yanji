/**
 * A faithful test double for `@shopify/flash-list`.
 *
 * The real package cannot be loaded here: its published `dist` is a TypeScript
 * project build that Node's ESM resolver cannot follow, and there is no
 * renderer in these tests to mount a Fabric list anyway. What the production
 * screens need from it is a *contract*, and that contract is what this double
 * implements:
 *
 *   - `data` is the complete row array; the list — not the caller — decides how
 *     many of those rows are mounted at once.
 *   - Only a window of cells is mounted on the first paint. Scrolling extends
 *     the window, so every row is reachable; nothing is dropped.
 *   - `ListHeaderComponent` and `ListFooterComponent` render outside that
 *     window, so a page's static blocks do not consume the cell budget.
 *   - `getItemType` is consulted per row, which is what keeps two recycling
 *     pools instead of one.
 *
 * The double therefore lets a test prove two separate things: that the page
 * hands over *all* of its rows (no truncation masquerading as optimization),
 * and that the number of mounted rows is bounded by the window rather than by
 * how much data the day contains. It cannot prove what the real Fabric list
 * does on a device — that remains a stated, unverified boundary.
 */

/** Default window: what fits a phone screen plus a little look-ahead. */
export const DEFAULT_WINDOW = 6;

export function createFlashListDouble({ windowSize = DEFAULT_WINDOW } = {}) {
  /** Rows the double will mount, keyed by list instance id. */
  const windows = new Map();
  /** Every render, so a test can inspect what the list was actually given. */
  const renders = [];

  function FlashList(props) {
    const {
      data = [],
      renderItem,
      keyExtractor,
      getItemType,
      ListHeaderComponent,
      ListFooterComponent,
      testID,
      ...rest
    } = props;

    const instanceId = testID ?? 'flash-list';
    const mounted = windows.get(instanceId) ?? Math.min(data.length, windowSize);
    windows.set(instanceId, mounted);

    const types = data.map((item, index) => (getItemType ? getItemType(item, index) : undefined));
    renders.push({ testID: instanceId, rowCount: data.length, mounted, types });

    const children = [];
    if (ListHeaderComponent) children.push(ListHeaderComponent);
    for (let index = 0; index < Math.min(mounted, data.length); index += 1) {
      const item = data[index];
      const key = keyExtractor ? keyExtractor(item, index) : String(index);
      const element = renderItem({ item, index, target: 'Cell' });
      children.push(element ? { ...element, key } : null);
    }
    if (ListFooterComponent) children.push(ListFooterComponent);

    return {
      type: 'FlashList',
      props: {
        ...rest,
        testID: instanceId,
        // Kept on the props as well as rendered into children, so a test can ask
        // "did the page hand its static blocks to the header?" without having to
        // search the rendered subtree for them.
        data,
        renderItem,
        keyExtractor,
        getItemType,
        ListHeaderComponent,
        ListFooterComponent,
        rowCount: data.length,
        mounted,
        types,
        children,
      },
    };
  }

  /**
   * Extend one list's window, as a scroll would.
   *
   * Returns the number of rows mounted afterwards, so a test can assert the
   * window grew without the caller having to re-render the page itself.
   */
  function scroll(by = windowSize, instanceId = 'flash-list') {
    const data = renders.filter(render => render.testID === instanceId).at(-1);
    const current = windows.get(instanceId) ?? 0;
    const next = Math.min((data?.rowCount ?? current), current + by);
    windows.set(instanceId, next);
    return next;
  }

  /** The most recent render of one list. */
  function lastRender(instanceId = 'flash-list') {
    return renders.filter(render => render.testID === instanceId).at(-1) ?? null;
  }

  return { FlashList, scroll, lastRender, renders };
}
