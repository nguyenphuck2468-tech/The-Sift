# The Sift - tài liệu bàn giao

Tài liệu này dành cho người tiếp nhận phát triển plugin The Sift. Cập nhật mới
nhất ở cuối tài liệu ghi commit và trạng thái hiện hành; các ghi chú cũ bên dưới
được giữ để làm lịch sử khi cần.

## 1. Mục tiêu của repository

Repository hiện là một project Paper plugin duy nhất, dùng Java 25 và Gradle.
Các resource runtime được gom vào cùng source tree:

```text
src/main/java/                    Source Java
src/main/resources/plugin.yml     Plugin metadata
src/main/resources/config.yml     Runtime configuration
src/main/resources/datapack/      Datapack và worldgen data
src/main/resources/pack/          Java resource pack
src/main/resources/bedrock/       Unverified embedded Geyser extension (excluded from Paper JAR)
build.gradle                      Gradle build
README.md                         Tổng quan và test flow
```

Package plugin cũ dạng `.class`, bản mod Fabric reference và file ZIP lớn đã
được loại khỏi repository để tránh có nhiều nguồn sự thật. Commit cleanup hiện
đã được đẩy lên GitHub branch `main`.

## 2. Công nghệ và target

- Java 25.
- Gradle 9.x.
- Paper API mặc định: `26.3.build.218-stable`.
- Target kế hoạch: Minecraft `27.1` (bản tương lai; schema/API chưa công bố).
- Dimension The Sift hiện là prototype đóng gói theo data format 26.3.
- Project chuyển sang Paper plugin loader để đăng ký datapack trước khi world
  được nạp; plugin.yml vẫn có để giữ metadata/permission cho Bukkit tooling.

Build bằng wrapper đã ghim Gradle 9.7.1:

```bash
./gradlew --no-daemon clean build
```

JAR tạo tại:

```text
build/libs/TheSift-foundation-2.0.0-foundation.jar
```

Có thể thay Paper API dependency khi target API đã xác định:

```bash
gradle build -PpaperApiVersion=<paper-api-version>
```

## 3. Kiến trúc source

### Entrypoint

[TheSiftFoundationPlugin.java](./src/main/java/com/gamepathics/thesift/foundation/TheSiftFoundationPlugin.java)

Chịu trách nhiệm:

- load config và migration;
- khởi tạo content modules;
- đăng ký listener/recipe;
- command `/sift`;
- khởi động resonance scheduler.

### Content registry

[ContentId.java](./src/main/java/com/gamepathics/thesift/foundation/ContentId.java)

[ContentRegistry.java](./src/main/java/com/gamepathics/thesift/foundation/ContentRegistry.java)

Đây là nơi định danh các nhóm content. Khi thêm item/entity mới, phải thêm ID
và config flag tương ứng, không hard-code tên rải rác trong listener.

### Ichor

- [IchorContent.java](./src/main/java/com/gamepathics/thesift/foundation/IchorContent.java)
- [IchorState.java](./src/main/java/com/gamepathics/thesift/foundation/IchorState.java)
- [IchorListener.java](./src/main/java/com/gamepathics/thesift/foundation/IchorListener.java)

Hiện có:

- Ichor Bottle;
- Ichor Glass/Panes;
- Ichor Snow/Snow Block identity;
- cauldron level persistence;
- mỗi Ichor Bottle chỉ nạp một charge; rút chai tuân theo bottle-cost;
- particle/sound;
- identity-preserving drops.

### Charoite và Siftite

- [SiftiteContent.java](./src/main/java/com/gamepathics/thesift/foundation/SiftiteContent.java)
- [SiftiteListener.java](./src/main/java/com/gamepathics/thesift/foundation/SiftiteListener.java)

Hiện có:

- Charoite item/ore prototype;
- Siftite nugget/ingot;
- Siftite armor/tools/spear identity;
- nâng cấp từ Netherite với Siftite Upgrade Template và Siftite Ingot;
- chặn nguyên liệu Siftite bị dùng trong crafting/smithing vanilla theo Material;
- config balance;
- `/sift give`.
- Tốc độ đào cộng thêm cho công cụ và knockback resistance cho giáp; thuộc tính
  chỉ áp dụng cho đúng nhóm trang bị, không áp lên nugget/ingot.

