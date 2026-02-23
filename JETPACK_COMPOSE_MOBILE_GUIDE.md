# Chat Assistant Mobile Guide (Jetpack Compose)

Tài liệu này mô tả cách xây app Android Jetpack Compose kết nối với backend hiện tại của dự án.

## 1) Mục tiêu

- Đăng nhập Google trên Android.
- Đổi `Google ID token` sang `access_token + refresh_token` của backend.
- Gọi `POST /api/v1/analyze-chat` để phân tích hội thoại.
- Tự refresh token khi access token hết hạn.
- Triển khai cách lấy text tin nhắn từ màn hình/notification để tạo dữ liệu đầu vào.
- Sinh widget hoặc UI component gợi ý trả lời.

## 2) Luồng hệ thống

1. User bấm đăng nhập Google trên app.
2. App lấy `id_token` từ Google Sign-In.
3. App gọi `POST /api/v1/auth/google/login` để nhận token backend.
4. App lưu token an toàn.
5. App gửi dữ liệu chat lên `POST /api/v1/analyze-chat` với header `Authorization: Bearer <access_token>`.
6. Nếu `401`, app gọi `POST /api/v1/auth/refresh` bằng `refresh_token`, lưu token mới và retry request.
7. Khi logout, app gọi `POST /api/v1/auth/logout` rồi xóa token local.

## 3) Chuẩn bị backend trước khi mobile kết nối

Trong `.env` backend:

```env
GOOGLE_AUTH_ENABLED=true
GOOGLE_CLIENT_IDS=<android_client_id>,<web_client_id_if_needed>
GOOGLE_REQUIRE_VERIFIED_EMAIL=true

AUTH_ACCESS_TOKEN_SECRET=<long_random_secret>
AUTH_REFRESH_TOKEN_SECRET=<long_random_secret>

AUTH_STATE_BACKEND=redis
AUTH_STATE_REDIS_URL=redis://redis:6379/1
RATE_LIMIT_BACKEND=redis
```

Chạy:

```bash
docker compose up -d --build
```

## 4) Android project structure gợi ý

```text
app/
  src/main/java/com/yourapp/
    data/
      api/
      auth/
      local/
      repository/
    domain/
      model/
      usecase/
    ui/
      auth/
      chat/
      analysis/
      widget/
    service/
      notification/
      accessibility/
    di/
```

## 5) Cấu hình Google Sign-In cho Android

## 5.1 Trên Google Cloud/Firebase

1. Tạo project.
2. Bật Google Sign-In.
3. Tạo OAuth client cho Android.
4. Thêm SHA-1/SHA-256 cho debug và release.
5. Lấy `client id` và đưa vào backend `GOOGLE_CLIENT_IDS`.

Lưu ý:
- App Android phải dùng đúng client id tương ứng package name + SHA.
- Sai `aud` sẽ bị backend trả `401`.

## 5.2 Dependency (khuyến nghị)

Sử dụng:
- Credential Manager
- Google ID token credential library
- Retrofit + OkHttp + Kotlinx Serialization/Moshi
- DataStore/EncryptedSharedPreferences
- Hilt (nếu dùng DI)

Nên pin theo phiên bản stable mới nhất tại thời điểm triển khai.

## 6) API contract backend

## 6.1 Login Google

`POST /api/v1/auth/google/login`

Request:

```json
{
  "id_token": "..."
}
```

Response:

```json
{
  "access_token": "...",
  "token_type": "bearer",
  "expires_in": 3600,
  "refresh_token": "...",
  "refresh_expires_in": 1209600,
  "user": {
    "provider": "google",
    "subject": "google:123",
    "email": "user@example.com",
    "name": "User",
    "picture": "https://..."
  }
}
```

## 6.2 Refresh

`POST /api/v1/auth/refresh`

```json
{
  "refresh_token": "..."
}
```

## 6.3 Logout

`POST /api/v1/auth/logout`

Header:

```http
Authorization: Bearer <access_token>
```

Body:

```json
{
  "refresh_token": "..."
}
```

