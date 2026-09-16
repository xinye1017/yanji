# ADR 0001: Retain the QuickStartPreset compatibility tombstone

- Status: Accepted for the v12 / backup-schema-v1 line
- Date: 2026-09-16
- Owners: Data and release maintainers

## Context

`QuickStartPreset` has no UI, repository, or Store consumer. Runtime code neither creates nor reads it for product behavior. The Room table has existed since schema v5, however, and backup schema v1 still decodes and round-trips `quickStartPresets`. Older installations and user-owned JSON backups may therefore contain rows that the current UI cannot reach.

Deleting the table during an automation-hardening change would combine a Room v12→v13 migration with a user backup-format policy change. That is outside this change's low-risk mechanical scope and would make rollback materially harder.

## Decision

Keep `quick_start_presets` as a backward-compatibility tombstone for the current Room and backup major versions. The runtime feature stays disabled. The fixture guard and `AppInitializerInstrumentedTest` prevent it from becoming a seed path again.

The staged exit is:

1. **Phase A — complete:** runtime creation and consumption remain removed.
2. **Phase B — current:** backup schema v1 continues to decode and round-trip the legacy field.
3. **Phase C — deferred:** when explicitly approved, add Room v13, migrate every supported schema to v13, and drop the table/index without `fallbackToDestructiveMigration()`.
4. **Phase D — deferred:** only at a future backup schema major version may exports stop writing the field. Old files must still decode through a compatibility DTO.

## Consequences

- Benefit: old databases and backups retain reversible compatibility; this automation PR has no data migration risk.
- Cost: one dead Room table, DAO, entity, schema entry, and backup round-trip remain maintained.
- Trigger to revisit: an approved Room v13 data-cleanup release with migration/device evidence and a separately reviewed backup-major-version policy.

## Verification required for Phase C

- Reference report proving zero UI/repository/Store consumers.
- `MIGRATION_12_13` plus committed `app/schemas/13.json`.
- Every supported migration path to v13, including a populated v12 tombstone table.
- Old backup decode and import behavior tests.
- Upgrade, downgrade/rollback, and real-device recovery notes.