Charoite Ore và Siftite Ore sinh ra được đánh dấu trong chunk PDC; block cùng
Material do người chơi đặt không tự nhận drop ore đặc biệt. Ore chuẩn (sắt,
than, đồng, redstone, lapis, kim cương, vàng) được thêm vào dimension để có
thể chơi survival khi biome JSON không khai báo placed features.

### Singer và Resonance

[ResonanceManager.java](./src/main/java/com/gamepathics/thesift/foundation/ResonanceManager.java)

Hiện có:

- Singer prototype dùng Allay Bukkit entity;
- Pulse gây 1 damage (nửa tim) mặc định và Slowness I trong 3 giây;
- `resonance.singer.attack-enabled` tắt damage/debuff; Charoite được tính khi
  mang trong inventory/hotbar, offhand hoặc armor, không tính ender chest;
- Creative/Spectator không bị ảnh hưởng;
- persistent entity ID;
- Echo Golem prototype có thể bond bằng Echo Shard và lưu Soul Block;
- boss bar;
- Charoite drop;
- `/sift summon singer`.

Không dùng NMS. Khi Paper target ổn định và resource pack hoàn chỉnh, có thể
thay runtime prototype bằng custom entity implementation.

## 4. Lệnh test hiện tại

```text
/sift doctor
/sift reload
/sift dimension
/sift return
/sift give ichor_bottle <player>
/sift give charoite <player>
/sift give charoite_ore <player>
/sift give siftite_nugget <player>
/sift give siftite_ingot <player>
/sift give siftite_sword <player>
/sift give siftite_upgrade_smithing_template <player>
/sift summon singer <player>
```

Các lệnh yêu cầu permission `thesift.foundation.admin`, mặc định là operator.

## 5. Test smoke trên server

1. Build JAR bằng Gradle.
2. Cài JAR vào server Paper test.
3. Không cài song song plugin TheSift cũ vì plugin identity đều là `TheSift`.
4. Khởi động server.
5. Chạy `/sift doctor`.
6. Test Ichor:
   - lấy Ichor Bottle;
   - dùng lên cauldron;
   - dùng Glass Bottle để rút Ichor;
   - restart server và kiểm tra level không mất.
7. Test Siftite:
   - lấy Charoite/ingot;
   - kiểm tra item ID;
   - thử smithing recipe.
8. Test Singer:
   - summon Singer;
   - đứng trong/ngoài range;
   - kiểm tra damage, slowness, boss bar;
   - cầm Charoite để kiểm tra protection;
   - hạ Singer để kiểm tra drop.

## 6. Những điểm chưa hoàn thiện

Đây là foundation/reconstruction, chưa phải bản production parity hoàn chỉnh.

- Resource pack mới chưa có model/texture hoàn chỉnh cho toàn bộ Ichor,
  Charoite, Siftite và Singer.
- Block custom hiện dùng vanilla material làm nền.
- Ichor state nằm trong `ichor-state.yml`; vị trí Soul/Charoite/Siftite block
  được đánh dấu bằng chunk PersistentDataContainer.
- Custom block vẫn dùng vanilla material khi không cấu hình provider.
- Siftite spear hiện dùng Netherite Sword làm base material.
- Singer và Echo Golem vẫn là vanilla entity proxy.
- Singer đang dùng Allay prototype. Pulse damage/debuff/config và kiểm tra
  Charoite đã nối vào gameplay; Charoite cần có persistent content tag.
- Echo Golem mới có thao tác bond và cất/lấy Soul Block cơ bản; chưa có AI tự
  tìm Soul Canyon/Soul Block như mô tả trong mod cộng đồng.
- Cầu nối custom block nhận `SiftBlockProvider` qua Bukkit ServicesManager và
  có adapter tùy chọn cho Nexo, ItemsAdder, Oraxen. Cầu nối item/mob cũng có
  provider API; việc khai báo adapter không đồng nghĩa mọi ID/AI/model từ plugin
  ngoài tự tương thích. Cần map ID và kiểm tra trên server cài plugin tương ứng.