## 6.4 Analyze chat

`POST /api/v1/analyze-chat`

Header:

```http
Authorization: Bearer <access_token>
```

Body:

```json
{
  "relationship_role": "friend",
  "chat_history": [
    { "sender": "me", "text": "..." },
    { "sender": "other", "text": "..." }
  ],
  "locale": "vi-VN",
  "conversation_id": "optional-id"
}
```

`relationship_role` hợp lệ:
- `crush`
- `friend`
- `family`
- `customer`

## 7) Model Kotlin gợi ý

```kotlin
@Serializable
data class GoogleLoginRequest(val id_token: String)

@Serializable
data class RefreshRequest(val refresh_token: String)

@Serializable
data class LogoutRequest(val refresh_token: String)

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String,
    val refresh_expires_in: Int,
    val user: UserInfo
)

@Serializable
data class UserInfo(
    val provider: String,
    val subject: String,
    val email: String? = null,
    val name: String? = null,
    val picture: String? = null
)

@Serializable
data class ChatMessage(
    val sender: String,
    val text: String,
    val timestamp: String? = null
)

@Serializable
data class AnalyzeRequest(
    val relationship_role: String,
    val chat_history: List<ChatMessage>,
    val locale: String = "vi-VN",
    val conversation_id: String? = null
)
```

## 8) API service (Retrofit) gợi ý

```kotlin
interface ChatAssistantApi {
    @POST("/api/v1/auth/google/login")
    suspend fun login(@Body req: GoogleLoginRequest): TokenResponse

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): TokenResponse

    @POST("/api/v1/auth/logout")
    suspend fun logout(
        @Header("Authorization") bearer: String,
        @Body req: LogoutRequest
    ): LogoutResponse

    @POST("/api/v1/analyze-chat")
    suspend fun analyze(
        @Header("Authorization") bearer: String,
        @Body req: AnalyzeRequest
    ): AnalyzeResponse
}
```

## 9) Lưu token an toàn

Khuyến nghị:
- Lưu token trong EncryptedSharedPreferences hoặc DataStore + Android Keystore.
- Không log token ra Logcat.
- Không hardcode token trong source.

Nên lưu:
- `access_token`
- `refresh_token`
- `access_token_expired_at_epoch`

## 10) Auto refresh token

Dùng OkHttp `Authenticator` hoặc tầng repository:

1. Request API bằng access token.
2. Nếu nhận `401`:
   - gọi `/auth/refresh` với refresh token hiện tại.
   - lưu cặp token mới.
   - retry request cũ đúng 1 lần.
3. Nếu refresh fail:
   - clear session local.
   - điều hướng về màn login.

Lưu ý:
- Backend dùng refresh rotation một lần. Không được dùng lại refresh token cũ.
- Tránh nhiều request cùng refresh đồng thời; dùng mutex/single-flight.

## 11) Google Sign-In lấy `id_token` trên Android

Ý tưởng flow:

1. Trigger Google Sign-In qua Credential Manager.
2. Nhận Google credential.
3. Lấy `idToken`.
4. Gọi `/auth/google/login`.

Pseudo:

```kotlin
suspend fun signInAndExchangeToken(): Result<Unit> {
    val googleIdToken: String = googleSignInProvider.getIdToken()
    val token = api.login(GoogleLoginRequest(id_token = googleIdToken))
    sessionStore.save(token)
    return Result.success(Unit)
}
```

## 12) Cách lấy text tin nhắn để phân tích

Bạn có 4 hướng. Nên bắt đầu từ hướng 1 để nhanh ra sản phẩm.

## 12.1 Cách 1: User paste thủ công (khuyến nghị MVP)

- Tạo màn nhập hội thoại bằng Compose.
- User copy text từ app chat và paste vào.
- Parse thành danh sách `ChatMessage`.

Ưu điểm:
- Không vướng policy nhạy cảm.
- Triển khai nhanh.

## 12.2 Cách 2: NotificationListenerService

Dùng khi muốn đọc nội dung tin nhắn từ notification.

