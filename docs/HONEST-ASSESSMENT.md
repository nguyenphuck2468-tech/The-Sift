# Đánh giá trung thực The Sift — dựa trên code, không dựa trên hy vọng

Ngày: 2026-11-01 · Branch: `overnight/hardening` @ `025f03b` · So với mod `Arta48/Mielons-The-Sift` @ `cdd675e` (1.1.2)

---

## Số liệu thật (grepped, không nhớ)

| Chỉ số | Giá trị |
|---|---|
| File Java sản phẩm | 53 |
| File test | 35 (145 @Test method) |
| content-spec | 115 items, 14 blocks, 22 mobs (36 gắn fanmade) |
| Advancement JSON | 22 (44 translate keys × 2 locale) |
| Worldgen JSON | 26 feature, 32 placed, 3 carver, 15 noise, 21 density, 13 structure, 2 set, 2 pool |
| Biome trong biome_source | 15 (10 mod + 5 fan-made) |
| Recipe (Java-registered) | 47 (42 Siftite + 5 Ichor) |
| PDC keys | 70 unique |
| CI boot steps | 2 (fallback + strict) |
| `Messages.send/get` calls | 23 (trong 8 files) |
| Hardcode `sendMessage` còn lại | ~80 (trong 10 files, 51 ở Plugin.java) |
| `Reflect` cache call sites | 57 (trong 4 bridge files) |

---

## 1. Dimension và Worldgen — 8/10

**Có và hoạt động (CI boot xanh = registry parse + server Done):**
- 15 biome đầy đủ trong biome_source, noise router 21 density functions, 15 noises, 3 carvers wired vào mọi biome
- 26 configured + 32 placed features (ore + sculk + thực vật + hồ Ichor ngầm), 3 block tags
- 13 structures (portal NBT + JSON), 2 structure_sets, 2 template_pools
- 17 custom Java feature type do `SiftEcologyManager` sinh (Soul Canyon, Monolith, Sniffer Cave, Fossil, Ichor Spring, Cliff Arch, Abandoned Portal...), chạy cả strict + fallback
- `check_terrain_refs.py` chạy trong CI pre-check, xác thực mọi ref `thesift:` bao gồm tags, structures, NBT

**Chưa có:**
- Bầu trời động, hiệu ứng client-side — Paper không can thiệp
- Exact worldgen parity cùng seed — cần playtest

---

## 2. Mob — 7/10 gameplay, 3/10 hình ảnh

| Mob | Gameplay (code verified) | Hình ảnh |
|---|---|---|
| **Singer** | Allay stand-in; Goat Horn đánh thức, resonance, Soul Event, barter 20 mục, advancement | Allay model — không có hình dáng mod |
| **Echo Golem** | Iron Golem stand-in; bond Echo Shard, **quét dần vùng Soul Block có giới hạn ngân sách → nhặt → mang đến Singer (32 block) hoặc trả chủ** (như mod), advancement `like_father_and_son` / `friend_of_the_sift` | Iron Golem model — không có animation đi lại |
| **Blub** | Rabbit stand-in; **thuần 1/3**, **thu hút bởi Soul Block**, follow, **defend owner**, **stay/follow toggle**, advancement `blub` | Rabbit — không có tai cụp theo máu |
| **Sifter** | Ravager stand-in; **đàn 3–5 (per-herd anchor, prune rỗng)**, delayed bite attack theo mod, spawn Overgrown Forest, Raw Sifter Meat → Cooked, advancement | Ravager — không có model/animation cắn riêng |
| **Dark Sniffer** | Sniffer stand-in; **nhiễm sculk → 30s biến đổi**, hostile, Sonic Sniff kéo mục tiêu, đào Sculkflower Seeds, **Warden không đánh**, combat damage theo config | Sniffer — không có model nhiễm sculk |

**Điểm yếu thật:** 5 mob đều dùng vanilla entity — không có model, texture, animation riêng. Đây là giới hạn Paper vs Fabric/NeoForge, không phải thiếu nỗ lực.

---

## 3. Items, Block, Progression — 8.5/10

**Verified trong code:**
- **Overgrown Willow**: 21 entry (log → shelf), đầy đủ recipe vanilla shapes, cherry stand-in
- **Siftslate**: 12 entry (cobbled → reinforced), deepslate stand-in
- **5 ore**: coal/diamond/emerald (datapack data-driven), charoite/siftite (Java + marker fallback)
- **Siftite trọn bộ**: sword/axe/pickaxe/shovel/hoe/spear/helmet/chestplate/leggings/boots/template, smithing upgrade Netherite, KB-resist 0.15, Spear ném được với `spear-damage` + `spear-reach`
- **Charoite**: thay Lapis trong enchanting, +20 XP, +1 enchant level, chặn Anvil combine
- **Ichor**: bottle/bucket/cauldron/snow/snowball/shard/glass/pane, dripstone refill, weather effects, không infinite source
- **Mini Rift Recovery**: Siftite gear trả lại sau respawn, lưu qua restart
- **Singer barter**: 20 mục theo mod
- **47 recipe** Java-registered qua `ownedRecipeKeys` (reload-safe)