- Dimension The Sift hiện có năm custom biome (Pink Meadows, Bulb Forest,
  Carapace, Crystal Spires, Shattered Peaks), multi-noise biome selection,
  vanilla Overworld noise, biome color, procedural surface decoration, mob
  proxies, Singer/Echo Golem/Prism Warden/The Sifter encounters, natural Rift
  events, and a cycling Sift Tide.
  Terrain and entities use vanilla substitutes and are an intentionally rough
  prototype, not 27.1 parity.
- Resource pack assets are bundled under `src/main/resources/pack`; use
  `gradle siftResourcePack` to produce
  `build/distributions/TheSift-resource-pack.zip`. Players need the pack
  installed/served for custom item models to render.
- Trạng thái cũ “25/31 test fail do Mockito” đã được khắc phục: Mockito được
  nâng cấp và fixture dùng MockBukkit. Xem IMPLEMENTATION_REPORT.md để biết
  kết quả kiểm chứng hiện tại.
- `/sift reload` reload config và công thức; cần restart để bật/tắt module vì
  listener và scheduler được khởi tạo theo content flags lúc enable.
- Config target `27.1 (planned)` là kế hoạch, không khẳng định tương thích
  trước khi Paper/Minecraft 27.1 được phát hành và kiểm tra.

## 7.1 Dimension prototype

Dimension and biome files live at `src/main/resources/sift_dimension_datapack/`.
`TheSiftBootstrap` registers this isolated bundled datapack as
`TheSift/dimension`; `/sift dimension` creates/loads the world using the
`thesift:the_sift` key. This follows Paper's datapack-discovery lifecycle and
Paper 26.1+ `WorldCreator.ofKey` world keys. It is an implementation prototype
for planned Minecraft 27.1, not a tested 27.1 implementation.

Reference material reviewed:

- The repository's historical Fabric reference archive `The Sift 1.1.2`
  (26.3), including its `dimension/the_sift.json` and
  `dimension_type/the_sift.json` definitions.
- Existing plugin resources under `src/main/resources/datapack` and
  `src/main/resources/pack` for biome colors, worldgen ideas, item models, and
  mob model assets.
- Paper datapack discovery docs:
  https://docs.papermc.io/paper/dev/lifecycle/datapacks/
- Paper world creation API:
  https://jd.papermc.io/paper/26.3/org/bukkit/WorldCreator.html
- Mojang's September 2026 announcement:
  https://www.minecraft.net/en-us/article/mclive_sept2026_recap

## 7. Thứ tự công việc đề xuất

### Ưu tiên 1 - ổn định foundation

1. Thêm unit tests cho ID, config migration và item factory.
2. Tách Ichor runtime state khỏi config.
3. Sửa lifecycle của `/sift reload`.
4. Xác minh Paper target chính thức.
5. Thêm CI build trên Java 25.

### Ưu tiên 2 - hoàn thiện asset parity

1. Model/texture Ichor.
2. Model/texture Charoite và Siftite.
3. Singer model/animation.
4. Bedrock mappings.
5. Sound và localization.

### Ưu tiên 3 - gameplay

1. Echo Golem guardian.
2. Resonance biome/structure.
3. Rift open/active/closing state machine.
4. Rift boss bar, phase, reward và restart recovery.
5. Advancement/loot/guide.

## 8. Quy tắc phát triển

- Không sửa trực tiếp `.class` hoặc thêm lại plugin package cũ.
- Không dùng NMS nếu Bukkit/Paper API đủ đáp ứng.
- Mỗi item/entity phải có persistent ID.
- Mỗi content mới cần có config flag và `/sift doctor` visibility.
- Recipe phải có `NamespacedKey` của plugin.
- Không ghi secret/token vào repository.
- Build trước khi commit:

```bash
./gradlew --no-daemon clean build
git diff --check
```

## 10. Trạng thái Giai đoạn 1 (2026-10-24)

