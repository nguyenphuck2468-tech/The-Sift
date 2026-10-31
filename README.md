# The Sift — plugin Paper làm bởi người hâm mộ

The Sift là bản triển khai của người hâm mộ lấy cảm hứng từ chiều không gian được công bố tại
Minecraft Live 2026. Nhắm tới **Paper 26.3**, Java 25 và Paper API `26.3.build.218-stable`;
không liên kết với Mojang hay Microsoft. Các provider nội dung cung cấp item, block và mob ở
những chỗ Paper API không có sẵn khái niệm tương ứng. Trạng thái triển khai và các phần thay thế
(stand-in) được theo dõi trong [`docs/parity-matrix.md`](docs/parity-matrix.md).

## Biên dịch

```bash
./gradlew clean build
```

Tác vụ `siftResourcePack` đóng gói các file PNG chưa được xác minh nguồn gốc. Không phát hành hay
phân phối lại `TheSift-resource-pack.zip` cho đến khi nguồn gốc asset được xác nhận.

Thư viện biên dịch mặc định là Paper API `26.3.build.218-stable`. Ghi đè khi kiểm chứng phiên
khác: `gradle build -PpaperApiVersion=<phiên-bản>`.

Plugin đăng ký dimension datapack qua lifecycle datapack-discovery của Paper; cần Paper 26.3 trở
lên. Chiều không gian gồm 15 biome (10 của mod tham chiếu cộng 5 biome tự viết) trên noise router
port từ mod, với cả ba carver đã được nối vào mọi biome. Custom block dùng vanilla stand-in ở
những chỗ chưa có provider.

## Ngôn ngữ

Thông điệp trong game mặc định là **tiếng Việt** (`locale: vi` trong `config.yml`; chọn `en` cho
tiếng Anh). Chủ máy có thể ghi đè từng câu bằng tệp `plugins/TheSift/messages.yml` mà không cần
sửa jar; key thiếu sẽ lần lượt fallback sang tiếng Anh rồi đến mặc định tại chỗ gọi.

## Phạm vi nền móng hiện tại

- Build Gradle/Java 25; cấu hình plugin có phiên bản (`config-version: 4`), từ chối ghi đè config
  mới hơn build.
- Registry nội dung: Siftslate, Ichor, Charoite, Siftite, Resonance, Rift và các mob stand-in.
- Ichor v2: cauldron có mức tồn, chi phí rót/lọ, Ichor Glass/Pane/Snow, Ichor Bucket, Ichor
  Shard, Ichor Snowball (ném làm chậm mục tiêu; trúng gần Warden mở advancement `false_alarm`),
  công thức, particle và âm thanh.
- Tiến trình Charoite/Siftite: quặng rơi có tag, công thức thỏi/viên, nâng cấp smithing giáp/tool
  Siftite, thương Siftite có sát thương riêng.
- Singer resonance: Allay có tag, xung sóng âm theo cấu hình, miễn nhiễm khi cầm Charoite, boss
  bar, drop.
- `/sift doctor` xem trạng thái; `/sift reload` kiểm tra migration; `/sift dimension` và
  `/sift return` dành cho người chơi; `/sift locate` chỉ thướccompass tới Ancient City gate.
- Chiều không The Sift với 15 biome, vào bằng `/sift dimension`, ra bằng `/sift return`.
- Trang trí chunk lần đầu: rừng anh đào, rừng hành bulb, tháp pha lê, cánh đồng đá vỡ, mốc
  Carapace; suối Ichor (một khối nguồn — không tạo nguồn vô hạn),portal đổ nát có rương loot,
  Soul Canyon, hang Sniffer, vòm đá, monolith.
- Đàn Sifter tự nhiên (3–5 con, hiếm) trong Overgrown Forest, mỗi đàn một anchor riêng; Blub hoang
  bị thu hút bởi người cầm Soul Block và thuần hoá theo tỉ lệ 1/3 đúng mod (`mobs.blub.tame-chance`).
- Ném giáo Siftite: giáo bay là projectile thật, gây `siftite.spear-damage` và rơi lại nơi trúng
  để nhặt.
- Sift Tide xoay vòng hiệu ứng di chuyển/khai thác.
- Cổng Sonorous Deepslate: đặt block có tag, đặt note block phía trên, chuột phải để tune, chơi
  đúng giai điệu `1, 3, 7, 6, 5, 2, 4, 8` để mở Rift. Advancestream của mod đã được nối 6/8
  trigger còn thiếu; `false_alarm` có sẵn qua Ichor Snowball.
- Rift nối The Sift với Overworld hai chiều, đưa theo entity gần đó, mặc định mở 5 phút.
- Giáp/tool Siftite đánh dấu rơi khi chết sẽ được trả lại qua hiệu ứng Mini Rift; trạng thái chờ
  được lưu qua restart.
- Optional MythicMobs, EliteMobs, ModelEngine, Nexo, ItemsAdder, Oraxen và SPI
  (`SiftItemProvider`/`SiftMobProvider`/`SiftBlockProvider`) — tra cứu class/method được cache
  (`Reflect`) nên gọi lại trong event nóng không còn tốn reflection lặp.

