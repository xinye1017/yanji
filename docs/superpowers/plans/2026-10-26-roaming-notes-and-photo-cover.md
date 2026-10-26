# 记录此刻拍照封面与回顾页随笔库及漫游笔记实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为「记录此刻」增加拍照封面能力（方案 A 纸意渐隐），在「回顾页」右上角胶囊中拓展第三个选项「随笔库」（收藏随笔、未解决疑点、已解决疑点归类），并提供 3D 空间立体抽卡风格的「漫游随笔」功能。

**Architecture:**
1. **数据层 (Room v28 → v29)**：`journal_entries` 新增 `imageUri TEXT NULL` 字段，严格手写 SQLite 迁移脚本并同步 Room 29.json。新增随机抽取及随笔库分类查询 DAO。
2. **原生桥接层 (Kotlin Bridge)**：在 `YanjiDataModule` 中以零额外 npm 依赖的方式，利用 Android 系统 `MediaStore.ACTION_IMAGE_CAPTURE` 调起原生相机拍照，压缩存入应用专属私有目录（`context.filesDir/journal_photos/`），回传 `file://` URI。同步升级 `BridgeMappers` 与 TypeScript `NoteEntry` 契约。
3. **编辑页封面 (RecordMomentModal)**：顶部展示定高截断照片，通过 `react-native-svg` 实现自中间向下淡出到纸面（`colors.surface`）的平滑渐隐，支持重拍与移除。
4. **回顾页随笔库 (JournalLibraryView)**：在 `ReviewScreen` 胶囊增加「随笔库」视图，包含顶部「漫游随笔」Hero 入口及「⭐ 收藏」「❓ 待解决」「✅ 已解决」三态分类流，卡片高度随文本自适应。
5. **漫游抽卡界面 (RoamingNotesModal)**：全屏沉浸 Overlay，屏幕中央悬浮 3D 景深实体印相卡，支持左右拖拽滑动手势切卡与随机抽取。

**Tech Stack:** React Native 0.87 (Fabric), React 19, TypeScript, Android Kotlin, Room 2.7, React Native Reanimated, react-native-svg, Node test runner.

**Spec / Directives:**
1. 视觉遵循已确认的 Preview 原型工件（方案 A 纸意穿透渐隐 + 卡片自适应高度 + 顶部图片定高截断）。
2. 漫游抽卡遵循大空间感 3D 岁月印相卡设计，与时间线列表卡片形成鲜明风格区分。
3. 遵循 AGENTS.md 约束：零虚假数据、Room 为唯一持久化事实源、保护在途修改、严禁破坏性 Git 命令。

---

## Global Constraints
- React Native 0.87.1 / React 19.2.3，不引入带原生 C++ codegen 的复杂第三方 npm 相机库。
- Room 必须手写单调迁移脚本，严禁 `fallbackToDestructiveMigration()`。
- 照片存储必须使用内部私有目录，不泄露至外部公有存储，备份/恢复流程支持内部照片文件维护。

---

## Review Focus
1. 无照片随笔退化展现：没有拍照或存量旧随笔在编辑页、随笔库与漫游卡中必须优雅回退为纯文字版面，不留白边或异常空图层。
2. 权限与相机取消：用户在系统相机点击取消或权限受阻时，不崩溃且保留已录入的随笔正文。
3. 疑点攻克实时联动：在随笔库中点击「标为已攻克」后，即时从「待解决」流转移到「已解决」流，无需刷新整页。
4. 漫游抽卡无随笔边界：随笔库为空或仅有 1 篇时，抽卡界面提供文雅的空状态提示，不陷入死循环。
5. 深浅色无缝适配：渐隐遮罩在素笺浅色（`#FFFFFF`）与墨纸深色（`#262320`）之间无色差断层。

---

## Tasks

### Task 1: Room Database 迁移 (v28 → v29) 与 DAO 扩展
**Files:**
- Modify: `app/src/main/java/com/example/yanji/data/db/Entities.kt`
- Modify: `app/src/main/java/com/example/yanji/data/Models.kt`
- Modify: `app/src/main/java/com/example/yanji/data/db/Daos.kt`
- Modify: `app/src/main/java/com/example/yanji/data/db/YanjiDatabase.kt`
- Test: `app/src/test/java/com/example/yanji/data/db/YanjiMigrationTest.kt`

**Interfaces:**
- Consumes: Room Database v28
- Produces: `NoteEntryEntity.imageUri: String?`, `NoteEntry.imageUri: String?`, `getRandomNotes(limit): List<NoteEntryEntity>`

