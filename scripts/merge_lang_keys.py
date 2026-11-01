"""One-off P2-C: merge the new i18n keys into the existing lang sections (no duplicates)."""
import yaml
from pathlib import Path

VI_GATE_NEW = '''  nearest: "Cổng The Sift gần nhất: %coords%. La bàn của bạn giờ chỉ về đó."
  cannot-awaken: "Cổng The Sift không đánh thức được: provider bắt buộc chưa có. Chạy /sift doctor."
  singer-failed: "Singer không xuất hiện được. Kiểm tra provider mob bắt buộc bằng /sift doctor."
  awakened: "Bài hát của Singer đã đánh thức Sonorous Deepslate."
  horn-first: "Hãy thổi Sừng Dê gần cổng Ancient City để đánh thức Sonorous Deepslate trước."
  opened: "§bCổng Ancient City mở vào The Sift!"
  missing-notes: "Đặt Note Block lên cả 8 block Sonorous Deepslate; còn thiếu %count%."
  set-notes: "Đặt đủ 8 note block, rồi chơi 1 → 3 → 7 → 6 → 5 → 2 → 4 → 8."
  no-return-site: "Không có chỗ cổng về 1:1 an toàn tại tọa độ X/Z này trong The Sift."
  return-frame-failed: "Không dựng an toàn được khung cổng về 1:1; không block nào bị thay đổi."
  returned: "Bạn đã trở về Overworld qua cổng Ancient City."
  crossed: "Bạn đã băng qua cổng Ancient City vào The Sift."'''
VI_RIFT_NEW = '''  blocked-providers: "Rift bị chặn: provider bắt buộc cho The Sift chưa sẵn sàng. Chạy /sift doctor."
  blocked-world: "Rift bị chặn: không tải được thế giới đích. Kiểm tra log server và /sift doctor."
  tear-open: "§5Một Rift vừa xé toạc ra đâu đó gần đây..."
  tearing-open: "§5Khe nứt bắt đầu xé mở..."
  carries: "§5Rift đưa bạn xuyên qua hai thế giới."
  open-failed: "§cRift không mở được; kiểm tra mức sẵn sàng của provider."'''
VI_TAIL = '''
sift:
  reload-done: "Đã reload cấu hình The Sift. Khởi động lại server để áp dụng thay đổi cờ nội dung."
  datapack-usage: "Cách dùng: /sift datapack <list|enable|disable> [tên]"
  resourcepack-sent: "Đã gửi resource pack The Sift cho bạn (%mode%)."
  resourcepack-no-provider: "Không phát hiện plugin provider resource pack và resource-pack.server-url đang trống; hãy đặt URL http(s) hoặc cài provider rồi phát sóng lại."
  resourcepack-broadcast: "Đã gửi resource pack The Sift cho %count% người chơi qua %provider%."
  version: "The Sift foundation: %version%"
  runtime-target: "Mục tiêu runtime: Paper 26.3 / Paper API 26.3.build.218-stable"
  dimension-disabled: "The Sift dimension đang bị tắt."
  dimension-locked: "The Sift dimension bị khóa vì thiếu provider ID bắt buộc. Chạy /sift doctor; xem integrations/README.md để cài đặt."
  dimension-ready: "The Sift dimension đã sẵn sàng. Người chơi vào bằng /sift dimension."
  dimension-load-failed: "Không tải được The Sift dimension; kiểm tra datapack và log server."
  locate-console-only: "Chỉ người chơi mới tìm được cổng Ancient City."
  return-console-only: "Chỉ người chơi mới trở về từ The Sift."
  ichor-disabled: "Nội dung Ichor đang bị tắt."
  rift-disabled: "Nội dung Rift đang bị tắt."
  siftite-disabled: "Nội dung Charoite/Siftite đang bị tắt."
  singer-disabled: "Nội dung Singer đang bị tắt."
  echo-golem-disabled: "Nội dung Echo Golem đang bị tắt."
  creature-disabled: "Nội dung sinh vật Sift đang bị tắt."
  player-not-found: "Không tìm thấy người chơi."
  bottle-unresolved: "Item ID 'ichor_bottle' chưa được provider giải quyết. Chạy /sift doctor."
  key-unavailable: "Item ID 'rift_key' không khả dụng ở chế độ strict. Chạy /sift doctor."
  item-unresolved: "Item ID '%item%' không rõ hoặc provider chưa giải quyết. Chạy /sift doctor."
  gave: "Đã đưa %item% cho %target%."
  summon-usage: "Cách dùng: /sift summon singer <người chơi>"
  summon-usage-echo: "Cách dùng: /sift summon echo_golem <người chơi>"
  summon-usage-rift: "Cách dùng: /sift summon rift <người chơi>"
  summon-usage-generic: "Cách dùng: /sift summon <loài> [người chơi]"
  summoned: "Đã triệu hồi %mob% gần %target%."
  unknown-creature: "Không rõ sinh vật Sift: %species%"
  usage: "Cách dùng: /sift <doctor|reload|datapack <list|enable|disable>|resourcepack [force]|dimension|locate|return|give <item> <người chơi>|summon <loài> [người chơi]>"
  locked: "The Sift bị khóa vì thiếu provider ID bắt buộc. Chạy /sift doctor."
  locked-no-world: "The Sift bị khóa và không có thế giới an toàn nào được tải. Hãy nhờ admin tải một world."

sonorous:
  note: "§bNốt Sift: %note%"
  wrong-note: "§cSai nốt; giai điệu của cổng đặt lại từ đầu."
  melody: "§bGiai điệu Ancient City: %progress%/8"
  awaken-first: "§cHãy đánh thức Sonorous Deepslate của thành này bằng Sừng Dê trước."
  no-frame: "§7Bài hát vang lên nhưng không có khung Reinforced Deepslate nào đáp lại."
  already-open: "§7Cổng đã mở. Hát ngược bài hát để đóng lại."
  complete: "§bBài hát Sonorous đã hoàn thành."
  closed: "§5Cổng gập lại vào sâu lòng deepslate."

soul:
  no-provider: "Soul Block không có từ provider đã cấu hình. Chạy /sift doctor."

siftite:
  mini-rift-returned: "§5Một Mini Rift đã trả lại trang bị Siftite của bạn."
'''