## Plugin nên cài thêm

The Sift chạy được độc lập: thiếu provider chỉ giảm tính năng và ghi cảnh báo (plugin không tự
tắt, trừ khi bật `requirements.hard-stop: true`). Cài càng nhiều mục dưới đây, nội dung càng sát
mod gốc:

| Plugin | Vai trò | Bắt buộc? |
|---|---|---|
| **Nexo / ItemsAdder / Oraxen** (chọn 1) | Cung cấp item + block custom thật | Không — nhưng `providers.strict: true` mặc định sẽ chặn vào dimension nếu chưa có một backend item, block và mob |
| **MythicMobs** | Định nghĩa mob chỉnh được chỉ số/stats cho sinh vật The Sift | Không |
| **EliteMobs** | Thay boss spawn bằng custom boss | Không |
| **ModelEngine** | Gắn model render cho mob (chỉ render, không điều khiển hành vi) | Không — nhưng `requirements.all-of` mặc định liệt kê nó, thiếu chỉ cảnh báo |
| **ResourcePackManager** | Phát resource pack cho người chơi | Không (hiện chỉ được dò tìm trong `/sift doctor`) |

Cấu hình mặc định trong `config.yml` (`requirements`) chỉ **cảnh báo** khi thiếu plugin; cài ít
nhất một backend item/block/mob (hoặc plugin nào đó đăng ký SPI `SiftItemProvider` /
`SiftBlockProvider` / `SiftMobProvider`) để mở khóa vào dimension khi `providers.strict: true`.
Chạy `/sift doctor` để xem còn thiếu gì.

## Geyser và người chơi Bedrock (không bắt buộc)

- Muốn người chơi **Bedrock** vào được server thì mới cần cài **Geyser** (kèm **Floodgate** để
  đăng nhập không cần tài khoản Java). Không cài cũng không ảnh hưởng người chơi Java.
- Nếu server phục vụ nhiều phiên bản Minecraft, cài thêm **ViaVersion**/**ViaBackwards**.
- Mức hỗ trợ Bedrock hiện tại của plugin là mức 1 (dò nhận diện Geyser/Floodgate/ViaVersion trong
  `/sift doctor`); mức 2 (sinh pack riêng cho Bedrock + custom mapping) **chưa làm** — người chơi
  Bedrock sẽ thấy các block/item stand-in dạng vanilla.

## Provider nội dung custom

`providers.strict` mặc định `true`: việc tạo/vào dimension bị chặn cho đến khi một backend
item/block/mob resolve đủ mọi ID bắt buộc. `false` là chế độ dev/CI (fallback vanilla stand-in);
gate boot của CI chạy server ở chế độ này. `/sift doctor` báo backend đã cài, provider đã chọn,
ID thiếu và gợi ý cài đặt.

Định nghĩa chuẩn: `integrations/content-spec.json`. Sinh cấu hình provider bằng
`./gradlew generateProviderDefinitions`; kết quả nằm ở `build/generated/provider-definitions/`.
Với `auto`, thứ tự ưu tiên item/block: Sift service → Nexo → ItemsAdder → Oraxen; mob: Sift
service → ItemsAdder entity → MythicMobs → EliteMobs. ModelEngine chỉ phụ trách render.

## Chiều không gian The Sift

Plugin tự supply datapack, không cần mod Fabric tham chiếu. Một số tên biome, giai điệu portal,
thời tiết Ichor, cấu trúc và hành vi sinh vật lấy cảm hứng từ dự án MIT
[Mielon's The Sift](https://github.com/Arta48/Mielons-The-Sift) — phần data port trực tiếp được
ghi công trong `NOTICE.md`. So sánh tính năng và mức kiểm chứng nằm ở
[`IMPLEMENTATION_REPORT.md`](docs/archive/IMPLEMENTATION_REPORT.md) và `docs/parity-matrix.md`.

Trên Paper 26.3, dùng `/sift dimension` để tạo/nạp và vào chiều không gian (console cũng chạy
được); `/sift return` để quay về. `/datapack list enabled` phải thấy `TheSift/dimension`.

## Kịch bản thử nhanh

- Ichor v2: `/sift give ichor_bottle <người chơi>` → chuột phải cauldron Ichor để rót lọ;
  xô trống rút cả cauldron ra Ichor Bucket; tuyết Ichor ném được; 4 tuyết ép thành 1 shard.
- Charoite/Siftite: `/sift give charoite|siftite_nugget|siftite_ingot|siftite_spear <người chơi>`.
- Singer: `/sift summon singer <người chơi>`; Echo Golem: cho Echo Shard để bond, giao/nhận lại
  Soul Block mở `friend_of_the_sift`.
- Cổng: đặt Sonorous Deepslate + note block phía trên, tune bằng chuột phải, chơi
  `1, 3, 7, 6, 5, 2, 4, 8` bằng chuột trái để mở Rift portal.

The Sift là dự án fan độc lập, không tuyên bố liên kết hay được chứng thực bởi Mojang hay
Microsoft.