**Điểm yếu:** mọi block/item đều là vanilla stand-in khi không có provider — hình dạng giống vanilla, không giống mod. Cần resource pack + provider (Nexo/ItemsAdder/Oraxen).

---

## 4. Portal và Rift — 8/10

**Portal (code verified):**
- Goat Horn → Singer xuất hiện → tự đặt 8 Sonorous Deepslate → Note Block tune → chuỗi `1,3,7,6,5,2,4,8` → portal mở
- Gate manager có `ownsSonorous()` phân quyền với RiftManager
- Persistence qua chunk PDC, readGate quét 3×3 chunk
- Cooldown server-wide sau melody thành công

**Rift (code verified):**
- Hai chiều, 5 phút (portal 900s), tự nhiên 6–14 min max 2
- Transport: players, mobs, **vehicles + riders** (remount sau teleport), **projectiles giữ momentum** (re-apply 1 tick), dropped items
- **rifts.json persistence**: ghi atomic (temp+ATOMIC_MOVE), load sau khi world ready, skip expired, portal gate=-1 restore full duration
- Advancement: rifter, rift_resonance (empty jukebox 16 block)

---

## 5. Advancement — 8/10

22 JSON (mod 20 + 2 fan-made), **44 translate keys × en_us + vi_vn** (client-language rendering).

**8/8 mod triggers wired (code-verified):**
1. Blub taming (1/3 roll)
2. Food chain (tamed Blub kills Dark Snifter)
3. Planting_the_dark (seeds on Sculk)
4. Spicewood (ignite wood in dimension)
5. Smells_creepy (obtain seeds — pickup + craft)
6. Friend_of_the_sift (Echo Golem returns Soul Block)
7. Like_father_and_son (Echo Golem delivers to Singer)
8. False_alarm (Ichor Snowball lands near Warden)

Kèm 6 trigger vanilla (Ancient City visit, biome visits) handled by datapack.

---

## 6. Provider — 8/10 kiến trúc, 3/10 kiểm chứng

**Code verified:**
- SPI: `SiftItemProvider`, `SiftMobProvider`, `SiftBlockProvider` — bất kỳ plugin nào đăng ký qua ServicesManager đều được dùng
- 6 bridge: Nexo, ItemsAdder, Oraxen, MythicMobs, EliteMobs, ModelEngine — **tất cả qua `Reflect` cache** (57 call sites), không bao giờ `Class.forName` lặp
- `content-spec.json` → `generateProviderDefinitions` sinh YAML cho Nexo/IA/Oraxen/MythicMobs
- 36 ID fan-made gắn cờ, `ProviderReadiness.mandatory()` loại khi gate tắt
- Strict mode: `dimensionAllowed()` chặn khi thiếu backend, test matrix xanh
- CI: boot strict không provider → plugin bật, dimension khóa, không exception

**Chưa xác minh:** chưa có provider nào được parse YAML thật trên server thật. `test-provider` là stub (PAPER/no-op). Tất cả cell "Live checked" trong parity-matrix đều trống.

---

## 7. Localization — 7/10

**Có:**
- `Messages` catalog: `locale: vi` mặc định, `en` fallback, `messages.yml` override per-key
- 22 advancement dịch đầy đủ vi/en (44 keys × 2)
- 23 `Messages.send/get` call trong 8 files
- Blub, Echo Golem, Ichor, tide, spawn eggs, dimension, gate — đều qua catalog

**Thiếu:**
- ~80 hardcoded `sendMessage` còn lại (51 ở Plugin.java, 10 ở GateManager)
- Tên item/mob hiển thị bằng `setDisplayName("§d" + name)` — EN hardcoded trong Java, không qua Messages
- Không có per-player locale

---

## 8. Hiệu năng — 7/10 thiết kế

**Đã làm (code verified):**
- Wildlife index: UUID buckets qua EntityAdd/Remove events, refile khi tag xuất hiện muộn, prune empty buckets, seed 1 lần/world
- Reflect cache: class + method handles, absent plugins được cache tránh re-probe
- findConsole: 4096 lookup budget + 4-tick cooldown (từ ~2.3M/click)
- Wildlife tick: 5 loops resolve qua index thay vì `getEntities()` full scan
- Dark Sniffer combat: budget-capped sonic wave, config-driven damage
- Chunk decoration: marker-first check, backoff retry, entity cleanup on failure

**Chưa đo:** MSPT, heap, GC, entity count, chunk gen time với nhiều người chơi. Cần server thật.

---

## 9. Test và CI — 7.5/10

**145 @Test methods, 35 test class.** CI chạy: unit tests + **Paper 26.3 boot (fallback mode)** + **Paper 26.3 boot (strict mode, dimension locked)** + terrain reference check + datapack integrity + config YAML regression.