EN_GATE_NEW = '''  nearest: "Nearest The Sift gate: %coords%. Your compass now points to it."
  cannot-awaken: "The Sift gate cannot awaken: required providers are unresolved. Run /sift doctor."
  singer-failed: "The Singer could not appear. Check the required mob provider with /sift doctor."
  awakened: "The Singer's song awakens the Sonorous Deepslate."
  horn-first: "Play a Goat Horn near the Ancient City gate to awaken its Sonorous Deepslate first."
  opened: "§bThe Ancient City portal opens into The Sift!"
  missing-notes: "Place Note Blocks on all 8 Sonorous Deepslate blocks; missing %count%."
  set-notes: "Set the 8 note blocks, then play 1 → 3 → 7 → 6 → 5 → 2 → 4 → 8."
  no-return-site: "No safe 1:1 return-portal site is available at these X/Z coordinates in The Sift."
  return-frame-failed: "The 1:1 return frame could not be built safely; no blocks were changed."
  returned: "You returned to the Overworld through the Ancient City gate."
  crossed: "You crossed the Ancient City gate into The Sift."'''
EN_RIFT_NEW = '''  blocked-providers: "Rift blocked: required providers for The Sift are unavailable. Run /sift doctor."
  blocked-world: "Rift blocked: its destination world could not be loaded. Check the server log and /sift doctor."
  tear-open: "§5A Rift tears open somewhere nearby..."
  tearing-open: "§5The rift begins to tear open..."
  carries: "§5The Rift carries you between worlds."
  open-failed: "§cThe Rift could not open; check provider readiness."'''