- [ ] **Step 1: Write the failing Room migration test (v28 → v29)**
  在 `YanjiMigrationTest.kt` 中添加 `migration_28_to_29_adds_imageUri_column` 测试用例，断言存量行升级后 `imageUri` 为 NULL。
- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew.bat testDebugUnitTest --tests com.example.yanji.data.db.YanjiMigrationTest`
  Expected: FAIL (Migration not found)
- [ ] **Step 3: Implement Entity, Models & Migration (28, 29)**
  - 在 `Entities.kt` 的 `NoteEntryEntity` 中新增 `val imageUri: String? = null`。
  - 在 `Models.kt` 的 `NoteEntry` 中新增 `val imageUri: String? = null`。
  - 在 `YanjiDatabase.kt` 中定义 `MIGRATION_28_29` 执行 `ALTER TABLE journal_entries ADD COLUMN imageUri TEXT DEFAULT NULL`，更新数据库版本为 29。
  - 在 `Daos.kt` 中增加 `@Query("SELECT * FROM journal_entries ORDER BY RANDOM() LIMIT :limit") suspend fun getRandomNotes(limit: Int): List<NoteEntryEntity>` 与分类查询方法。
- [ ] **Step 4: Run test to verify it passes & export schema**
  Run: `./gradlew.bat testDebugUnitTest --tests com.example.yanji.data.db.YanjiMigrationTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/example/yanji/data/ app/src/test/java/com/example/yanji/data/db/
  git commit -m "feat(db): migrate room database to v29 with imageUri column"
  ```

---

### Task 2: 原生相机拍照与 Bridge 契约升级
**Files:**
- Modify: `app/src/main/java/com/example/yanji/bridge/YanjiDataModule.kt`
- Modify: `app/src/main/java/com/example/yanji/bridge/BridgeMappers.kt`
- Modify: `src/bridge/index.ts`
- Test: `app/src/test/java/com/example/yanji/bridge/DataTableEventMappingTest.kt`

**Interfaces:**
- Consumes: `YanjiDataModule`, `BridgeMappers`
- Produces: `YanjiDataNative.takePhoto(): Promise<string>`, `saveQuickNote(content, date, sessionId, category, imageUri)`, `NoteEntry.imageUri`

- [ ] **Step 1: Update TypeScript contract in `src/bridge/index.ts`**
  - 在 `NoteEntry` 中增加 `imageUri: string | null;`。
  - 在 `YanjiDataBridge` 中增加 `takePhoto(): Promise<string | null>;`，并让 `saveQuickNote` 支持可选 `imageUri: string | null`。
- [ ] **Step 2: Implement Photo Capture & Mapping in Kotlin**
  - 在 `BridgeMappers.kt` 中将 `note.imageUri` 映射进 `NoteFields`。
  - 在 `YanjiDataModule.kt` 中增加 `@ReactMethod fun takePhoto(promise: Promise)`：创建私有临时文件 `context.filesDir.resolve("journal_photos/${UUID.randomUUID()}.jpg")`，通过 `FileProvider` 调起系统拍照 Intent，并在 `ActivityEventListener` 中处理回调，返回其 `file://` URI。
  - 更新 `saveQuickNote` 接收 `imageUri` 参数并持久化到 Room。
- [ ] **Step 3: Run TypeScript typecheck & native tests**
  Run: `npm run typecheck`
  Expected: PASS
- [ ] **Step 4: Commit**
  ```bash
  git add app/src/main/java/com/example/yanji/bridge/ src/bridge/
  git commit -m "feat(bridge): add native takePhoto bridge and imageUri field mapping"
  ```

---

### Task 3: 「记录此刻」编辑页顶部照片渐隐封面 (RecordMomentModal)
**Files:**
- Modify: `src/components/RecordMomentModal.tsx`
- Test: `test-e2e/decision-tagger-ui.test.mjs`

**Interfaces:**
- Consumes: `YanjiDataNative.takePhoto`, `YanjiDataNative.saveQuickNote`
- Produces: 编辑页相机拍照、预览渐隐顶图、重拍与移除交互

- [ ] **Step 1: Write test for photo capture UI state in `RecordMomentModal`**
  在 `test-e2e/decision-tagger-ui.test.mjs` 中添加针对带封面保存随笔的契约测试。
- [ ] **Step 2: Implement Scheme A Gradient Photo Header in `RecordMomentModal.tsx`**
  - 新增 `imageUri` 状态。
  - 工具栏增加「拍照」胶囊按钮，调用 `YanjiDataNative.takePhoto()`。
  - 有照片时在抽屉顶部展示高约 140dp 的图片，上方覆盖 SVG `LinearGradient` 从透明渐隐过渡到底部 `paper.colors.surface`。
  - 右上角悬浮「重拍」与「移除」按钮。
