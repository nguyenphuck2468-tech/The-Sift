# Cấu hình The Sift (config-version 5)

Key quan trọng (mặc định trong `src/main/resources/config.yml`, mô tả tiếng Việt ngay trong file):

| Key | Mặc định | Ý nghĩa |
|---|---|---|
| `locale` | `vi` | Ngôn ngữ thông điệp trong game (`en` cho tiếng Anh); override từng câu bằng `plugins/TheSift/messages.yml` |
| `providers.strict` | `false` | Mặc định cài là chạy: dùng vanilla stand-in có tag khi thiếu provider; bật `true` nếu muốn khóa dimension cho đến khi provider đủ ID |
| `requirements.*` | soft | Chỉ cảnh báo khi thiếu plugin; `hard-stop: true` để tự tắt plugin |
| `rift.reward-drops` | `0` | Số item thưởng khi rift đóng (hardening: mặc định 0) |
| `rift.reward-cooldown-minutes` | `30` | Giới hạn nhận thưởng theo người chơi |
| `rift.natural.*` | bật | Rift tự nhiên 6–14 phút, tối đa 2 rift |
| `mobs.blub.tame-chance` | `0.333...` | Tỉ lệ thuần Blub đúng mod |
| `ichor.cauldron.dripstone-fill` | `true` | Dripstone nhỏ giọt đầy cauldron Ichor |
| `siftite.spear-damage` | `6.0` | Sát thương giáo ném |

Migration 4→5 chỉ THÊM key còn thiếu, không ghi đè giá trị người dùng đã đặt.
