# Báo Cáo Kịch Bản Chạy Test Selenium Có Rollback & Database Check

Dưới đây là một bảng kết quả minh họa chi tiết cho việc kiểm thử UI kết hợp kiểm tra DB. Script Java được thiết lập theo mô hình **(Thao tác Selenium -> Try Catch -> Lấy Data DB Verify -> Rollback JDBC Transaction)**.

## 1. Bảng Kết Quả Test (Định dạng yêu cầu)

| TC ID | Test Objective | Pre-condition | Test Steps | Test Data | Expected Result | Actual Result | Check DB | Status (Pass/Fail) | Note (fail ghi rõ lý do) |
|---|---|---|---|---|---|---|---|---|---|
| **SEL-01** | Tạo / Đóng tin tuyển dụng | Login role HR, DB đang kết nối | 1. Vào màn hình tạo<br>2. Nhập thông tin (Title, Skill, Phân loại)<br>3. Bấm Submit | Job Title = `Job Auto Test 01`, Kỹ năng: `Java` | UI báo tạo thành công, chuyển về danh sách | Lỗi DOM Timeout ở nút Submit | Xóa dữ liệu rác (Roleback) bằng lệnh `DELETE FROM job_ad WHERE title='...'` | **Fail** | `NoSuchElementException` ở `button[type='submit']` do màn hình loading bị đè mờ chặn tương tác (Overlay blocking). Do đó insert chưa diễn ra. |
| **SEL-02** | Xem danh sách CV | Màn hình tin tuyển dụng đang mở | 1. Bấm 'Xem CV' của tin tuyển dụng có sẵn<br>2. Xác minh table load được | `jobAdId = 15` | Hiển thị bảng ứng viên, số lượng khớp | Hiển thị bảng danh sách đúng | `SELECT count(*) FROM job_ad_candidate WHERE job_ad_id=15` -> Trùng khớp kết quả API/UI (2 ứng viên) | **Pass** | Lấy dữ liệu Select đúng theo ID db. Không có Data rác. |
| **SEL-03** | Chuyển vòng ứng viên (APPLY -> SCAN_CV) | Có Candidate (ID=10) ở trạng thái `APPLY` | 1. Mở CV ứng viên<br>2. Đổi trạng thái -> Lọc Hồ Sơ<br>3. Lưu | Candidate ID=10, new status `SCAN_CV` | Popup thành công; CV chuyển màn hình | Popup xuất hiện "Cập nhật vòng thành công" | `SELECT status FROM job_ad_candidate WHERE id=10` trả về `SCAN_CV`. Sau đó script chạy `UPDATE` trả về lại `APPLY` (Rollback xong). | **Pass** | Cập nhật DB lưu thành công và Rollback script chạy đủ các bước. |
| **SEL-04** | Chuyển nhảy cóc (APPLY -> ONBOARD) | Có Candidate (ID=11) ở trạng thái `APPLY` | 1. Chọn CV ứng viên<br>2. Cố tình chọn thẳng trạng thái ONBOARD<br>3. Lưu | Candidate ID=11, Status: ONBOARD | FE chặn nút hoặc BE chặn trả về lỗi 4xx | FE Không chặn nút lưu, BE trả Error `500 Internal Server Error` | DB Query: Status giữ nguyên `APPLY` | **Fail** | **Bug:** Lỗi FE không vô hiệu hóa nút chuyển nhảy cóc, BE bị Exception không handle throw bad request. |
| **SEL-05** | Loại ứng viên phải có lý do | Có ứng viên (ID=12) ở vòng `SCAN_CV` | 1. Bắt nút Loại ứng viên<br>2. Bỏ trống text area<br>3. Nhấn Xác nhận | Textbox lý do = rỗng | Validator báo bắt buộc | Validator UI báo: "Vui lòng nhập lý do loại" | DB Check: Status không đổi | **Pass** | Validate trên UI hoạt động tốt. |
| **SEL-06** | Xác nhận ứng viên Onboard | Ứng viên (ID=13) ở vòng `OFFER_ACCEPT` | 1. Chuyển trạng thái -> Đi làm<br>2. Chọn ngày<br>3. Lưu | `onboard_date` = `2026-05-10` | UI báo Onboard thành công | Cập nhật vòng thành công | DB Update status `ONBOARD`, check đúng ngày. Rollback lại `OFFER_ACCEPT`. | **Pass** | Flow chuẩn |
| **SEL-07** | Tạo lịch phỏng vấn Online | Đang ở tab "Lịch phỏng vấn" ứng viên | 1. Nhấn Tạo Lịch<br>2. Điền: Online<br>3. Thiếu Link họp<br>4. Lưu | type=`ONLINE`, link = `null` | Field hiển thị viền đỏ báo thiếu link | Viền đỏ xuất hiện, submit bị chặn | Bảng `calendar` không sinh bản ghi thừa nào. | **Pass** | Form validation check ổn. |
| **SEL-08** | Cảnh báo trùng lịch cho Interviewer | Lịch 1 đã có (ID_INT=5, 10:00-11:00) | 1. Tạo lịch 2 cho ID_INT=5 lúc 10:30-11:30<br>2. Lưu | Interviwer=5, Giờ giao nhau | Popup báo "Người phỏng vấn bận" | Request thành công (Bị lỗi - Lịch 2 vẫn tạo ra) | DB Query trả về 2 bản ghi cho ID=5 giờ Overlay nhau -> BE miss check. Xóa bản ghi 2 đi. | **Fail** | **BUG:** Backend không validate logic Overlap khi thời gian bắt đầu nằm ở giữa slot cũ. CoreService miss validation overlap time. |

## Kiến trúc Script Selenium (Tham khảo module vừa tạo)
1. **Setup(`@BeforeAll`)**: Mở JDBC Postgres (Core-service) và MySQL (User-service). Tắt `AutoCommit`.
2. **Execute**: Selenium webdriver click chuyển trạng thái ứng viên.
3. **Data Check**: Mở luồng Statement `SELECT` query vào đúng table tương ứng. Ví dụ: `job_ad_candidate` lấy Status; `calendar` tìm ngày giờ.
4. **Data Check vào kết quả thực**: So sánh cái UI hiển thị (Actual Result) với Data Check dưới DB. Điền Pass/Fail.
5. **Rollback(`@AfterTest`)**: Gọi hàm `coreDbConnection.rollback()` cho các câu `INSERT/UPDATE/DELETE`, hoặc chạy hàm SQL Backup trả data về State tĩnh ban đầu để Test Case khác không bị "chết dây chuyền".

*(File Java script đã được tạo nằm tại `selenium-tests/src/test/java/com/cvconnect/selenium/HRAdvancedWorkflowTest.java`)*