# README - CalendarServiceImplTest

## File test
- CalendarServiceImplTest.java

## Test standard (required)
- No mock for DB behavior verification.
- For code paths that read/write DB, test must verify DB state and DB access as required.
- Each test that writes data must rollback to the pre-test state.
- Recommended annotations: `@SpringBootTest`, `@Transactional`, `@Rollback`.

## Cong cu
- JUnit 5
- Spring Boot Test
- Maven Surefire
- JaCoCo

## Scope
Calendar scheduling and notification logic in `CalendarServiceImpl`:
- Date and duration validation
- Calendar type location/link validation
- Participant and candidate validation
- Slot allocation (`joinSameTime` true/false)
- Notification routing/count
- Timezone conversion and time-range formatting

## CheckDB guideline
- Before action: capture baseline records from related tables (`calendar`, `interview_panel`, `calendar_candidate_info`).
- Execute service action.
- Verify inserted/updated rows exactly match expected business result.
- Verify no unexpected row changes outside target records.

## Rollback guideline
- Each DB-changing test runs in a transaction and must rollback after assertion.
- After test completion, DB state must be equal to baseline state before test.

## Run command
```powershell
cd BE/core-service
./mvnw.cmd -Dtest=CalendarServiceImplTest test
```


