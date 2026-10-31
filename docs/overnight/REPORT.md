# Overnight hardening — REPORT

Nhánh: `overnight/hardening` (từ `main` @ `adc1fc0`). Baseline: BUILD SUCCESSFUL 2m36s,
134 test PASSED / 0 failed. Kết thúc: BUILD SUCCESSFUL, 136 test (134 cũ + ProviderStrictnessTest
+ FanMadeGateTest), jacoco report bật ở mức report-only.

## Bảng task

| Task | Trạng thái | Commit |
|---|---|---|
| P0 baseline + verify 4 claims | Xong (1 claim REJECTED: nhánh biome prototype ĐANG generate → bỏ task xóa) | `91fa2c1` |
| P1-T1.1 Spear dedup (SpearThrowHandler) | Xong | `bf38081` |
| P1-T1.2 Rift economy 0/0.0 + cooldown + migration 4→5 | Xong | `dc4e52e` |
| P1-T1.3 findConsole 4096 budget + cooldown 4 tick | Xong | `4ddc8bc` |
| P1-T1.4 Strict gate test + STRICT boot CI | Xong | `622e905` |
| P1-T1.5 Pháp lý (licenses từ upstream thật, gỡ Geyser jar) | Xong | `fef2f0f` |
| P2 7 minor fixes | Xong (2 mục verified no-op: ichor spring đã 1 lần, placePlant không tồn tại) | `0c28b77` |
| P3 Fan-made gates mặc định FALSE | Xong | `aa2a46e` |
| P7 Docs (ARCHITECTURE/CONFIG/CHANGELOG/gap-analysis/README/parity) | Xong | `71dc39f` |
| P8 jacoco report-only + FanMadeGateTest | Xong | `7d19862` |
| P5 Brigadier + i18n toàn bộ | **HOÃN** — thiếu giờ phiên; Messages + lang vi/en đã có sẵn từ trước | — |
| P6 dimension 1:1 UX + rifts.json | **HOÃN** — thiếu giờ phiên; enter/return hiện giữ tọa độ qua SiftDimensionManager, persistence rift chưa có | — |
| P4 Refactor god class | **HOÃN theo quy tắc "làm cuối, dừng ở bước xanh"** — SpearThrowHandler là phần trích đầu tiên; phần còn lại cần phiên riêng | — |
| P9 Stretch test-provider | **KHÔNG LÀM** | — |

## Các quyết định mặc định đã áp dụng
- `rift.reward-drops: 0`, `rift.rift-key-chance: 0.0`, `rift.reward-cooldown-minutes: 30` (mới).
- `content.prism_warden / sift_standin_mobs / sift_tide / rift_walker_waves: false` (mới);
  `content.spire_tyrant` vốn đã false. 5 mob lõi mod miễn trừ khỏi wildlife gate.
- Locale `vi` mặc định, fallback `en` (đã có sẵn từ trước, giữ nguyên).
- `config-version` 4→5, migration add-only.
- Mọi tên key PDC cũ giữ nguyên.

## Việc cần người quyết
1. Số liệu cân bằng cuối (reward drops, key chance, tần suất boss) — hiện 0/0.0 theo kế hoạch.
2. Quyền asset (texture Dark Sniffer, nhạc Rift/Wellspring) trước khi đóng gói resource pack.
3. Ghi chú "note block vô hiệu server-wide" trên trang Modrinth release cần sửa (code không làm vậy).
4. Có giữ nội dung fan-made trong catalog dài hạn hay không.

## Rủi ro còn lại
- findConsole: budget ±2/block giả định console liền kề sát — một console trải rộng >2 block
  giữa các phần sẽ không gom đủ (đã sớm dừng, không scan tràn; chưa test với world thật).
- Strict boot CI: bước mới, lần chạy đầu sẽ xác nhận thực tế trên runner.
- ConfigMigrationTest chỉ cập nhật assertion số version; kịch bản add-only của 4→5 chưa có test riêng.

## Diff stat
Xem `git diff --stat main...overnight/hardening`.
