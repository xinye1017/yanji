# Yanji iOS-inspired Component Mapping & System Specification

> **Task Reference**: YANJI-B — iOS-inspired UI System Redesign  
> **Target Audience**: Android UI Engineers & Product Designers  
> **Philosophy**: Deliver an iOS-inspired, restrained, elegant design system anchored natively in Android platform correctness (Jetpack Compose, Material3 foundations, WCAG 2.1 AA, TalkBack semantics, $\ge 48\,\text{dp}$ touch targets, and dynamic type scaling).

---

## 1. Global Architecture & Design Token Alignment

| Dimension | iOS HIG / Apple Design Language | Yanji Design System Token | Compose Primitive / Token Hook |
| :--- | :--- | :--- | :--- |
| **Canvas Background** | `systemGroupedBackground` | `YanjiGroupedBackground` (`#F2F2F7` / `#000000`) | `YanjiTheme.colors.groupedBackground` |
| **Container Surface** | `secondarySystemGroupedBackground` | `YanjiElevatedSurface` (`#FFFFFF` / `#1C1C1E`) | `YanjiTheme.colors.elevatedSurface` |
| **Primary Label** | `label` | `YanjiPrimaryLabel` (`#000000` / `#FFFFFF`) | `YanjiTheme.colors.primaryLabel` |
| **Secondary Label** | `secondaryLabel` | `YanjiSecondaryLabel` (`#6C6C70` / `#8E8E93`) | `YanjiTheme.colors.secondaryLabel` |
| **Tertiary Label** | `tertiaryLabel` | `YanjiTertiaryLabel` (`#8E8E93` / `#636366`) | `YanjiTheme.colors.tertiaryLabel` |
| **Quaternary Label** | `quaternaryLabel` | `YanjiQuaternaryLabel` (`#3C3C434D` / `#EBEBF52E`) | `YanjiTheme.colors.quaternaryLabel` |
| **Separator** | `separator` | `YanjiSeparator` (`#3C3C434A` / `#54545899`) | `YanjiTheme.colors.separator` |
| **Accent / Tint** | `systemBlue` / `tintColor` | `YanjiPrimary` (`#356AE6` / `#5B8BF5`) | `YanjiTheme.colors.primary` / `accent` |
| **Grouped Radius** | Continuous 16–20 pt | `GroupedCardRadius` (`20.dp`) | `YanjiTheme.radius.groupedCard` |
| **Row Radius** | Continuous 10–12 pt | `RowRadius` (`12.dp`) | `YanjiTheme.radius.row` |
| **Touch Target** | HIG 44×44 pt minimum | Android Platform 48×48 dp minimum | `Modifier.defaultMinSize(minHeight = 48.dp)` |

---

## 2. Comprehensive Component Mapping Table

