# Báo cáo đối chiếu: plugin The Sift so với mã nguồn mod

Nguồn đối chiếu: `mielons-the-sift-neoforge-port` (MIT, © 2026 Mielon; bản port NeoForge của MikiKox1) cho Minecraft 26.3.
Phương pháp: đọc tĩnh toàn bộ mã Java và dữ liệu của mod, đối chiếu với `integrations/content-spec.json`, config và mã của plugin
(grep/diff, **chưa chạy server**). Các điểm ghi "chưa kiểm chứng" là chỗ chỉ dựa trên đọc mã.

## 1. Quy mô của mod

| Thành phần | Mod | Ghi chú |
| --- | --- | --- |
| Mã Java | 158 file (main) + 73 file (client) | 41 mixin |
| Item có model | 90 | gồm spawn egg, đĩa nhạc, bộ gỗ, đá |
| Mob | 5 | Singer, Echo Golem, Blub, Sifter, Dark Sniffer |
| Biome | 10 | |
| Worldgen | 158 file | 21 density function, 15 noise, 46 feature, 59 placed feature, 3 carver, 2 structure |
| Structure NBT | 13 | 10 abandoned portal, `main_portal`, 2 cây Willow; cùng thay đổi `ancient_city/city_center` |
| Công thức | 76 | |
| Loot table | 66 | block, rương abandoned portal, 3 mob, 2 bảng đào của Sniffer |
| Advancement | 22 thật (+76 công thức, +2 ghi đè vanilla) | root + 18 nhánh `the_sift/`, 3 nhánh `story/` |
| Âm thanh | 26 file + `sounds.json`, 2 jukebox song | |
| Model/texture | 245 model, 142 texture, 10 file GeckoLib, 12 shader | |

## 2. Nội dung plugin CÓ nhưng mod KHÔNG có (54 ID)

