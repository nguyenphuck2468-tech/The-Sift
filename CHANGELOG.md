# Changelog

## Overnight hardening (nhánh overnight/hardening)

- fix(spear): SpearThrowHandler — hết nhân bản độ bền, drop đúng một lần cả khi trúng block.
- fix(rift-economy): reward-drops 0 / rift-key-chance 0.0 / cooldown 30 phút; migration 4→5.
- fix(rift-console): findConsole giới hạn 4096 tra cứu + cooldown 4 tick (trước: ~2.3M tra cứu).
- test(strict): matrix dimensionAllowed; CI thêm bước boot strict:true không provider.
- chore(legal): THIRD_PARTY_LICENSES từ LICENSE thật của 2 repo upstream; gỡ Geyser jar.embedded.
- refactor: minor fixes Phase 2 (ThreadLocalRandom, opener UUID, anchors UUID, index cleanup,
  getPluginMeta).
- feat(gate): nội dung fan-made mặc định TẮT (prism_warden, sift_standin_mobs, sift_tide,
  rift_walker_waves); 5 mob lõi mod không bị ảnh hưởng.

## Trước đó (đã vào main)

- Consolidation toàn bộ nhánh, port worldgen mod, parity advancement 8/8, i18n vi/en,
  họ Willow 21 + Siftslate 12, fossils, discs, transport phương tiện — xem git log.