| Current Yanji Component | iOS HIG Equivalent | Compose Implementation & Accessibility | Design Rationale & Token Binding |
| :--- | :--- | :--- | :--- |
| **`GlassBottomBar`** | `UITabBar` with modern translucent glass material (`UIBlurEffect` style `systemChromeMaterial`) | **Container**: Floating capsule or docked bar with `Modifier.navigationBarsPadding()`.<br>**Glass**: `haze` blur on supported APIs (Android 12+), fallback to solid `YanjiElevatedSurface` with 80% alpha and 0.8dp border (`separator`).<br>**Semantics**: `Role.Tab`, explicit `stateDescription = if (selected) "已选中" else "未选中"`, `contentDescription = tab.title` (avoids TalkBack duplication).<br>**Motion**: Respects `isReduceMotionEnabled()`; disable spring stretch bounce when active. | Replaces harsh opaque borders with optical boundary; maintains 48dp minimum item target; prevents double announcement in screen readers. |
| **`YanjiPageHeader`** | `UINavigationBar` (Large Title collapse to inline Title) | **Compose**: `YanjiPageHeader(largeTitle = true)` rendering `largeTitle` (34sp Bold). On scroll, collapses into compact inline title (17sp Semibold) within top app bar.<br>**Actions**: Flat text buttons or 40dp circle icon buttons with 48dp touch delegation. | Implements classic iOS hierarchical navigation anchor while strictly respecting Android status bar insets. |
| **`YanjiSectionHeader`** | `UITableView` Section Header (`UIListContentConfiguration`) | **Compose**: Simple `Text` with `YanjiTypography.footnote` (13sp Semibold), uppercase letter spacing, tinted with `YanjiSecondaryLabel`.<br>**Padding**: `8.dp` horizontal, `6.dp` bottom. Semantics marked as `heading()`. | Subtle typographic visual grouping without heavy card nesting or boxy outlines. |
| **`YanjiCard`** | Grouped Card Container (`UICollectionView` inset grouped style) | **Compose**: Surface with `GroupedCardRadius` (`20.dp`), background `YanjiElevatedSurface`, subtle border `0.8.dp` of `separator`.<br>**Shadow**: Eliminated heavy Android elevation; replaced by border stroke and background tint contrast. | Restrained depth; separates background from card content without blurry drop shadows. |
| **`YanjiGroupedCard`** | `UITableView` Grouped Inset Section | **Compose**: Column container wrapping list of `YanjiSettingsRow` with automatic hairline dividers (`YanjiSeparator`, 0.6dp) inset by 56dp (matching icon margin). Rounded outer border (`20.dp`). | Core building block for Home, Profile, and Settings screens; guarantees clean visual rhythm. |
| **`YanjiSettingsRow`** | `UITableViewCell` (Value1 / Inset Grouped Cell) | **Compose**: `Row` with `defaultMinSize(minHeight = 48.dp)`. Leading icon container (32×32dp rounded squircle), title (17sp Body), subtitle (13sp Footnote), value text (15sp Secondary), trailing chevron (`Icons.Outlined.ChevronRight`).<br>**Semantics**: Full row merged with `Modifier.semantics(mergeDescendants = true)`. | Essential for accessible settings and action lists. Merged TalkBack node reads whole line coherently in a single swipe. |
| **`YanjiPrimaryButton`** | `UIButton.Configuration.filled()` | **Compose**: `Button` with height `48.dp`, shape `PillRadius` (`999.dp`) or `ButtonRadius` (`12.dp`), background `YanjiPrimary`. Text in `17sp Semibold` with `OnPrimary`.<br>**Interaction**: Press feedback scale `0.98f` (`rememberPressScale()`), bypassed when `isReduceMotionEnabled()` is true. | Bold visual anchor for primary screen actions; haptic/scale feedback feels tactile yet responsive. |
| **`YanjiSecondaryButton`** | `UIButton.Configuration.tinted()` / `.gray()` | **Compose**: `FilledTonalButton` or `Surface` with `primarySoft` fill and `primary` label, or neutral `fill` (`#7878801F`). Min height 48dp. | Provides secondary visual hierarchy without conflicting with the page's primary CTA. |
| **`YanjiDangerButton`** | Destructive Action Button (`role = .destructive`) | **Compose**: Button with `YanjiDanger` fill (or soft danger tint with solid danger text), min height 48dp.<br>**Semantics**: Custom action or role configured with destructive announcement if required. | High warning salience; reserved exclusively for irreversible actions (e.g. data reset, delete records). |
| **`GlassSegmentedControl`** | `UISegmentedControl` | **Compose**: Background capsule in `secondaryFill` (`#7878801F`), active segment represented by an animated sliding white/dark-surface capsule with spring spec (`dampingRatio = 0.82f`).<br>**Semantics**: `Role.Tab` or `Role.RadioButton` per segment. Minimum target height 40dp (in 48dp padded row). | iOS-authentic segmented control with smooth physical sliding indicator and haptic feedback. |
| **`YanjiTextField`** | `UITextField` (Rounded Rect style) | **Compose**: `BasicTextField` inside container with `ContainerRadius` (`12.dp`), background `secondaryFill` (`#7878801F`), subtle focused ring `YanjiPrimary`. Includes clear button (`Icons.Default.Clear`) and optional leading icon. Height 48dp. | Flat modern text entry; no heavy Material underline or boxy floating outline. |
| **Inline Search Field** | `UISearchBar` (minimal style) | **Compose**: Pill-shaped container (`PillRadius`), background `secondaryFill`, search icon leading, placeholder in `secondaryLabel`, trailing microphone/clear button. Height 40dp with 48dp tap boundary. | Clean inline search bar standard across iOS Navigation Headers. |
| **Bottom Sheet** | `UISheetPresentationController` (Detents: `.medium()`, `.large()`) | **Compose**: `ModalBottomSheet` with top radius `24.dp`, background `elevatedSurface`, iOS drag handle (36×5dp capsule in `separator`), spring entrance/exit. Supports drag-to-dismiss with velocity threshold. | Standard Android bottom sheet elevated with iOS handle styling and backdrop dimming. |
| **Alert Dialog** | `UIAlertController` (`style = .alert`) | **Compose**: Custom dialog container with `20.dp` corner radius, centered title (`17sp Bold`), message (`13sp Regular`), separated by hairlines (`YanjiSeparator`), vertical or horizontal stacked action buttons with blue/red tint. | Familiar compact prompt dialog with crystal-clear action separation. |
| **Progress Ring** | `UIProgressView` / Activity Ring |
 **Compose**: `Canvas` arc drawing with rounded caps, background track `secondaryFill`, progress stroke `primary` (or lavender accent gradient). Respects `reduceMotion` (instant transition instead of perpetual spin). | Clean metric visualization; avoids cluttering with ticks or percentage numbers inside narrow rings. |