- [ ] **Step 3: Run tests to verify**
  Run: `node --test test-e2e/decision-tagger-ui.test.mjs`
  Expected: PASS
- [ ] **Step 4: Commit**
  ```bash
  git add src/components/RecordMomentModal.tsx test-e2e/decision-tagger-ui.test.mjs
  git commit -m "feat(ui): add photo cover with scheme A gradient fade to RecordMomentModal"
  ```

---

### Task 4: 回顾页胶囊扩展与「随笔库」分类流 (JournalLibraryView)
**Files:**
- Create: `src/screens/review/JournalLibraryView.tsx`
- Modify: `src/screens/ReviewScreen.tsx`
- Test: `test-e2e/review-timeline-entry.test.mjs`

**Interfaces:**
- Consumes: `ReviewView = 'timeline' | 'trend' | 'journal'`
- Produces: 胶囊第三项切换、随笔库三态分类过滤流、漫游入口唤起

- [ ] **Step 1: Write failing e2e test for Journal Library view in ReviewScreen**
  在 `test-e2e/review-timeline-entry.test.mjs` 中验证 `VIEWS` 包含 3 项，切换到 `journal` 时渲染「随笔库」及三态选项。
- [ ] **Step 2: Implement `JournalLibraryView.tsx`**
  - 实现顶部「漫游随笔」Hero 推荐卡。
  - 实现「⭐ 收藏」「❓ 待解决」「✅ 已解决」三态选项卡及动态计数。
  - 实现基于方案 A 的自适应卡片列表，支持一键切换收藏状态与疑点攻克状态。
- [ ] **Step 3: Wire into `ReviewScreen.tsx`**
  - `VIEWS` 扩展为 `['时间线', '趋势统计', '随笔库']`。
  - 当 `view === 'journal'` 时渲染 `JournalLibraryView`。
- [ ] **Step 4: Run tests to verify**
  Run: `node --test test-e2e/review-timeline-entry.test.mjs`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/screens/review/JournalLibraryView.tsx src/screens/ReviewScreen.tsx test-e2e/
  git commit -m "feat(review): introduce JournalLibraryView with 3-state categorization"
  ```

---

### Task 5: 3D 空间立体「漫游随笔」抽卡界面 (RoamingNotesModal)
**Files:**
- Create: `src/components/RoamingNotesModal.tsx`
- Modify: `src/screens/review/JournalLibraryView.tsx`
- Test: `test-e2e/journal-roaming.test.mjs`

**Interfaces:**
- Consumes: `getRandomNotes` / `getAllNotesFlow`
- Produces: 全屏 3D 景深抽卡 Modal、手势切卡、火漆印章排版

- [ ] **Step 1: Write test for RoamingNotesModal**
  编写 `test-e2e/journal-roaming.test.mjs` 验证抽卡 Modal 的渲染、左右翻卡及随机切卡契约。
- [ ] **Step 2: Implement `RoamingNotesModal.tsx`**
  - 使用 `react-native-reanimated` 与 `PanResponder` 构建中央悬浮 3D 卡片。
  - 实现背景双层景深倾斜散焦底牌。
  - 呈现和纸胶带、独立艺术画框照片（方案 A 纸意渐隐）、大引号引语、朱红火漆印章。
  - 支持左右滑动手势及「随机抽取」换一张动作。
- [ ] **Step 3: Connect with `JournalLibraryView.tsx`**
  点击随笔库顶部 Hero 卡片唤起 `RoamingNotesModal`，点击右上角 ✕ 平滑关闭。
- [ ] **Step 4: Run tests and typecheck**
  Run: `npm run typecheck && node --test test-e2e/journal-roaming.test.mjs`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/components/RoamingNotesModal.tsx src/screens/review/JournalLibraryView.tsx test-e2e/
  git commit -m "feat(roam): implement 3D spatial RoamingNotesModal with card gacha experience"
  ```

---

### Task 6: 完整构建验证与真机推送测试
**Files:**
- Output: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Execute bundle & typecheck**
  Run: `npm run typecheck && npm run bundle:android`
  Expected: 生成最新 Hermes 字节码资产且无类型错误。
- [ ] **Step 2: Assemble Debug APK**
  Run: `./gradlew.bat assembleDebug`
  Expected: BUILD SUCCESSFUL (无 clean，利用配置缓存增量编译)。
- [ ] **Step 3: Push to Real Device**
  运行 ADB 发现与推送复合指令，调起 `MainActivity` 并使用 `screencap` 验证各页面渲染正常无闪退。
- [ ] **Step 4: Final Git Push**
  推送代码至远端 `origin/main`。
