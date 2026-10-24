# The Sift - tài liệu bàn giao

Tài liệu này dành cho người tiếp nhận phát triển plugin The Sift sau commit
`7b1e0f0`.

## 1. Mục tiêu của repository

Repository hiện là một project Paper plugin duy nhất, dùng Java 25 và Gradle.
Các resource runtime được gom vào cùng source tree:

```text
src/main/java/                    Source Java
src/main/resources/plugin.yml     Plugin metadata
src/main/resources/config.yml     Runtime configuration
src/main/resources/datapack/      Datapack và worldgen data
src/main/resources/pack/          Java resource pack
src/main/resources/bedrock/       Geyser support artifact
build.gradle                      Gradle build
README.md                         Tổng quan và test flow
```

Package plugin cũ dạng `.class`, bản mod Fabric reference và file ZIP lớn đã
được loại khỏi repository để tránh có nhiều nguồn sự thật. Commit cleanup hiện
đang ở local branch `main`; nếu chưa push được thì cần xác thực GitHub trước.

## 2. Công nghệ và target

- Java 25.
- Gradle 9.x.
- Paper API mặc định: `26.3.build.218-stable`.
- Target kế hoạch: Minecraft `27.1` (bản tương lai; schema/API chưa công bố).
- Dimension The Sift hiện là prototype đóng gói theo data format 26.3.
- Project chuyển sang Paper plugin loader để đăng ký datapack trước khi world
  được nạp; plugin.yml vẫn có để giữ metadata/permission cho Bukkit tooling.

Build:

```bash
gradle --no-daemon clean build
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
- refill và bottle cost;
- particle/sound;
- identity-preserving drops.

### Charoite và Siftite

- [SiftiteContent.java](./src/main/java/com/gamepathics/thesift/foundation/SiftiteContent.java)
- [SiftiteListener.java](./src/main/java/com/gamepathics/thesift/foundation/SiftiteListener.java)

Hiện có:

- Charoite item/ore prototype;
- Siftite nugget/ingot;
- Siftite armor/tools/spear identity;
- smithing recipes;
- config balance;
- `/sift give`.

### Singer và Resonance

[ResonanceManager.java](./src/main/java/com/gamepathics/thesift/foundation/ResonanceManager.java)

Hiện có:

- Singer prototype dùng Allay Bukkit entity;
- persistent entity ID;
- timed sonic pulse;
- damage/slowness;
- Charoite protection;
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
- Ichor state đang lưu trong config; về lâu dài nên chuyển sang storage riêng
  hoặc PersistentDataContainer phù hợp.
- Charoite ore đang prototype bằng Amethyst Block.
- Siftite spear hiện dùng Netherite Sword làm base material.
- Smithing template hiện dùng vanilla Netherite Upgrade Template.
- Singer đang dùng Allay prototype.
- Chưa có Echo Golem.
- Chưa có Rift event/persistence/reward.
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
- Unit tests có trong source nhưng 25/31 hiện fail trong môi trường test do lỗi
  khởi tạo Mockito; build JAR và Paper server smoke test chạy được.
- `reload` hiện reload config/flags nhưng không teardown và đăng ký lại toàn bộ
  module. Nếu thay đổi module flag lúc runtime, nên restart server.
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
gradle --no-daemon clean build
git diff --check
```

## 9. Git và release

Kiểm tra trạng thái:

```bash
git status
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
