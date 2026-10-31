# Kiến trúc The Sift

Tất cả lớp nằm trong package phẳng `com.gamepathics.thesift.foundation` và chia theo vai trò:

## Vòng đời
- `TheSiftFoundationPlugin` — onEnable: init Messages/IchorState, đăng ký listener, wire các
  manager với nhau (gate owner, singer probe), lịch tick.
- `TheSiftBootstrap` — khởi động sớm, config version.

## Điều phối thế giới
- `SiftEcologyManager` — trang trí chunk (2 nhánh strict/fallback), wildlife index (UUID qua
  EntityAdd/RemoveToWorld), thời tiết Ichor, cauldron dripstone, đàn Sifter, spawn egg, gỗ
  Willis... Lớp lớn nhất; kế hoạch tách controller nằm ở Phase 4 (dừng ở bước xanh).
- `ChunkRetryBackoff` — backoff retry khi decoration strict fail.

## Portal / Rift
- `RiftManager` (+ `RiftInstance` nội bộ) — rift hai chiều, transport (mobs/items/vehicles),
  portal song tracker, reward economy (mặc định 0 sau hardening).
- `AncientCityPortal` — Goat Horn đánh thức Singer; `AncientCityGateManager` — gate frame,
  ownsSonorous() phân quyền note block với RiftManager; `AncientCityFrame` — hình học khung.
- `PortalSongTracker`, `SonorousBlockRegistry`, `SonorousConsoleTracker` — melody + block state.

## Nội dung
- `ContentRegistry`/`ContentId` — ID nội dung; `SiftiteContent`/`IchorContent` — item + recipe
  (exact-choice giữ tag); `ProviderContentIds` — danh sách bắt buộc.
- `SpearThrowHandler`, `Messages` (i18n vi/en + override), `Reflect` (cache reflection),
  `SiftMobFactory` — spawn + stat mod.

## Provider (đa plugin)
- `ExternalItemBridge`/`ExternalBlockBridge`/`MythicMobBridge`/`ModelEngineBridge` — reflective
  adapter cho Nexo/ItemsAdder/Oraxen/MythicMobs/EliteMobs/ModelEngine; SPI:
  `api/SiftItemProvider`, `SiftMobProvider`, `SiftBlockProvider`.
- `ProviderReadiness`/`ProviderAccessGuard` — strict gate (dimensionAllowed).

## Luồng dữ liệu
- PDC (chunk/entity) cho marker; `IchorState` (ghi nguyên tử + migration) cho cauldron/block;
  config.yml (migration 5) cho cấu hình; wildlife index in-RAM.
