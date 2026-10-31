# Phân tích khoảng cách nội dung so với mod gốc

Đối chiếu: plugin `overnight/hardening` (2026-11-01) so với Mielon's The Sift 1.1.2
(github.com/Arta48/Mielons-The-Sift, commit cdd675e). Cập nhật các số liệu cũ — các mục
"chưa làm" dưới đây đã có trong repo và được kiểm chứng bằng commit:

| Khoảng cách cũ trong tài liệu | Trạng thái hiện tại | Commit |
|---|---|---|
| "Thiếu họ gỗ Overgrown Willow" | Đủ 21 mục + recipe | `4a13d86` |
| "Thiếu họ đá Siftslate" | Đủ 12 mục + recipe | `4a13d86` |
| "Sifter không có herd behavior" | Đàn 3–5, anchor theo đàn, spawn terrain tự nhiên | `b487d12`, `adc1fc0` |
| "Dark Sniffer chưa đạt parity" | Nhiễm 30s → biến hình, Sonic Sniff, đào quà, tương tác Warden | đã vào main trước `2463038` |
| "Advancement false_alarm/like_father_and_son chưa nối" | 8/8 trigger đã wired | `1bb19cf`, `4f5483e` |
| "Fossils (1.1.0) chưa có" | Java decoration trong hang deepslate | `bb46c37` |
| "Wellspring/Sift Rubble (1.1.0) chưa có" | Item + give + spec | `bb46c37` |
| "Ambient sound lần đầu vào dimension chưa có" | Sting + dòng localized | `bb46c37` |
| "Spear là fan-made" | Sai — mod có Siftite Spear; README đã sửa | `bb46c37` |

## Khoảng cách còn lại (fidelity, không phải thiếu content)

1. Model/texture/animation mob + block — cần resource pack; asset contributor của mod
   (ImpSteve, Fuzja Jądrowa, Echo_Dev) chưa xác minh quyền — cần quyết của chủ repo.
2. Âm thanh: 8 nốt Sonorous, nhạc Rift/Wellspring — cùng rào cản asset.
3. Bầu trời động/shader — client-side, Paper không can thiệp.
4. Ichor là fluid thật — Paper không hỗ trợ custom fluid; mô phỏng đã phủ mọi tương tác người chơi.
5. Echo Golem AI: vòng tìm Soul Block trong phạm vi thay vì hành trình Soul Canyon đầy đủ của mod.
6. Exact worldgen parity (shape/vein distribution) — cần playtest cùng seed.

Tài liệu lịch sử chi tiết: `docs/archive/IMPLEMENTATION_REPORT.md`.
