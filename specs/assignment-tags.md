# Đặc tả Backend: Tag cho bài tập

## Feature

Cho phép giáo viên gắn tag cho bài tập lẻ (`Assignment`) để phân loại, tìm
kiếm và tái sử dụng. Đợt đầu không gắn tag trực tiếp cho phiếu bài tập
(`AssignmentSheet`).

Ba nhóm tag cố định:

- Khối lớp: `10`, `11`, `12`.
- Phân môn: `Đại số`, `Hình học`.
- Độ khó: `Dễ`, `Vừa`, `Khó`.

Tag là tùy chọn trong kho cá nhân. Bài chia sẻ vào Thư viện cộng đồng
(`visibility = PUBLIC`) phải có đủ một tag của mỗi nhóm.

## Business Goal

- Giúp giáo viên tổ chức kho bài theo khối lớp, phân môn và độ khó.
- Cho phép tìm nhanh bài phù hợp để giao, đưa vào phiếu hoặc tái sử dụng.
- Đảm bảo bài trong Thư viện cộng đồng có metadata đầy đủ và đáng tin cậy.
- Chuẩn bị data model để Admin quản lý giá trị tag về sau mà không đổi cấu trúc
  của `Assignment`.

## Functional Requirements

- Giáo viên có thể gắn, thay đổi hoặc bỏ tag khi tạo/cập nhật bài tập lẻ.
- Giáo viên chỉ chọn tag đang hoạt động từ danh sách hệ thống; chưa có quyền
  tạo, sửa, xóa tag trong đợt này.
- Một bài có tối đa một tag cho mỗi nhóm: khối lớp, phân môn, độ khó.
- API trả danh sách/chi tiết bài phải trả các tag đã gắn.
- API danh sách bài hỗ trợ lọc kết hợp theo cả ba nhóm tag.
- API cung cấp danh sách tag đang hoạt động để frontend tạo bộ lọc và control
  chọn tag.
- Tag của bài được sao chép nguyên vẹn khi tạo master sheet, giao cho lớp, hoặc
  clone từ Thư viện.
- Chia sẻ ra Thư viện bị từ chối nếu bài thiếu bất kỳ nhóm tag bắt buộc nào.

## Business Rules

- Tag chỉ gắn trực tiếp với `Assignment`; phiếu không có tag riêng trong scope
  này.
- Bài cũ, bài nháp và bài trong kho riêng có thể thiếu tag. Không backfill tự
  động dữ liệu cũ.
- Tag được chọn phải tồn tại và `active = true`.
- Không được gắn trùng `tagId`, hoặc hai tag cùng `type` cho một bài.
- Bài `PUBLIC` bắt buộc có đúng một `GRADE`, một `SUBJECT`, một `DIFFICULTY`.
- Rule public được kiểm tra lúc tạo, cập nhật, và `PATCH` visibility.
- Bản clone là snapshot: đổi tag bài nguồn không làm đổi tag bản đã giao,
  master sheet, hay bài do giáo viên khác clone.
- Nhiều filter tag dùng phép AND: bài trả về phải khớp mọi filter được gửi.
- Seed tag là dữ liệu hệ thống; Admin CRUD tag là scope đợt sau.

## Data Model

Dùng hai bảng mới. Không tạo bảng `tag_types`; `type` là enum cố định vì mới
có ba nhóm ít thay đổi.

### Bảng `tags`

| Cột | Kiểu | Ràng buộc | Mô tả |
| --- | --- | --- | --- |
| `id` | bigint | PK | ID tag |
| `name` | varchar | not null | Tên hiển thị |
| `type` | enum/varchar | not null | `GRADE`, `SUBJECT`, `DIFFICULTY` |
| `active` | boolean | not null, default true | Có được gắn mới không |
| `created_at`, `updated_at` | datetime | `BaseEntity` | Audit fields |

Ràng buộc unique (`type`, `name`) ngăn hai tag cùng tên trong một nhóm.

### Bảng `assignment_tags`

| Cột | Kiểu | Ràng buộc | Mô tả |
| --- | --- | --- | --- |
| `assignment_id` | bigint | FK `assignments.id` | Bài được gắn tag |
| `tag_id` | bigint | FK `tags.id` | Tag được gắn |

Ràng buộc unique (`assignment_id`, `tag_id`) ngăn liên kết trùng. Rule "một tag
mỗi type" validate tại Service vì `type` nằm trong bảng `tags`.

```text
Assignment 1 --- * AssignmentTag * --- 1 Tag
```

Dùng entity liên kết `AssignmentTag` thay cho `@ManyToMany` trực tiếp để dễ mở
rộng metadata/audit sau này.

### Seed data

| Type | Giá trị |
| --- | --- |
| `GRADE` | `10`, `11`, `12` |
| `SUBJECT` | `Đại số`, `Hình học` |
| `DIFFICULTY` | `Dễ`, `Vừa`, `Khó` |

## API Contract

Các route tuân theo API version hiện có của dự án.

### Danh sách tag

`GET /tags?type={type}`

- Người dùng đã đăng nhập được phép đọc.
- Chỉ trả tag `active = true`.
- `type` optional: `GRADE`, `SUBJECT`, `DIFFICULTY`.
- Response `List<TagResponse>`: `id`, `name`, `type`.

### Tạo và cập nhật bài

Mở rộng `POST /assignments` và `PUT /assignments/{id}`:

```json
{ "tagIds": [1, 4, 7] }
```