EN_TAIL = '''
sift:
  reload-done: "The Sift config was reloaded. Restart the server to apply content flag changes."
  datapack-usage: "Usage: /sift datapack <list|enable|disable> [name]"
  resourcepack-sent: "The Sift resource pack sent to you (%mode%)."
  resourcepack-no-provider: "No resource-pack provider plugin detected and resource-pack.server-url is empty; set an http(s) download URL or install a provider before broadcasting."
  resourcepack-broadcast: "The Sift resource pack sent to %count% player(s) via %provider%."
  version: "The Sift foundation: %version%"
  runtime-target: "Runtime target: Paper 26.3 / Paper API 26.3.build.218-stable"
  dimension-disabled: "The Sift dimension is disabled."
  dimension-locked: "The Sift dimension is locked because required provider IDs are missing. Run /sift doctor; see integrations/README.md for setup instructions."
  dimension-ready: "The Sift dimension is ready. Players can enter with /sift dimension."
  dimension-load-failed: "The Sift dimension could not be loaded; check the datapack and server log."
  locate-console-only: "Only players can locate an Ancient City gate."
  return-console-only: "Only players can return from The Sift."
  ichor-disabled: "Ichor content is disabled."
  rift-disabled: "Rift content is disabled."
  siftite-disabled: "Charoite/Siftite content is disabled."
  singer-disabled: "Singer content is disabled."
  echo-golem-disabled: "Echo Golem content is disabled."
  creature-disabled: "Sift creature content is disabled."
  player-not-found: "Player not found."
  bottle-unresolved: "Item ID 'ichor_bottle' is unresolved by the selected provider. Run /sift doctor."
  key-unavailable: "Item ID 'rift_key' is unavailable in strict provider mode. Run /sift doctor."
  item-unresolved: "Item ID '%item%' is unknown or unresolved by the selected provider. Run /sift doctor."
  gave: "Gave %item% to %target%."
  summon-usage: "Usage: /sift summon singer <player>"
  summon-usage-echo: "Usage: /sift summon echo_golem <player>"
  summon-usage-rift: "Usage: /sift summon rift <player>"
  summon-usage-generic: "Usage: /sift summon <species> [player]"
  summoned: "Summoned %mob% near %target%."
  unknown-creature: "Unknown Sift creature: %species%"
  usage: "Usage: /sift <doctor|reload|datapack <list|enable|disable>|resourcepack [force]|dimension|locate|return|give <item> <player>|summon <species> [player]>"
  locked: "The Sift is locked because required provider IDs are unavailable. Run /sift doctor."
  locked-no-world: "The Sift is locked, and no safe world is loaded. Ask an administrator to load a main world."

sonorous:
  note: "§bSift note: %note%"
  wrong-note: "§cWrong note; the gate melody resets."
  melody: "§bAncient City melody: %progress%/8"
  awaken-first: "§cAwaken this city's Sonorous Deepslate with a Goat Horn first."
  no-frame: "§7The song echoes, but no Reinforced Deepslate frame answers."
  already-open: "§7The portal is already open. Play the song backwards to close it."
  complete: "§bThe Sonorous song is complete."
  closed: "§5The portal folds back into the deepslate."

soul:
  no-provider: "Soul Block item is unavailable from the configured provider. Run /sift doctor."

siftite:
  mini-rift-returned: "§5A Mini Rift returned your Siftite equipment."
'''


def merge(path, gate_anchor, gate_new, rift_anchor, rift_new, tail):
    p = Path(path)
    t = p.read_text(encoding="utf-8")
    assert t.count(gate_anchor) == 1, "gate anchor " + path
    t = t.replace(gate_anchor, gate_anchor + "\n" + gate_new)
    assert t.count(rift_anchor) == 1, "rift anchor " + path
    t = t.replace(rift_anchor, rift_anchor + "\n" + rift_new)
    t = t.rstrip("\n") + "\n" + tail
    p.write_text(t, encoding="utf-8")
    print("merged", path)


merge("src/main/resources/lang/vi.yml",
      '  gate-city: "Cổng Ancient City này thuộc về Sift Gate — hãy đánh thức bằng Sừng Dê tại Sonorous Deepslate."',
      VI_GATE_NEW,
      'rift:\n  dimension-unavailable: "The Sift không khả dụng. Chạy /sift doctor."',
      VI_RIFT_NEW, VI_TAIL)
merge("src/main/resources/lang/en.yml",
      '  gate-city: "This Ancient City\'s portal belongs to its Sift gate — awaken it with a Goat Horn at the Sonorous Deepslate."',
      EN_GATE_NEW,
      'rift:\n  dimension-unavailable: "The Sift dimension is unavailable. Run /sift doctor."',
      EN_RIFT_NEW, EN_TAIL)

for f in ["src/main/resources/lang/vi.yml", "src/main/resources/lang/en.yml"]:
    d = yaml.safe_load(open(f, encoding="utf-8"))
    assert d["gate"]["disabled"] and d["gate"]["nearest"] and d["gate"]["gate-city"], f
    assert d["rift"]["dimension-unavailable"] and d["rift"]["blocked-providers"], f
    assert d["sonorous"]["note"] and d["sift"]["usage"] and d["soul"]["no-provider"], f
    print(f, "OK — top keys:", len(d))