Yêu cầu:
- Permission notification listener (user bật thủ công trong settings).
- Parse `StatusBarNotification`.

Giới hạn:
- Chỉ đọc được nội dung xuất hiện trong notification.
- Nhiều app chat che nội dung hoặc không cung cấp đầy đủ.

## 12.3 Cách 3: AccessibilityService đọc UI text

Dùng để đọc text trực tiếp từ cây view của app khác.

Yêu cầu:
- Enable accessibility service.
- Traverse node tree và thu text.

Rủi ro:
- Nhạy cảm quyền riêng tư.
- Dễ bị từ chối khi publish nếu không có lý do rất rõ.
- Cần giải thích minh bạch cho user về dữ liệu thu thập.

## 12.4 Cách 4: OCR màn hình (MediaProjection + ML Kit)

- Chụp màn hình rồi OCR để trích text.
- Phức tạp hơn và tốn pin/CPU.

Khuyến nghị:
- Chỉ dùng cho use-case đặc thù.
- Cần consent rõ ràng.

## 13) Chuẩn hóa dữ liệu chat trước khi gọi backend

Trước khi gửi:

1. Loại bỏ dòng rỗng.
2. Giới hạn chiều dài mỗi message.
3. Giữ thứ tự thời gian.
4. Map sender thành `me` hoặc `other`.
5. Giới hạn số lượng message gửi mỗi lần.

## 14) Sinh widget/UI gợi ý trả lời

Bạn có 2 kiểu phổ biến:

## 14.1 In-app widget (Compose component)

- Tạo `SuggestionCard` hiển thị 4 scenario: `red/yellow/green/gray`.
- Hiển thị:
  - `title`
  - `interpretation`
  - `sample_reply`
  - nút `Copy`

## 14.2 Home Screen App Widget (Glance)

- Đồng bộ kết quả phân tích gần nhất vào local store.
- Widget hiển thị:
  - summary ngắn
  - 1-2 gợi ý phản hồi nhanh
  - nút mở app vào màn chi tiết

Flow:

1. User phân tích chat trong app.
2. App lưu kết quả mới nhất.
3. Trigger update widget qua `GlanceAppWidgetManager`.

## 15) Compose screen gợi ý

- `LoginScreen`: nút sign in Google.
- `ConversationInputScreen`: nhập/paste tin nhắn, chọn role.
- `AnalyzingScreen`: loading + retry.
- `AnalysisResultScreen`: card scenario + copy reply.
- `SettingsScreen`: logout, quyền notification/accessibility, privacy.

## 16) Error handling map cho mobile

- `401`:
  - nếu từ analyze: refresh token rồi retry.
  - nếu refresh fail: logout local, về login.
- `429`: hiển thị quá tải, chờ rồi thử lại.
- `502/504`: lỗi upstream AI, cho phép retry có backoff.
- `500`: lỗi cấu hình backend, hiển thị thông báo hệ thống.

## 17) Bảo mật và pháp lý

- Luôn xin consent rõ ràng trước khi đọc notification/accessibility/screen content.
- Chỉ thu thập dữ liệu tối thiểu cần thiết.
- Cho phép user xóa dữ liệu đã lưu.
- Mã hóa dữ liệu nhạy cảm local.
- Không gửi dữ liệu ngoài phạm vi chức năng đã công bố.

## 18) Checklist go-live

- Android release SHA đã đăng ký.
- `GOOGLE_CLIENT_IDS` backend khớp client thực tế.
- Auth refresh flow đã test.
- Logout revoke flow đã test.
- Rate limit và retry policy đã test.
- Privacy policy đã mô tả rõ cách thu thập dữ liệu chat.
- QA trên Android 10-14.

## 19) Gợi ý lộ trình triển khai

1. MVP:
   - Login Google
   - Paste hội thoại thủ công
   - Analyze + hiển thị kết quả
2. v1:
   - Auto refresh token
   - Copy reply và history
3. v2:
   - Notification listener
   - Home screen widget
4. v3:
   - Accessibility/OCR nếu thật sự cần và đáp ứng policy
