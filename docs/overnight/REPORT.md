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

## Vòng review sau hardening (2026-11-01)

4 bug P1 xác thực được từ review độc lập đã sửa trong commit 8663630 và b1ec874:
1. config.yml duplicate rift keys (SnakeYAML last-wins → 3/0.3 đè 0/0.0)
2. wildlife index không re-file Blub/Sifter (AI hai mob lõi bị đói tick)
3. sifter herd guard chặn spawn khi chỗ đứng là AIR
4. DatapackManagerBridge tìm tên colon trong khi Paper báo slash form

Kèm: SpearHandler dùng kết quả damage() làm chuẩn, creative mode đúng javadoc,
spear-reach giờ điều chỉnh vận tốc, giáo despawn không va chạm vẫn drop đúng một lần,
note-console map dọn khi quit. Docs đồng bộ (config-version 5, biome fan-made vẫn sinh).

## Vòng review 06:37 (2026-11-01) — các mục đã xử lý thêm

- Ichor spring double placeBlock: XÁC NHẬN CÒN SÓT (dòng 708-709 giống hệt) → đã xóa (b366f59)
- Test hồi quy YAML: ConfigYamlRegressionTest đọc config.yml SHIP thật qua YamlConfiguration,
  khẳng định 0/0.0 + cooldown + 5 cờ fan-made + locale vi (b366f59)
- Dimension 1:1 enter/return: /sift dimension vào đúng X/Z của người chơi (thay vì 0,0),
  lưu điểm Overworld vào PDC, /sift return về đúng chỗ đó, fallback surface nếu bị chắn (1e03770)
- content-spec metadata: 36 entry được gắn cờ fanmade; generator emit FANMADE_IDS;
  ProviderReadiness.mandatory() bỏ các ID fan-made khi content.sift_standin_mobs=false —
  server chỉ chơi phần mod không cần provider cho nội dung đã tắt (44905aa)
- i18n: enter/return/no-overworld qua Messages vi/en (e2398dc)

## Bổ sung 01/11 (theo yêu cầu "làm tiếp")

- P4.1: WildlifeIndex tách khỏi SiftEcologyManager + 5 unit test hồi quy
  (refile từ bucket vanilla sang species, prune bucket rỗng, scrub, copy-safe) — a4e8ead
- P6.4: rifts.json persistence — rift ACTIVE lưu nguyên tử (temp+move) khi mở/đóng/shutdown;
  loadRifts sau khi world sẵn sàng, bỏ mục hết hạn/world mất, file hỏng chỉ warn — d2f0d05
- P6.5: RiftPersistenceTest.skipExpired — b366f59 lineage
- P6.1: dimension 1:1 enter/return (1e03770); P6.2: fanmade metadata + readiness filter (44905aa);
  P6.3: i18n dimension (e2398dc); ichor spring dedupe + ConfigYamlRegressionTest (b366f59)

Còn lại: P4 tiếp tục (BlubController, DarkSnifferController, ChunkDecorationManager),
P5 Brigadier, P9 test-provider — cần phiên tiếp theo.

## Diff stat
Xem `git diff --stat main...overnight/hardening`.