**Đã test:** WildlifeIndex (refile/scrub/prune), SpearThrowHandler lifecycle, ConfigMigration 4→5, ConfigYamlRegression (duplicate keys), ProviderStrictness matrix, FanMadeGate exemption, RiftPersistence skipExpired, DatapackIntegrity (biome tags, carver refs, ore wiring), ChunkRetryBackoff, ConfigMigration legacy, ProviderAccessGuard, ResonanceManager, SoulBlockListener, SonorousConsoleTracker, SingerBarter, MeatFoodChain, SiftiteRecipe, NewContent, CommandSuggestions, AncientCityFrame, AncientCityMelodyTracker, EchoGolem...

**Thiếu:** integration test với provider thật, worldgen cùng seed, portal/rift gameplay end-to-end, resource pack delivery.

---

## 10. Điểm còn thiếu thật sự

| Nhóm | Mức độ | Lý do |
|---|---|---|
| Model/texture/animation mob | 🔴 | Cần GeckoLib/ModelEngine + asset có quyền — Paper không tự render |
| Bầu trời động, shader | 🔴 | Client-side — Paper không can thiệp |
| Ichor là fluid thật | 🔴 | Paper không hỗ trợ custom fluid registration |
| Âm thanh gốc (Rift disc, 8 nốt Sonorous, ambient) | 🟡 | Cần OGG files + quyền phân phối |
| ~80 hardcoded sendMessage | 🟡 | Pattern có sẵn, cần thời gian wire |
| Echo Golem hành trình Soul Canyon đầy đủ | 🟡 | Đã có quét dần vùng Soul Block và tìm cụm trong các chunk đang tải; chưa có route/landmark Soul Canyon đầy đủ |
| Integration test end-to-end | 🟡 | Unit test mạnh, chưa có test gameplay trên server thật |
| God class SiftEcologyManager (2189 dòng) | 🟡 | Đã trích 3 class, còn DarkSnifferController + ChunkDecorationManager |
| Brigadier command tree | 🟡 | Hiện if-chain, không phải bug nhưng không tối ưu |

---

## 11. Điểm tổng

| Tiêu chí | Điểm | Căn cứ |
|---|---|---|
| Độ rộng nội dung gameplay | **8.5** | 115 items, 22 mobs, 15 biome, 22 advancement, 47 recipe, portal/rift/ichor/siftite/charoite progression |
| Fidelity hành vi với mod | **6.5** | Tame/attract/delivery/herb/infection/sonic đều có code; model/animation/stand-in là giới hạn |
| World generation | **7.5** | Full noise port + carvers + features + structures + fossils + landmarks, 15 biome generating |
| Kiến trúc | **7.5** | WildlifeIndex, BlubController, SpearThrowHandler, Reflect, Messages, ProviderReadiness —god class còn 2189 dòng |
| Provider ecosystem | **8.0** | 6 bridge + SPI + Reflect cache + fanmade metadata + strict/fallback matrix tested |
| Test và CI | **7.5** | 145 tests, 2 boot steps, terrain check, datapack integrity, YAML regression |
| Localization | **7.0** | Advancement 44×2 keys, Messages vi/en + override, ~80 hardcode còn |
| Resource pack | **3.0** | Task + bridge có, PNG excluded, asset chưa xác minh |
| Release readiness | **5.5** | Cần playtest + asset quyết định + provider live check |

**Tổng: 6.8/10** — đi lên từ 6.1 → 6.4 → 6.6 → 6.8 qua 4 vòng review và fix.

---

## 12. Điều tôi muốn nói thẳng

**Plugin này đã vượt qua giai đoạn "thêm vài item vào Paper".** Nó có dimension tự sinh 15 biome, 5 mob với hành vi cốt lõi được code thật (không chỉ đặt tên), portal puzzle hoàn chỉnh, Rift hai chiều chở được xe, hệ thống Ichor 8 loại item, progression Siftite/Charoite với enchanting, 22 advancement đúng trigger, và một provider ecosystem cho phép thay vanilla stand-in bằng custom content mà không sửa lõi.

**Nhưng nó chưa phải bản thay thế mod.** Khi bạn vào server, bạn thấy Rabbit thay vì Blub, Iron Golem thay vì Echo Golem, vanilla cherry log thay vì Overgrown Willow Log. Gameplay logic đúng, nhưng hình dạng và âm thanh là vanilla. Đây là giới hạn Paper — plugin không thể đăng ký entity type hay fluid type mới. Cần resource pack + asset có quyền.

**CI xanh không có nghĩa mọi thứ hoạt động.** Nó chứng minh code biên dịch, unit test pass, và Paper boot được datapack không lỗi registry. Nó không chứng minh Echo Golem tìm đúng Soul Block trong Soul Canyon, hay portal mở đúng khi người chơi chơi melody trong Ancient City thật. Cần server thật.

**Điều tốt nhất bạn có thể làm bây giờ:** boot server với PR #12, dùng `/sift doctor`, vào dimension, đào quặng, thử portal — rồi quay lại nói tôi cái gì hoạt động và cái gì không. Tôi sẽ fix từ đó, chính xác hơn bất kỳ static review nào.
