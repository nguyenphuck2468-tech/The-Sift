# Cấu hình The Sift (config-version 5)

Key quan trọng (mặc định trong `src/main/resources/config.yml`, mô tả tiếng Việt ngay trong file):

| Key | Mặc định | Ý nghĩa |
|---|---|---|
| `locale` | `vi` | Ngôn ngữ thông điệp trong game (`en` cho tiếng Anh); override từng câu bằng `plugins/TheSift/messages.yml` |
| `providers.strict` | `true` | Production: chặn dimension đến khi provider đủ ID; `false` = dev/CI fallback vanilla |
| `requirements.*` | soft | Chỉ cảnh báo khi thiếu plugin; `hard-stop: true` để tự tắt plugin |
| `rift.reward-drops` | `0` | Số item thưởng khi rift đóng (hardening: mặc định 0) |
| `rift.rift-key-chance` | `0.0` | Tỉ lệ rơi Rift Key (hardening: mặc định 0) |
| `rift.reward-cooldown-minutes` | `30` | Giới hạn nhận thưởng theo người chơi |
| `rift.natural.*` | bật | Rift tự nhiên 6–14 phút, tối đa 2 rift |
| `content.prism_warden` | `false` | Fan-made boss |
| `content.sift_standin_mobs` | `false` | Đàn mob stand-in đổi tên (không ảnh hưởng 5 mob lõi mod) |
| `content.sift_tide` | `false` | Sift Tide |
| `content.rift_walker_waves` | `false` | Wave mob Rift + pulse DARKNESS/NAUSEA |
| `content.spire_tyrant` | `false` | Fan-made boss thay The Sifter |
| `mobs.blub.tame-chance` | `0.333...` | Tỉ lệ thuần Blub đúng mod |
| `ichor.cauldron.dripstone-fill` | `true` | Dripstone nhỏ giọt đầy cauldron Ichor |
| `siftite.spear-damage` | `6.0` | Sát thương giáo ném |

Migration 4→5 chỉ THÊM key còn thiếu, không ghi đè giá trị người dùng đã đặt.