| **Journal Row** | Inset Grouped List Item (`UIListContentView`) | **Compose**: Multi-line `YanjiSettingsRow` or card item showing date badge, mood emoji, title, truncated excerpt, and optional photo thumbnail. Padding `16.dp`, divider `0.6.dp`. | Scannable study journal entries with uniform vertical cadence. |
| **Chat Bubble** | iMessage Balloon (`ChatBubble`) | **Compose**: User bubble: `YanjiPrimary` background with `OnPrimary` text, corner radius 18dp with tail-like smaller radius on bottom-end (4dp). AI (卷卷) bubble: `secondaryFill` background with `PrimaryLabel` text, smaller radius on bottom-start (4dp). | Distinguishable conversation threads; soft organic curves feel friendly and conversational. |
| **Empty State** | `UIContentUnavailableConfiguration` | **Compose**: Centered column: Soft illustrated icon (48×48dp squircle in `primarySoft`), headline in `title3`, supporting message in `footnote`, optional pill action button. Height min `200.dp`. | Welcoming, actionable empty states guiding students toward starting their next focus session. |
| **Loading State** | `UIActivityIndicatorView` & Skeleton Shimmer | **Compose**: Compact spinner (`CircularProgressIndicator` with 3dp stroke and rounded endpoints) or subtle shimmer gradient (`YanjiTheme.colors.fill` oscillating alpha). | Understated loading indicators that do not distract the user from reading existing content. |
| **Toast / Snackbar** | Dynamic Island / Floating Capsule Banner | **Compose**: Floating pill container centered at top (`Modifier.statusBarsPadding()`) or above bottom bar. Background `elevatedSurface` with glass blur, leading status icon, concise text, swipe-to-dismiss gesture. | Non-intrusive system notifications; replaces bottom edge-to-edge snackbars with modern floating capsules. |

---

## 3. Platform Correctness Safeguards (Android Native Anchors)

1. **Touch Target Enforcement**:  
   Every interactive element (buttons, chips, list rows, bottom bar tabs) is wrapped or padded with `Modifier.defaultMinSize(minHeight = 48.dp)` or `Modifier.minimumInteractiveComponentSize()`. iOS HIG allows 44pt, but Android platform guidelines require **$\ge 48\,\text{dp}$**. Yanji strictly complies with Android's $48\,\text{dp}$ rule.
2. **Back Gesture Handling**:  
   Predictive back animation and hardware/system back button handling via `BackHandler` and AndroidX Navigation. No custom swipe-from-left override that breaks Android system back gestures.
3. **Accessibility (TalkBack)**:  
   Compound components (such as `YanjiSettingsRow`) merge descendants using `Modifier.semantics(mergeDescendants = true)` so the screen reader speaks the title, subtitle, value, and action in a single utterance rather than four disjointed focus rings.
4. **Dynamic Type (`fontScale`)**:  
   All text scales use `sp` units. Containers are built with `wrapContentHeight` and flexible column arrangements, preventing text clipping at $1.5\times$ or $2.0\times$ font scale.
5. **Dark Mode & OLED Optimization**:  
   In dark mode, `YanjiGroupedBackground` transitions to pure `#000000` or `#0F172A`, while `YanjiElevatedSurface` provides contrast at `#1C1C1E` with subtle `#334155` borders, ensuring full contrast compliance without blinding users in low-light study sessions.
