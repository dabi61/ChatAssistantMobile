# Session Memory - 2026-02-23

Muc tieu file nay:
- Luu "ngu dieu", quy tac UI/UX, va cac quyet dinh ky thuat hien tai.
- Dung de khoi dong nhanh o phien lam viec tiep theo.

## 1) Ngu dieu va trai nghiem mong muon
- Ket qua phan tich phai ro rang, de doc, uu tien hanh dong ngay.
- Bubble phai hien ket qua nhanh, toi gian thao tac.
- UI va noi dung uu tien logic: tom tat -> goi y -> copy nhanh.
- Input can duoc loc ky de giam nhieu, tranh data icon/he thong.

## 2) Hanh vi chinh da dong bo
- Bubble result dang dung Compose overlay (de custom ve sau).
- Analyze response parser da ho tro schema moi:
  - `analysis.summary`
  - `analysis.scenarios` (array)
  - fallback schema cu top-level.
- Accessibility capture preview da co trong Settings:
  - Raw captured text (truoc loc)
  - Filtered input (duoc gui di analyze)
  - Co Refresh/Clear preview.

## 3) Rule speaker theo vi tri (quan trong)
- Bong bong ben trai => `other`
- Bong bong ben phai => `me`
- Text o giua man hinh => `other` (theo yeu cau moi nhat)
- Filtered output luu theo format:
  - `me: ...`
  - `other: ...`

## 4) Filter Accessibility hien tai
- Bo icon/menu/system labels.
- Bo time/date separators.
- Bo text tu EditText composer.
- Bo token ngan dang badge/symbol.
- Raw van duoc luu de debug.

## 5) Cac file can doc dau phien sau
- `app/src/main/java/com/chatassistantmobile/service/accessibility/ChatAccessibilityService.kt`
- `app/src/main/java/com/chatassistantmobile/data/repository/ChatRepository.kt`
- `app/src/main/java/com/chatassistantmobile/service/overlay/FloatingBubbleService.kt`
- `app/src/main/java/com/chatassistantmobile/ui/screen/SettingsScreen.kt`
- `app/src/main/java/com/chatassistantmobile/data/local/CurrentScreenCaptureStore.kt`

## 6) Ghi chu van hanh
- Neu thay ket qua phan tich lech: vao Settings -> Accessibility capture preview de so sanh Raw vs Filtered.
- Neu bubble/service bi stale sau update: force stop app, bat lai bubble.
- Local backend thuong chay qua `127.0.0.1:8001` + `adb reverse`.

## 7) Cach tiep tuc o phien sau
- Bat dau bang cau: "Hay doc SESSION_MEMORY_2026-02-23.md va tiep tuc tu trang thai nay."
- Neu can custom giao dien popup ket qua: sua trong `FloatingBubbleService.kt` (Composable overlay content).