- Các commit đã đẩy lên `main`: `213dead` (Ichor migration), `594d682`
  (wrapper/CI), `baa1fd7` (Rift Key formatting), `e6db80c` (Mockito/fixtures),
  `3da3e21` (ExactChoice), `618548a` (advancement fixes), `35d72c8` (chunk
  metrics), `87241ed` (Linux executable bit), `30f6f41` (Rift/Singer regression
  checks). Giai đoạn 1 report commit bổ sung được ghi trong report.
- Ichor legacy keys migrate idempotently sang `ichor-state.yml`; config cũ
  được backup trước và key chỉ bị xóa sau khi state save thành công.
- CI chạy `./gradlew --no-daemon clean build`; test được thực thi một lần qua
  lifecycle `build`.
- Regression hiện xác nhận: Rift Key advancement được gọi; template không bị
  tính là gear; The Sifter chỉ cấp advancement của chính nó; uống Ichor qua
  custom interaction cấp advancement `storm`. Full local build hiện tại: 52 tests.
- `rift_block`: không có block producer; handler vô hiệu và marker trên Rift
  Walker entity đã được gỡ. Test đảm bảo không còn handler phá block.
- Singer/Charoite: pulse giờ gây damage/debuff có thể tắt qua config; Charoite
  có tag trong inventory/hotbar, offhand hoặc armor chặn cả hai hiệu ứng. Unit
  tests chạy; chưa xác minh trên server Paper thật.
- Chunk benchmark tạm có thể bật bằng
  `debug.chunk-generation-metrics.enabled: true`; log đo thời gian tạo chunk và
  số operation attempts. Mặc định tắt. Đây là phép đo thô ở cấp generation
  stage/điểm thử bề mặt, chưa phải bộ profiler BlockPopulator.
- Kiểm chứng live Paper server và các provider Nexo/ItemsAdder/Oraxen/MythicMobs/
  EliteMobs/ModelEngine còn cần thực hiện trên server cài các plugin đó.
- GitHub Actions từng fail do `gradlew` thiếu executable bit trên Linux runner;
  sau commit `87241ed`, run CI đã xanh. Các commit mới sau đó cũng tự kích hoạt
  workflow; kiểm tra trạng thái run mới nhất trong GitHub trước khi release.
- Tài liệu provenance/giấy phép đang được rà soát trong IMPLEMENTATION_REPORT.md;
  không thêm LICENSE toàn repo khi chưa có xác nhận của chủ sở hữu.

## 9. Git và release

Kiểm tra trạng thái:

```bash
git status

## 10. Step 1 follow-up (2026-10-24)

- Singer pulse: default 1.0 damage every 5 seconds and Slowness I for 3 seconds;
  configurable and disableable. Tagged Charoite in inventory/hotbar, offhand,
  or armor grants immunity; ender chest does not count. Creative/Spectator are
  ignored. Tests pass; no live server check yet.
- `rift_block`: no placed-block producer exists, so the ineffective block
  handler and entity marker were removed. A regression test checks this state.
- ItemsAdder block recognition now requires the configured namespaced ID. A
  fixture verifies both ID match and mismatch; no live ItemsAdder test.
- Ecology chunk markers record in-progress and complete states. Runtime
  failures clear the marker and remove entities spawned during the attempt;
  the chunk may retry. Partial block changes are not transactionally rolled
  back.
- `TheSiftGeyser.jar.embedded` has unknown source/terms and no code references.
  It remains in the source tree pending owner direction, but the build excludes
  it from the Paper plugin JAR. See `IMPLEMENTATION_REPORT.md` and
  `docs/step1-paper-jar-inventory.txt`.
- Config audit is in `IMPLEMENTATION_REPORT.md`. No keys were deleted; any
  cleanup requires owner approval and a versioned migration plan.
- `NOTICE.draft.md` records only the current source investigation. No LICENSE
  was created. Live server, external-provider validation, and Bước 2 tasks are
  still pending; do not start Bước 2 before owner confirmation.
git log --oneline -5
```

Push commit hiện tại:

```bash
gh auth login
git push origin main
```

Remote hiện tại:

```text
https://github.com/nguyenphuck2468-tech/The-Sift
```

Khi tạo commit mới, giữ commit message ngắn và thêm trailer:

```text
Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```
