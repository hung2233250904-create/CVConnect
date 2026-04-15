# Kiet Unit Test Evidence - Interview Scheduling & Notification

## 1) Test Script Implemented
- Test class: `src/test/java/com/cvconnect/service/impl/CalendarServiceImplTest.java`
- Framework/tools: JUnit5, Mockito, AssertJ/JUnit Assertions, Maven Surefire, JaCoCo
- Implemented test cases: TC01 -> TC15 (15 test methods)

## 2) Run Command
```powershell
cd BE/core-service
./mvnw.cmd -Dtest=CalendarServiceImplTest verify
```

## 3) Pass Evidence
- Surefire report file:
  - `target/surefire-reports/com.cvconnect.service.impl.CalendarServiceImplTest.txt`
- Expected summary line:
  - `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`

## 4) Coverage Evidence
- JaCoCo report index:
  - `target/site/jacoco/index.html`
- Raw coverage data:
  - `target/site/jacoco/jacoco.csv`
- CalendarServiceImpl coverage (from current run):
  - Instruction: 31.31%
  - Line: 26.62%
  - Branch: 28.26%

## 5) Screenshot Checklist
1. Terminal after `verify` run showing:
   - `Running com.cvconnect.service.impl.CalendarServiceImplTest`
   - `Tests run: 15, Failures: 0, Errors: 0`
   - `BUILD SUCCESS`
2. Surefire txt report showing the summary line.
3. JaCoCo `index.html` page showing overall table.
4. JaCoCo class row for `com.cvconnect.service.impl.CalendarServiceImpl`.

## 6) Notes on Unavailable Scenarios in Current Service API
The current `CalendarService` interface in this project exposes `createCalendar` and view/detail methods only. There is no explicit API yet for:
- conflict detection endpoint/rule API (interviewer/room/candidate overlap)
- reschedule/cancel policy rule API
- reminder scheduler API for non-duplicate sends
- timeline mapping API

Those scenarios should be tested as integration tests once corresponding service methods are implemented.
