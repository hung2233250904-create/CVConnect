package com.cvconnect.selenium;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class CandidateStateFlowTest {
        private WebDriver driver;
        private WebDriverWait wait;
        private Connection dbConnection;

        static final String BASE_URL = "http://localhost:3000";
        static final String SCREENSHOT_DIR = "target/screenshots/";

        // Cấu hình Database PostgreSQL (Đúng với Docker config)
        static final String DB_URL = "jdbc:postgresql://localhost:5433/cvconnect_core_service";
        static final String DB_USER = "postgres";
        static final String DB_PASS = "123456";

        @BeforeAll
        public void setup() {
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--remote-allow-origins=*");
                driver = new ChromeDriver(options);
                driver.manage().window().maximize();
                // WebDriverWait 20 giây cho từng bước test
                wait = new WebDriverWait(driver, Duration.ofSeconds(20));
                new File(SCREENSHOT_DIR).mkdirs();

                try {
                        dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                        System.out.println("Đã kết nối DB thành công!");
                } catch (Exception e) {
                        System.out.println("Lỗi kết nối DB: " + e.getMessage());
                }

                // Mở trang đăng nhập và chờ bạn tự đăng nhập (tối đa 5 phút)
                driver.get(BASE_URL + "/auth/login");
                System.out
                                .println(">>> Vui lòng tự đăng nhập trên Chrome. Test sẽ tự chạy sau khi bạn vào được trang nội bộ...");
                WebDriverWait loginWait = new WebDriverWait(driver, Duration.ofMinutes(5));
                loginWait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/auth/login")));
                System.out.println("Đã đăng nhập thành công! Bắt đầu chạy test...");
                // Chờ thêm 1 giây để token kịp lưu vào LocalStorage
                try {
                        Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
        }

        @AfterAll
        public void teardown() {
                if (driver != null) {
                        // driver.quit(); // Đã comment để trình duyệt không tự đóng khi lỗi
                }
                try {
                        if (dbConnection != null)
                                dbConnection.close();
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        @AfterEach
        public void captureScreenshotOnResult(TestInfo testInfo) {
                String testName = testInfo.getTestMethod().isPresent() ? testInfo.getTestMethod().get().getName()
                                : "UnknownTest";
                takeScreenshot(testName + "_Finished");
        }

        private void takeScreenshot(String fileName) {
                try {
                        TakesScreenshot ts = (TakesScreenshot) driver;
                        File source = ts.getScreenshotAs(OutputType.FILE);
                        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                        FileUtils.copyFile(source, new File(SCREENSHOT_DIR + fileName + "_" + timestamp + ".png"));
                } catch (Exception ignored) {
                }
        }

        private void rollbackCandidate2ToApplied() {
                try {
                        dbConnection.prepareStatement(
                                        "UPDATE job_ad_candidate SET candidate_status='APPLIED', eliminate_reason_type=NULL, eliminate_reason_detail=NULL, onboard_date=NULL WHERE id=2")
                                        .executeUpdate();
                        dbConnection
                                        .prepareStatement(
                                                        "UPDATE job_ad_process_candidate SET is_current_process=false WHERE job_ad_candidate_id=2")
                                        .executeUpdate();
                        dbConnection.prepareStatement(
                                        "UPDATE job_ad_process_candidate SET action_date=NULL WHERE job_ad_candidate_id=2 AND job_ad_process_id != 5")
                                        .executeUpdate();
                        dbConnection.prepareStatement(
                                        "UPDATE job_ad_process_candidate SET is_current_process=true WHERE job_ad_candidate_id=2 AND job_ad_process_id=5")
                                        .executeUpdate();
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        private void safeClick(By locator) {
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
                try {
                        element.click();
                } catch (Exception e) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                }
        }

        /**
         * Click vào radio button bên trong modal "Chuyển vòng" theo tên hiển thị.
         */
        private void clickProcessRadio(String processName) {
                wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                                "//span[contains(text(), '" + processName + "')]/ancestor::label"))).click();
                // Chờ Vue reactivity cập nhật formInput.process xong
                try {
                        Thread.sleep(800);
                } catch (Exception ignored) {
                }
        }

        /*
         * =============================================================================
         * ====
         * TEST CASE 1: ST_HR_01 - Chuyển vòng hợp lệ APPLY → SCAN_CV
         * =============================================================================
         * ====
         */
        @Test
        public void test01_ST_HR_01_changeCandidateProcess() {
                driver.get(BASE_URL + "/org/candidate/detail/2");
                try {
                        safeClick(By.xpath("//div[@title='Chuyển vòng' or contains(@class, 'next-step-btn')]"));
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }
                        clickProcessRadio("Thi tuyển");
                        // Chờ nút Xác nhận enabled (modal đang fetch config mail)
                        safeClick(By.xpath("//button[normalize-space(.)='Xác nhận' and not(@disabled)]"));

                        // Đợi modal đóng (nút Xác nhận biến mất) báo hiệu API thành công
                        wait.until(ExpectedConditions
                                        .invisibilityOfElementLocated(
                                                        By.xpath("//button[normalize-space(.)='Xác nhận']")));

                        // CHECK DB
                        PreparedStatement ps = dbConnection
                                        .prepareStatement("SELECT candidate_status FROM job_ad_candidate WHERE id=2");
                        ResultSet rs = ps.executeQuery();
                        if (rs.next())
                                Assertions.assertEquals("IN_PROGRESS", rs.getString("candidate_status"));
                } catch (Exception e) {
                        Assertions.fail(e.getMessage());
                } finally {
                        // ROLLBACK
                        rollbackCandidate2ToApplied();
                }
        }

        /*
         * =============================================================================
         * ====
         * TEST CASE 2: ST_HR_02 - Chuyển đủ các vòng đến ONBOARD
         * =============================================================================
         * ====
         */
        @Test
        public void test02_ST_HR_02_fullProcessToOnboard() {
                driver.get(BASE_URL + "/org/candidate/detail/2");
                try {
                        // Bước 1: Ứng tuyển → Thi tuyển
                        safeClick(By.xpath("//div[@title='Chuyển vòng' or contains(@class, 'next-step-btn')]"));
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }
                        clickProcessRadio("Thi tuyển");
                        safeClick(By.xpath("//button[normalize-space(.)='Xác nhận' and not(@disabled)]"));
                        wait.until(ExpectedConditions
                                        .invisibilityOfElementLocated(
                                                        By.xpath("//button[normalize-space(.)='Xác nhận']")));
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        } // Đợi UI cập nhật

                        // Bước 2: Thi tuyển → Phỏng vấn chuyên môn
                        safeClick(By.xpath("//div[@title='Chuyển vòng' or contains(@class, 'next-step-btn')]"));
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }
                        clickProcessRadio("Phỏng vấn chuyên môn");
                        safeClick(By.xpath("//button[normalize-space(.)='Xác nhận' and not(@disabled)]"));
                        wait.until(ExpectedConditions
                                        .invisibilityOfElementLocated(
                                                        By.xpath("//button[normalize-space(.)='Xác nhận']")));
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }

                        // Bước 3: Phỏng vấn chuyên môn → Phỏng vấn khách hàng
                        safeClick(By.xpath("//div[@title='Chuyển vòng' or contains(@class, 'next-step-btn')]"));
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }
                        clickProcessRadio("Phỏng vấn khách hàng");
                        safeClick(By.xpath("//button[normalize-space(.)='Xác nhận' and not(@disabled)]"));
                        wait.until(ExpectedConditions
                                        .invisibilityOfElementLocated(
                                                        By.xpath("//button[normalize-space(.)='Xác nhận']")));
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }

                        // Bước 4: Phỏng vấn khách hàng → Onboard (cần nhập ngày)
                        safeClick(By.xpath("//div[@title='Chuyển vòng' or contains(@class, 'next-step-btn')]"));
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }
                        clickProcessRadio("Onboard");
                        // Chờ datepicker hiện cho Onboard
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }
                        // Click vào ô date picker mở popup
                        WebElement datepicker = wait.until(ExpectedConditions.elementToBeClickable(
                                        By.xpath(
                                                        "//label[contains(text(),'Chọn ngày')]/following-sibling::div | //div[contains(@class,'onboard-datepicker')]//input | //div[contains(@class,'dp__input_wrap')]//input")));
                        datepicker.click();
                        try {
                                Thread.sleep(500);
                        } catch (Exception ignored) {
                        }

                        // Chọn ngày 15 trên lịch
                        safeClick(By.xpath(
                                        "//*[contains(@class, 'dp__cell_inner') and text()='15'] | //div[contains(@class, 'dp__calendar_item')]//div[normalize-space(.)='15']"));
                        try {
                                Thread.sleep(500);
                        } catch (Exception ignored) {
                        }

                        // Bấm nút Select của VueDatePicker để chốt ngày
                        safeClick(By.xpath("//button[contains(., 'Select') or contains(@class, 'dp__action_select')]"));
                        try {
                                Thread.sleep(500);
                        } catch (Exception ignored) {
                        }

                        safeClick(By.xpath("//button[normalize-space(.)='Xác nhận' and not(@disabled)]"));
                        wait.until(ExpectedConditions
                                        .invisibilityOfElementLocated(
                                                        By.xpath("//button[normalize-space(.)='Xác nhận']")));

                        // CHECK DB
                        PreparedStatement ps = dbConnection
                                        .prepareStatement("SELECT candidate_status FROM job_ad_candidate WHERE id=2");
                        ResultSet rs = ps.executeQuery();
                        if (rs.next())
                                Assertions.assertEquals("WAITING_ONBOARDING", rs.getString("candidate_status"));
                } catch (Exception e) {
                        try {
                                java.io.PrintWriter pw = new java.io.PrintWriter(
                                                new java.io.File("error_test02_fail.txt"));
                                e.printStackTrace(pw);
                                pw.close();
                        } catch (Exception ignored) {
                        }
                        Assertions.fail(e.getMessage());
                } finally {
                        rollbackCandidate2ToApplied();
                }
        }

        /*
         * =============================================================================
         * ====
         * TEST CASE 3: ST_HR_03 - Chặn nhảy cóc vòng (Ủng tuyển → Phỏng vấn chuyên môn)
         * =============================================================================
         * ====
         */
        @Test
        public void test03_ST_HR_03_preventSkipStep() {
                driver.get(BASE_URL + "/org/candidate/detail/2");
                try {
                        wait.until(ExpectedConditions
                                        .elementToBeClickable(By.xpath(
                                                        "//div[@title='Chuyển vòng' or contains(@class, 'next-step-btn')]")))
                                        .click();
                        // Chờ modal bật lên (đợi animation)
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }

                        clickProcessRadio("Phỏng vấn chuyên môn");
                        safeClick(By.xpath("//button[normalize-space(.)='Xác nhận' and not(@disabled)]"));
                        wait.until(ExpectedConditions
                                        .invisibilityOfElementLocated(
                                                        By.xpath("//button[normalize-space(.)='Xác nhận']")));

                        // Hệ thống phải hiển thị lỗi vì nhảy cóc từ Ứng tuyển sang Phỏng vấn chuyên môn
                        // không hợp lệ
                        WebElement toast = wait
                                        .until(ExpectedConditions.visibilityOfElementLocated(
                                                        By.xpath("//*[contains(text(), 'lỗi')]")));
                        Assertions.assertTrue(toast.isDisplayed());
                } catch (TimeoutException e) {
                        Assertions.fail(
                                        "ST_HR_03 FAIL: BUG FE không hiển thị lỗi khi nhảy cóc từ Ứng tuyển sang Phỏng vấn chuyên môn!");
                } catch (Exception e) {
                        Assertions.fail(e.getMessage());
                } finally {
                        rollbackCandidate2ToApplied();
                }
        }

        /*
         * =============================================================================
         * ====
         * TEST CASE 4: ST_HR_04 - Loại ứng viên có lý do
         * =============================================================================
         * ====
         */
        @Test
        public void test04_ST_HR_04_eliminateCandidate() {
                driver.get(BASE_URL + "/org/candidate/detail/2");
                try {
                        wait.until(ExpectedConditions
                                        .elementToBeClickable(By.xpath(
                                                        "//div[@title='Loại ứng viên' or contains(@class, 'reject-btn')]")))
                                        .click();
                        // Mở dropdown "Lí do loại ứng viên"
                        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                                        "//div[contains(@class, 'search-select-input') and contains(., 'Lí do loại')]//button[contains(@class, 'selector')]")))
                                        .click();
                        try {
                                Thread.sleep(500);
                        } catch (Exception ignored) {
                        }

                        // Chọn "Thiếu kinh nghiệm" trong danh sách dropdown
                        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                                        "//*[contains(text(), 'Thiếu kinh nghiệm')]//ancestor-or-self::li | " +
                                                        "//*[contains(text(), 'Thiếu kinh nghiệm')]")))
                                        .click();
                        try {
                                Thread.sleep(500);
                        } catch (Exception ignored) {
                        }
                        safeClick(By.xpath("//button[normalize-space(.)='Xác nhận' and not(@disabled)]"));
                        wait.until(ExpectedConditions
                                        .invisibilityOfElementLocated(
                                                        By.xpath("//button[normalize-space(.)='Xác nhận']")));
                        wait.until(ExpectedConditions
                                        .presenceOfElementLocated(By.xpath("//*[contains(text(), 'Đã loại')]")));

                        // CHECK DB
                        ResultSet rs = dbConnection
                                        .prepareStatement("SELECT candidate_status FROM job_ad_candidate WHERE id=2")
                                        .executeQuery();
                        if (rs.next())
                                Assertions.assertEquals("REJECTED", rs.getString("candidate_status"));
                } catch (Exception e) {
                        Assertions.fail(e.getMessage());
                } finally {
                        rollbackCandidate2ToApplied();
                }
        }

        /*
         * =============================================================================
         * ====
         * TEST CASE 5: ST_HR_05 - Loại ứng viên không chọn lý do
         * =============================================================================
         * ====
         */
        @Test
        public void test05_ST_HR_05_eliminateWithoutReason() {
                driver.get(BASE_URL + "/org/candidate/detail/2");
                try {
                        wait.until(ExpectedConditions
                                        .elementToBeClickable(By.xpath(
                                                        "//div[@title='Loại ứng viên' or contains(@class, 'reject-btn')]")))
                                        .click();
                        // Chờ modal mở xong
                        try {
                                Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }
                        // Kỳ vọng: nút Xác nhận bị disable khi chưa chọn lý do
                        WebElement submitBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                                        By.xpath("//button[contains(.,'Xác nhận')]")));

                        // Kỳ vọng: nút bị disable
                        Assertions.assertFalse(submitBtn.isEnabled(), "Nút xác nhận không bị disable khi thiếu lý do");
                } catch (Exception e) {
                        Assertions.fail(e.getMessage());
                }
        }

        /*
         * =============================================================================
         * ====
         * TEST CASE 6: ST_HR_06 - Chặn loại ứng viên đã bị loại
         * =============================================================================
         * ====
         */
        @Test
        public void test06_ST_HR_06_preventEliminateAlreadyRejected() {
                driver.get(BASE_URL + "/org/candidate/detail/2");
                try {
                        // Nút Loại phải bị ẩn hoặc không thể tương tác
                        List<WebElement> rejectBtns = driver
                                        .findElements(By.xpath(
                                                        "//div[@title='Loại ứng viên' or contains(@class, 'reject-btn')]"));
                        if (!rejectBtns.isEmpty()) {
                                Assertions.assertFalse(rejectBtns.get(0).isDisplayed(),
                                                "Nút loại vẫn hiển thị cho ứng viên đã rớt");
                        }
                } catch (Exception e) {
                        Assertions.fail(e.getMessage());
                }
        }

        /*
         * =============================================================================
         * ====
         * TEST CASE 7: ST_HR_07 - Mark Onboard = true
         * =============================================================================
         * ====
         */
        @Test
        public void test07_ST_HR_07_markOnboardTrue() {
                driver.get(BASE_URL + "/org/candidate/detail/3");
                try {
                        wait.until(ExpectedConditions
                                        .elementToBeClickable(By.xpath(
                                                        "//div[@title='Onboard' or contains(@class, 'onboard-btn')]")))
                                        .click();
                        safeClick(By.xpath("//button[normalize-space(.)='Xác nhận' and not(@disabled)]"));
                        wait.until(ExpectedConditions
                                        .invisibilityOfElementLocated(
                                                        By.xpath("//button[normalize-space(.)='Xác nhận']")));

                        // CHECK DB
                        ResultSet rs = dbConnection
                                        .prepareStatement("SELECT candidate_status FROM job_ad_candidate WHERE id=3")
                                        .executeQuery();
                        if (rs.next())
                                Assertions.assertEquals("ONBOARDED", rs.getString("candidate_status"));
                } catch (Exception e) {
                        Assertions.fail(e.getMessage());
                } finally {
                        try {
                                dbConnection
                                                .prepareStatement(
                                                                "UPDATE job_ad_candidate SET candidate_status='WAITING_ONBOARDING' WHERE id=3")
                                                .executeUpdate();
                        } catch (Exception ignored) {
                        }
                }
        }

        /*
         * =============================================================================
         * ====
         * TEST CASE 8: ST_HR_08 - Chặn đặt ngày onboard là quá khứ
         * =============================================================================
         * ====
         */
        @Test
        public void test08_ST_HR_08_preventPastOnboardDate() {
                driver.get(BASE_URL + "/org/candidate/detail/3");
                try {
                        wait.until(ExpectedConditions.elementToBeClickable(
                                        By.xpath("//div[@title='Sửa ngày onboard' or contains(@class, 'edit-onboard-date-btn')]")))
                                        .click();
                        WebElement dateInput = wait
                                        .until(ExpectedConditions
                                                        .visibilityOfElementLocated(By.xpath("//input[@type='date']")));

                        // Thử nhập ngày quá khứ
                        dateInput.sendKeys("2020-01-01");
                        safeClick(By.xpath("//button[normalize-space(.)='Xác nhận' and not(@disabled)]"));

                        WebElement errorMsg = wait.until(
                                        ExpectedConditions.visibilityOfElementLocated(
                                                        By.xpath("//*[contains(text(), 'không hợp lệ')]")));
                        Assertions.assertTrue(errorMsg.isDisplayed(), "Không hiện lỗi chọn ngày quá khứ");
                } catch (Exception e) {
                        Assertions.fail(e.getMessage());
                }
        }

}