Các mục này nằm trong `content-spec.json` nhưng không có ID trùng trong mod. Ba mục là bản đổi tên của nội dung mod: `siftite_ore` (= `siftslate_siftite_ore`), `charoite_ore` (= `siftslate_charoite_ore`) và `overgrown_willow` (= bộ gỗ `overgrown_willow_*`). Chúng đến từ một thiết kế khác (không phải Mielon's The Sift):

`bulb`, `bulb_glow`, `charoite_ore`, `chiseled_siftstone`, `cooked_loomer`, `drifter`, `frost_fragment`, `frost_stalker`, `glimmerwing`, `glowbell`, `heart_of_the_sift`, `hollow_bulb`, `koi`, `loomer`, `lumen_tulip`, `overgrown_willow`, `phase_boots`, `prism_blade`, `prism_boots`, `prism_chestplate`, `prism_helmet`, `prism_leggings`, `prism_warden`, `prismite`, `raw_loomer`, `rift_gate`, `rift_key`, `rift_walker`, `shard_axe`, `shard_ore`, `shard_pickaxe`, `shard_sword`, `shardling`, `sift_altar`, `sift_catalyst`, `sift_core`, `sift_crown`, `sift_grass`, `sift_leaves`, `sift_log`, `sift_planks`, `sift_shard`, `sift_soil`, `sift_wastes_stalker`, `siftite_ore`, `siftstone`, `siftstone_bricks`, `sky_fragment`, `spire_fragment`, `sunspore`, `the_sifter`, `thornback`, `violet_wisp`, `wisp`

Đáng chú ý nhất: **The Sifter** (boss Ravager 220 máu), **Prism Warden** (boss), các bộ đồ **Prism/Shard**, **Rift Key** (mod chỉ có item `sift_rift`, chỉ dùng được ở chế độ Creative; Rift Key dùng được khi sinh tồn là phần của plugin), 5 biome tự đặt (`crystal_spires`, `carapace`, `shattered_peaks`, `bulb_forest`, `pink_meadows`).
Nếu mục tiêu là "giống mod" thì nên tắt hoặc gỡ các mục này (xem mục 8).

## 3. Nội dung mod CÓ nhưng plugin THIẾU (67 item)

- **Bộ gỗ Overgrown Willow (21):** `overgrown_willow_boat`, `overgrown_willow_button`, `overgrown_willow_chest_boat`, `overgrown_willow_door`, `overgrown_willow_fence`, `overgrown_willow_fence_gate`, `overgrown_willow_foliage`, `overgrown_willow_hanging_sign`, `overgrown_willow_log`, `overgrown_willow_planks`, `overgrown_willow_pressure_plate`, `overgrown_willow_sapling`, `overgrown_willow_shelf`, `overgrown_willow_sign`, `overgrown_willow_slab`, `overgrown_willow_stairs`, `overgrown_willow_trapdoor`, `overgrown_willow_vines`, `overgrown_willow_wood`, `stripped_overgrown_willow_log`, `stripped_overgrown_willow_wood`
- **Họ đá Siftslate (13):** `chiseled_siftslate`, `cobbled_siftslate`, `cobbled_siftslate_slab`, `cobbled_siftslate_stairs`, `cobbled_siftslate_wall`, `reinforced_siftslate`, `siftslate_brick_slab`, `siftslate_brick_stairs`, `siftslate_brick_wall`, `siftslate_bricks`, `siftslate_growth`, `siftslate_hanging_roots`, `siftslate_stalks`
- **Quặng Siftslate (5):** `siftslate_charoite_ore`, `siftslate_coal_ore`, `siftslate_diamond_ore`, `siftslate_emerald_ore`, `siftslate_siftite_ore`
- **Thực vật và sculk (15):** `dry_healthy_sculk`, `dry_healthy_sculk_growth`, `dry_healthy_sculk_sprouts`, `healthy_sculk_sprouts`, `overgrown_chard`, `overgrown_fronds`, `overgrown_hanging_roots`, `overgrown_lotus`, `overgrown_stalks`, `sculkflower`, `sculkflower_seeds`, `siftslate_hanging_roots`, `siftslate_stalks`, `sunburst_plant`, `whisperbloom`
- **Khác (15):** `blub_spawn_egg`, `cooked_sifter_meat`, `dark_sniffer_spawn_egg`, `echo_golem_spawn_egg`, `ichor_bucket`, `ichor_shard`, `ichor_snowball`, `music_disc_rift`, `music_disc_wellspring`, `raw_sifter_meat`, `sift_portal`, `sift_rift`, `sift_rubble`, `sifter_spawn_egg`, `singer_spawn_egg`

Plugin chỉ có 1 khối `siftslate` và dùng vật liệu vanilla thay thế cho phần còn lại.

## 4. Advancement

Mod có 22 advancement thật. Plugin có bộ riêng (`prismatic`, `matriarch`, `tyrant`, `warden`, `storm`, `shard`, ...) **không trùng** với mod, và các advancement tôi thêm ở nhánh này (`singer_awake`, `ancient_gate`, ...) cũng không phải của mod.

| Mod (id) | Tên / điều kiện |
| --- | --- |
| `story/fallen_civilization` | Fallen Civilization: tới Ancient City |
| `story/song_of_the_past` | Song of the Past: thổi Goat Horn ở trung tâm Ancient City |
| `story/brave_the_unknown` | Brave the Unknown: vào The Sift |
| `the_sift/root` | The Sift: vào dimension |
| `rifter` / `rift_resonance` | Đi qua Rift / nghe đĩa Rift gần Rift |
| `blood_of_the_gods` | Đổ đầy xô Ichor |
| `certified_sifter` | Đi hết 10 biome |
| `smells_creepy` / `planting_the_dark` | Nhận hạt Sculkflower / trồng trên sculk |
| `kinda_purpleish` / `better_enchanter` | Đào Charoite Ore / enchant bằng Charoite |
| `sifted_nugget` / `cover_me_in_siftite` / `keep_inventory` | Đào Siftite Ore / đủ giáp Siftite / chết khi mang đồ Siftite |
| `like_father_and_son` / `friend_of_the_sift` / `pitch_perfect` | Echo Golem đưa Soul Block cho Singer / làm Echo Golem quý mình / đổi đồ với Singer |
| `blub` / `food_chain` | Thuần hoá Blub / Blub hạ Dark Sniffer |
| `false_alarm` | Dùng Ichor Snowball đánh lừa Warden |
| `spicewood` | Đốt gỗ trong The Sift |

## 5. Mob: khác biệt thông số và hành vi

| Mob | Mod | Plugin |
| --- | --- | --- |
| Singer | peaceful, bất tử ngoài soul event, trao đổi Soul Block | **đã sửa** ở commit trước (peaceful, trao đổi); chưa có soul event |
| Echo Golem | 34 máu, tốc độ 0.23, follow 128; **không tấn công**, nhặt Soul Block ở Soul Canyon, bond bằng Echo Shard | **đã sửa**: mặc định 34 máu, không còn ATTACK_DAMAGE (xóa key chết), charoite-protection hook vào pulse, scan theo chunk |
| Blub | 20 máu, tốc độ 0.3, dmg 3; thuần hoá bằng Soul Block, đi theo, ngồi, bảo vệ chủ | **đã sửa**: thuần hoá bằng Soul Block (follow/protect/sit), drop Blub Meat → nung thành Cooked Blub Meat (`smelt_blub_meat`) |
| Sifter | 40 máu, tốc độ 0.31, dmg 10, đi đàn, rơi Raw Sifter Meat | một phần: drop Raw Sifter Meat → nung thành Cooked Sifter Meat (`smelt_raw_sifter_meat`); **chưa có herd behavior** |
| Dark Sniffer | 40 máu, tốc độ 0.1, dmg 7, knockback 0.75, **Sonic Sniff** kéo mục tiêu; sniffer bị sculk nhiễm biến đổi | có spawn; chưa thấy Sonic Sniff hay cơ chế nhiễm |

Mod còn có: Warden-Dark Sniffer (mixin), RiftMobAvoidance (mob tránh Rift), Siftite return entity.

## 6. Hệ thống

| Hệ thống | Mod | Plugin |
| --- | --- | --- |
| Ichor | chất lỏng thật (flow, bucket, waterlogging, cauldron, pointed dripstone, tương tác lava), tái tạo Regeneration I | có tác dụng khi chạm, bucket/bottle; **không phải fluid thật** (chưa kiểm chứng đủ các tương tác) |
| Ichor Snow / mưa | có | có (`tickIchorWeather`) |
| Siftite | công cụ: độ bền netherite +470, tốc độ ×1.2, sát thương +1; giáp: độ bền +4, 3/6/8/3, toughness 3, knockback resist 0.15; thợ rèn Netherite→Siftite | smithing có; chỉ số dùng attribute modifier (chưa so khớp số) |
| Charoite | +20 cấp XP chi phí, +1 cấp enchant, giữ giới hạn anvil, tích hợp Easy Magic | có (đã làm theo mô tả; chưa so khớp số) |
| Rift | mở 5 phút, chuyển người, mob, phương tiện, đạn, item; ánh sáng, tránh mob | có Rift riêng (Rift Key, mob, resonance) **khác thiết kế mod**; chỉ chuyển entity qua bán kính |
| Cổng Ancient City | block `SIFT_PORTAL`, khung ≥3×3, Singer, HORN/NOTE | đã port phần chính (commit trước); chưa lưu qua restart |
| Ngày/đêm | `SiftDayNightCycle`, world clock, timeline `in_the_sift`, bầu trời 6 lớp | chưa thấy (grep không ra); client không thể làm bằng plugin |
| Giường | dùng được trong dimension | chưa kiểm chứng |
| Lệnh | `/locate` Sift, `/weather` Sift | có `/sift ...` riêng |

## 7. Worldgen và cấu trúc

- Mod: 10 biome, 46 feature + 59 placed feature (cây Willow, hồ Ichor, sculk, monolith, vòm đá, ...), 3 carver, 2 structure, 13 NBT.
- Plugin: 15 biome (5 không có trong mod); phần feature/placed feature trong datapack **trống**, phong cảnh do Java (`SiftEcologyManager`) vẽ sau khi chunk sinh. Commit `c0125d1` trên nhánh này đã port noise/density/carver/biome source (chưa kiểm chứng).
- NBT của mod (abandoned portal ×10, `main_portal`, cây Willow) **chưa dùng**; plugin tự sinh abandoned portal bằng code. Có thể nạp NBT qua `StructureManager` của Paper sau khi đổi tên block custom thành block vanilla thay thế.
- Tag, `jukebox_song` (rift, wellspring), loot table: chưa port.

## 8. Đề xuất thứ tự làm

1. **Dọn nội dung lạ** (mục 2): đưa vào cờ cấu hình, mặc định tắt (boss Sifter/Prism Warden, Rift Key, bộ Prism/Shard, 5 biome lạ).
2. **Advancement của mod** (mục 4): thay bộ hiện tại bằng 22 advancement của mod, gắn vào sự kiện thật (nhiều cái cần hook mới: Blub, Echo Golem, Warden...).
3. **Mob** (mục 5): sửa thông số Echo Golem; làm Blub thuần hoá được, Sifter đi đàn, Sonic Sniff, nhiễm sniffer.
4. **Công thức và loot** (76 + 66): port nguyên dữ liệu, đổi ID sang provider.
5. **Họ block** (Willow, Siftslate, thực vật sculk): định nghĩa cho Nexo/ItemsAdder/Oraxen qua `generateProviderDefinitions`.
6. **Cấu trúc NBT** và **resource pack** (model, texture, âm thanh, đĩa nhạc): cần xử lý phân phối pack và credit tác giả đóng góp (ImpSteve, Fuzja Jądrowa, Echo_Dev...).
7. **Ichor fluid thật, Rift theo mod, ngày/đêm**: cần thiết kế riêng vì Paper không có block/fluid tuỳ chỉnh.

## 9. Cập nhật: advancement

Bộ advancement của plugin đã được thay bằng 22 advancement của mod (xem README, mục "Advancement coverage").
Đã gỡ các advancement không có trong mod (`prismatic`, `matriarch`, `tyrant`, `warden`, `storm`, `shard`, `shard_gear`,
`sifter`, `rift_key`, ...). Kiểm tra mã nguồn mod: không có Spire Tyrant, Crimson Keep, Prism Warden hay Matriarch,
nên các dòng "parity" trước đó về Spire Tyrant không dựa trên mod này.