- `tagIds` optional.
- Khi có mặt, đây là toàn bộ tập tag mong muốn (replace semantics).
- Khi cập nhật mà không gửi `tagIds`, giữ tag cũ để tương thích client cũ.
- Mảng rỗng bỏ toàn bộ tag; bị từ chối nếu bài đang/được đặt `PUBLIC`.

Mở rộng `AssignmentResponse`:

```json
{
  "tags": [
    { "id": 1, "name": "10", "type": "GRADE" },
    { "id": 4, "name": "Đại số", "type": "SUBJECT" },
    { "id": 7, "name": "Vừa", "type": "DIFFICULTY" }
  ]
}
```

### Cập nhật visibility

`PATCH /assignments/{id}/visibility` giữ payload hiện có. Nếu visibility mới là
`PUBLIC`, Service phải kiểm tra đủ ba nhóm tag trước khi lưu.

### Tìm kiếm và lọc bài

Mở rộng `GET /assignments`:

```text
?gradeTagId=1&subjectTagId=4&difficultyTagId=7
```

- Mỗi parameter optional và phải đúng type tương ứng.
- Kết hợp filter bằng AND; giữ nguyên `keyword`, `classCode`, `status`, phân
  trang và phân quyền hiện có.
- Tag ID không tồn tại, inactive hoặc sai type trả `400 Bad Request`.

## Validation

- `tagIds` không chứa `null`, không trùng, tối đa ba ID.
- Load toàn bộ tag bằng một query; số kết quả phải bằng số ID yêu cầu.
- Mọi tag phải active, và type không được trùng.
- Public yêu cầu chính xác tập type `{GRADE, SUBJECT, DIFFICULTY}`.
- Filter tag phải tồn tại, active và đúng type của query parameter.
- Dùng `BadRequestException`; `GlobalExceptionHandler` trả `{ "error": "..." }`.
- Thông báo public thiếu tag: `Cần chọn Khối lớp, Phân môn và Độ khó trước khi
  chia sẻ vào Thư viện cộng đồng.` Frontend cảnh báo sớm, BE thực thi rule cuối.

## Implementation Constraints

- Tuân thủ `Repository -> Service -> ServiceImpl -> Controller`; controller
  không trả entity trực tiếp.
- Entity mới kế thừa `BaseEntity`, dùng `@Table` tên số nhiều và các FK
  `@ManyToOne(fetch = FetchType.LAZY)` phù hợp.
- Dùng DTO riêng và `@Valid @RequestBody` ở controller.
- Không sửa `application.properties`, `application-local.properties`, `.env`.
- Migration/seed phải idempotent và an toàn cho database có dữ liệu cũ.
- Tránh N+1 khi trả danh sách bài có tag và vẫn đảm bảo phân trang đúng.
- Không hardcode ID tag trong Java.
- Tái sử dụng Security hiện có; endpoint đọc tag yêu cầu đăng nhập.

## Decisions After Implementation

- Bài nguồn `ARCHIVED` trong Kho bài tập vẫn là tài sản của giáo viên và được
  phép cập nhật tag. Bản `PUBLISHED` đã giao cho lớp là snapshot, không sửa tag
  trực tiếp từ lớp.
- Khi cập nhật một tập tag, phải xóa và flush các liên kết `AssignmentTag` cũ
  trước khi thêm liên kết mới. Điều này tránh PostgreSQL unique violation cho
  cặp (`assignment_id`, `tag_id`) khi tag không thay đổi.
- `AssignmentResponse.fromEntity` và biến thể không content phải map `tags`.
  Đây là điều kiện để item trong `AssignmentSheetResponse` trả đúng tag của bài
  con.
- Giao lại bài chỉ cho lớp chưa có clone từ bài nguồn. Lớp đã giao phải được
  coi là read-only trong publish dialog để tránh tạo assignment trùng.

## Acceptance Criteria

- Tạo bài nháp không tag thành công.
- Gắn tối đa một tag mỗi nhóm và response trả đúng tags.
- Tag trùng, cùng nhóm, không tồn tại hoặc inactive bị từ chối với lỗi rõ ràng.
- Bài PRIVATE được thiếu tag; chuyển PUBLIC khi thiếu tag bị từ chối đúng thông
  báo; bài đủ ba nhóm chuyển PUBLIC thành công.
- `GET /assignments` lọc đúng từng tag và nhiều tag bằng AND, không làm sai
  phân trang/phân quyền.
- `GET /tags` trả tag seed active và lọc được theo type.
- Tags được sao chép khi publish bài, tạo/publish sheet và clone từ Thư viện.
- Đổi tag bài nguồn không đổi tags của clone đã tồn tại.
- Unit test và controller test bao phủ happy path, validation, public rule.

## Task Checklist

- [ ] Kiểm tra convention migration/seed hiện có và chọn cách seed idempotent.
- [ ] Tạo `TagType`, `Tag`, `AssignmentTag` entities và repositories.
- [ ] Tạo `TagResponse`; mở rộng DTO create/update/response của Assignment.
- [ ] Cập nhật mapper để trả tag trong `AssignmentResponse`.
- [ ] Implement Service validation, replace semantics và public rule.
- [ ] Sao chép tags tại toàn bộ helper clone assignment hiện có.
- [ ] Thêm `GET /tags` và filter tag vào `GET /assignments`.
- [ ] Viết/cập nhật test Service, Controller, Repository/Specification.
- [ ] Chạy `./gradlew test`, sau đó `./gradlew build`.
- [ ] Cập nhật API reference/Postman nếu các tài liệu đó đang được duy trì.
