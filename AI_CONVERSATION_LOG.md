# Codebase understanding overview

## User

Understand an existing codebase before implementing a new feature.

## Task
Inspect the project and explain how it currently works. Do not modify any files yet.

## Investigate
1. Project structure, frameworks, dependencies, and application entry points.
2. Architecture and responsibilities of the main layers (controller, service, repository, entity/model).
3. Database schema, entity relationships, and migration strategy.
4. The existing campaign, voucher, and redemption flows.

## Output
- A concise architecture overview.
- The end-to-end voucher redemption flow, including relevant files and methods.
- The database tables/entities involved and their relationships.
- The most relevant existing tests.
- Potential risks or design constraints for changing redemption limits.
- Any uncertainties that require further investigation.

Use evidence from the actual source code. Distinguish confirmed findings from assumptions. Do not implement the feature or make unrelated changes

## Assistant

I'll inspect the codebase as it exists and map the architecture, data model, and redemption flow without changing any files.

### Tool Glob File Search

```
{"targetDirectory":"c:\\dev\\voucher-service-takehome\\voucher-service","globPattern":"**/*.{java,kt,xml,yml,yaml,gradle,md,sql,properties}"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\README.md"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\build.gradle"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\resources\\schema.sql"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\resources\\data.sql"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\resources\\application.yml"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\service\\VoucherService.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\controller\\VoucherController.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\controller\\CampaignController.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\test\\java\\com\\prizm\\campaign\\VoucherServiceTest.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\model\\Campaign.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\model\\Voucher.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\model\\Redemption.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\repository\\CampaignRepository.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\repository\\VoucherRepository.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\repository\\RedemptionRepository.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\service\\CampaignStatsService.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\service\\AuditClient.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\dto\\RedeemResponse.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\dto\\CampaignStats.java"}
```

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\src\\main\\java\\com\\prizm\\campaign\\CampaignApplication.java"}
```

## Assistant

This is a small Spring Boot 3.2.5 / Java 17 service that redeems pre-seeded voucher codes against a campaign stock counter. There is no per-user redemption limit in the current code.

## Architecture

Package `com.prizm.campaign`. Entry point: `CampaignApplication` (`@SpringBootApplication`). HTTP is Spring MVC; persistence is Spring Data JPA against an in-memory H2 database (`jdbc:h2:mem:campaigndb`). Hibernate DDL is off (`ddl-auto: none`). Schema and seed data come from `src/main/resources/schema.sql` and `data.sql` with `spring.sql.init.mode: always`. There is no Flyway or Liquibase.

| Layer | Role |
|---|---|
| `VoucherController`, `CampaignController` | HTTP only. Redeem and void return `RedeemResponse`; stats return `CampaignStats`. No validation annotations are used, even though `spring-boot-starter-validation` is on the classpath. |
| `VoucherService` | Redeem and void. Field-injected repositories plus `AuditClient`. |
| `CampaignStatsService` | Read-only stock and counts. Constructor-injected. |
| `AuditClient` | After a successful redeem, POSTs to `http://internal-audit.prizm.local/api/v2/events` with a hardcoded API key. Failures are logged and swallowed. |
| Repositories | `JpaRepository` plus `findByCode`, `findByCampaignId`, `findByClientCode`, `findByVoucherId`. |
| Entities | `Campaign`, `Voucher`, `Redemption`. IDs are scalar foreign keys, not JPA associations. |

Confirmed endpoints (README and controllers match):

- `POST /vouchers/{code}/redeem?userId=...`
- `POST /vouchers/{code}/void`
- `GET /campaigns/{id}/stats`
- `GET /campaigns/by-client/{clientCode}/stats`

## Redemption flow

`VoucherController.redeem` calls `VoucherService.redeem(code, userId)`. Confirmed order:

1. `VoucherRepository.findByCode`. Missing code returns `FAILED` / `"Voucher not found"`.
2. Status `"REDEEMED"` returns `"Voucher already redeemed"`. Status `"VOID"` returns `"Voucher is void"`. Any other status, including `"ACTIVE"`, continues.
3. `CampaignRepository.findById(voucher.campaignId).get()`. A missing campaign throws `NoSuchElementException` (no `FAILED` response).
4. Inactive campaign returns `"Campaign is not active"`. `remainingStock <= 0` returns `"Campaign out of stock"`.
5. Voucher is set to `REDEEMED`, `redeemedBy`, and `redeemedAt`, then saved.
6. Campaign `remainingStock` is decremented by 1 and saved.
7. A `Redemption` row is inserted (`voucherId`, `campaignId`, `userId`, `createdAt`).
8. `AuditClient.recordRedemption` runs. On exception, the method still returns success.
9. Response is `OK` with the code and the new `remainingStock`.

There is no `@Transactional` on `redeem`. The three writes are separate saves. There is no row lock, unique constraint, or per-user check.

`voidVoucher` only sets status to `VOID` and saves. It does not check status, restore stock, or write or delete a redemption.

## Data model

`schema.sql` creates three tables and drops them first. No foreign keys, unique indexes, or check constraints.

```
campaign (id PK)
  name, client_code, total_stock, remaining_stock, active
    1
    ├── voucher (id PK, campaign_id, code, status, redeemed_by, redeemed_at)
    └── redemption (id identity PK, voucher_id, campaign_id, user_id, created_at)
```

Relationships are by convention only: `voucher.campaign_id` and `redemption.campaign_id` point at `campaign.id`; `redemption.voucher_id` points at `voucher.id`. `code` is not unique in the schema. Voucher and campaign IDs are assigned in SQL, not generated. Only `redemption.id` is `IDENTITY`.

Seed data (`data.sql`):

- Campaign 1 `ACME` "Raya Flash Deal": `total_stock` 100, `remaining_stock` 100, active. Vouchers `RAYA-0001`–`0003` and `0006` are `ACTIVE`; `RAYA-0004` is `REDEEMED` by `user-99`; `RAYA-0005` is `VOID` but still has `redeemed_by` and a redemption row.
- Campaign 2 `NOVA`: stock 50/50, `MRDK-0001` and `MRDK-0002` `ACTIVE`.
- Campaign 3 `ACME`: inactive, stock 10/0, voucher `EXPD-0001` still `ACTIVE`.

`remaining_stock` is not derived from voucher rows. Campaign 1 stays at 100 after a seeded redemption. Stats count a voucher as redeemed if any `redemption` row exists, so the void voucher `RAYA-0005` is included in `redeemedCount`. `activeVoucherCount` uses status `ACTIVE`.

## Tests

Only `src/test/java/com/prizm/campaign/VoucherServiceTest.java` (`@SpringBootTest`, real H2, seed data):

- `redeemActiveVoucherSucceeds` — `RAYA-0001` / `user-1` expects `OK`. This mutates the shared database.
- `redeemAlreadyRedeemedVoucherFails` — `RAYA-0004` expects `FAILED`. It does not assert the message.
- `redeemUnknownCodeFails` — `NOPE-9999` expects `FAILED`.

Nothing covers void, inactive campaign, stock exhaustion, double redeem, concurrency, stats, or the audit call.

## Constraints if redemption limits change

Confirmed from the code:

- Limits today are one successful status transition per voucher code plus a campaign-wide `remaining_stock` counter. Nothing counts redemptions per `userId`.
- `RedemptionRepository` can list by voucher or campaign only. A per-user limit needs a new query and, if it must hold under concurrency, a unique constraint or lock. The table allows duplicate `(campaign_id, user_id)` rows.
- Counting `redemption` rows treats the void voucher `RAYA-0005` as a redemption. Status `REDEEMED` and the redemption table disagree.
- `remaining_stock` can drift from both voucher status and redemption rows. Seed data already does this. A new limit that trusts that counter will disagree with history.
- Without a transaction or lock, two concurrent redeems of the same code or the last unit of stock can both pass the checks and both write.
- Audit runs after the writes and must not be required for the limit decision. It also embeds a live API key in source.
- `findByCode` returns a single `Voucher`. A non-unique `code` makes that lookup undefined.
- Existing tests share one Spring context and one H2 database. A new limit that rejects a second redeem by `user-99` on campaign 1 will see the seed redemption for the void voucher as well as `RAYA-0004`.

## Uncertainties

- No product rule says whether a limit is per campaign, per client, or per voucher, or whether void redemptions count. That is not in this repo.
- No test or config shows whether `AuditClient` is expected to be up in this environment. The URL is internal and the call is best-effort.
- `findByCode` behavior if two rows share a code is unspecified; the schema does not prevent it.
- Controller exception handling for a missing campaign is untested. Spring Boot’s default error JSON would apply.

## User

based on your inspection of the existing codebase, analyse the following feature.

## Requirement
Add a per-user redemption limit.

Each campaign should define the maximum number of vouchers a single user may redeem in that campaign. When a user tries to redeem beyond their limit, the request should be rejected with a clear reason.

Use `2` as the default limit for existing campaigns.

## Analyse
1. Identify the exact entities, services, controllers, repositories, dtos and db affected.
2. Treat it as production code base, propose the smallest appropriate schema and API changes consistent with the existing. Keep the changes as minimal as possible.
3. Explain how existing campaign will receive the default limit of 2 
4. Identify assumptions and questions must be resolved before implementations

## Output 
Provide a proposed implementation plan `PLAN.md`, the files expected to change, and the reasoning behind each change.

do not modify any files yet.

## Assistant

The per-user limit fits the current redeem path as one campaign column and one count check before any write. The HTTP contract can stay as it is: `POST /vouchers/{code}/redeem` already returns `RedeemResponse.fail` with a message.

## PLAN.md

### Goal

Each campaign stores how many vouchers one user may redeem in that campaign. Redeem rejects a user who is already at that number. Existing campaigns use `2`.

### Schema

`schema.sql` is the only schema mechanism (`ddl-auto: none`, `spring.sql.init.mode: always`, tables dropped and recreated on startup). Add a non-null column on `campaign`:

```sql
per_user_limit INT NOT NULL DEFAULT 2
```

Set `2` on all three `INSERT` rows in `data.sql` so the seed does not depend on the default being noticed later. No new table. `redemption` already stores `campaign_id` and `user_id`, which is enough to count.

There is no migration tool. A persistent database outside this H2 init would need `ALTER TABLE campaign ADD COLUMN per_user_limit INT NOT NULL DEFAULT 2`. Adding Flyway for that would be a larger change than this codebase uses.

### API

No new route and no DTO field. `VoucherController.redeem` already passes through `RedeemResponse`. Over the limit returns `result = "FAILED"` and a fixed message, for example `User redemption limit reached`. Same shape as `"Voucher already redeemed"` and `"Campaign out of stock"`.

Stats endpoints stay unchanged. The requirement does not ask clients to read the limit.

### Redeem check

In `VoucherService.redeem`, after the voucher exists, is not `REDEEMED` or `VOID`, the campaign exists and is active, and `remainingStock > 0`, and before any save:

1. Read `campaign.perUserLimit`.
2. `redemptionRepository.countByCampaignIdAndUserId(campaignId, userId)`.
3. If `count >= limit`, return `RedeemResponse.fail(...)`.
4. Otherwise keep the current voucher, stock, redemption, and audit sequence.

`voidVoucher` does not change.

### How existing campaigns get 2

On every boot, `schema.sql` recreates `campaign` with `DEFAULT 2`, and `data.sql` inserts campaigns 1–3 with `per_user_limit = 2`. That is the whole data set this service has. `Campaign.perUserLimit` maps the column; no startup backfill job.

### Files expected to change

| File | Change |
|---|---|
| `src/main/resources/schema.sql` | `per_user_limit INT NOT NULL DEFAULT 2` on `campaign`. |
| `src/main/resources/data.sql` | Set `2` on campaigns 1, 2, and 3. |
| `model/Campaign.java` | Field, column mapping, getter, setter. |
| `repository/RedemptionRepository.java` | `long countByCampaignIdAndUserId(Long campaignId, String userId)`. |
| `service/VoucherService.java` | Compare that count to `perUserLimit` before writes. |
| `src/test/java/.../VoucherServiceTest.java` | One success under the limit and one rejection at the limit, asserting `FAILED` and the message. |
| `README.md` | One line that campaigns limit redemptions per user and the seed limit is 2. |

Not changed: both controllers, `RedeemResponse`, `CampaignStats`, `CampaignStatsService`, `AuditClient`, `Voucher`, `Redemption`, `VoucherRepository`, `CampaignRepository`, `build.gradle`.

### Tests that matter

Seed already gives `user-99` two `redemption` rows on campaign 1 (`RAYA-0004` redeemed, `RAYA-0005` void). Whether that user is already at the limit depends on the open question below. A safe new case that does not depend on that row: redeem two active codes for a new user on campaign 1 (`RAYA-0001`, `RAYA-0002`), then redeem `RAYA-0003` for the same user and expect `FAILED`. Campaign 2 can show that the count does not cross campaigns.

`redeemActiveVoucherSucceeds` already redeems `RAYA-0001` for `user-1` against the shared Spring context. New tests must use other codes or users so they do not collide with that mutation.

### Assumptions to confirm before coding

1. **Void rows count toward the limit.** `user-99` has a redemption for void voucher `RAYA-0005`. Counting every `redemption` row means that user is already at 2 on campaign 1. Counting only vouchers with status `REDEEMED` means they have 1. The smallest query is a row count. Confirm that void history counts.
2. **The limit is per campaign, not per client.** `ACME` has campaigns 1 and 3. A user could redeem 2 on each. `client_code` is not part of the check.
3. **HTTP stays 200 with `FAILED`.** That matches every current rejection. A 409 would be a new contract.
4. **No admin API to change the limit.** It is data on the campaign row. Tests and seed set it; nothing in the service updates it.
5. **Concurrency matches the rest of redeem.** Two requests can both read a count under the limit and both insert. A transaction or a lock on the campaign row would close that, and it is more than the current method does (`redeem` is not `@Transactional`). Ship the check first unless a hard cap under parallel requests is required.
6. **Audit stays after a successful write** and is still best-effort. A rejected redeem does not call audit.

## User

1. Remain it as void history counts
2. Need to validate if a user/ client code redeem more than limit in that cmapaign
3. Can elaborate further which cause this
4. No need for now
5. Ship the check first unless a hard cap under parallel requests is required
6. Do include the rejected redeem to audit record

## Assistant

The limit check stays a count of `redemption` rows for one user on one campaign, including void history. The HTTP response stays the existing `FAILED` body. A limit rejection is also sent to audit.

## Locked decisions

1. Void history counts. `user-99` already has two `redemption` rows on campaign 1 (`RAYA-0004` and void `RAYA-0005`), so that user is already at the default of 2 for campaign 1.
2. The cap is per user on that campaign. `client_code` lives on the campaign, so the count is `campaign_id` + `user_id`. The same user can still redeem up to the limit on each campaign, including two ACME campaigns. This is not a single pool across every campaign for one client.
3. Keep HTTP 200. Detail below.
4. No API to change `per_user_limit`.
5. No transaction or row lock in this change. Two parallel requests can both read a count under the limit and both insert.
6. A limit rejection still calls audit, and the payload must say it was rejected.

## Why rejection is HTTP 200

`VoucherController.redeem` returns `RedeemResponse` directly. There is no `ResponseEntity`, `@ResponseStatus`, or `@ControllerAdvice`. Spring MVC treats a returned object as a successful response and writes **200** with JSON like:

```json
{"result":"FAILED","message":"Voucher already redeemed","voucherCode":null,"remainingStock":null}
```

Every current failure uses that path: unknown code, already redeemed, void, inactive campaign, and out of stock. Callers are expected to read `result`, not the status code. A 409 only for the new limit would make this one failure different from the others. The limit rejection uses the same body, with message `User redemption limit reached`.

## Updated redeem path

After the existing checks (voucher present, not `REDEEMED` or `VOID`, campaign present and active, `remainingStock > 0`) and before any save:

- `countByCampaignIdAndUserId(campaignId, userId) >= campaign.perUserLimit`
- If so, call audit with the campaign client code, voucher code, user id, and a rejected outcome, then return `RedeemResponse.fail("User redemption limit reached")`.
- Audit errors are still caught and logged. The user still gets `FAILED`, not a 500.
- If under the limit, the current writes run, then the existing success audit.

`AuditClient.recordRedemption` today only sends `clientCode`, `voucherCode`, `userId`, and `apiKey`. A success and a rejection would look the same. The payload gains a `result` field (`OK` or `FAILED`) and, on failure, the same message returned to the client. Success and the limit rejection both go through that method.

## Files

| File | Change |
|---|---|
| `src/main/resources/schema.sql` | `per_user_limit INT NOT NULL DEFAULT 2` on `campaign`. |
| `src/main/resources/data.sql` | `2` on campaigns 1, 2, and 3. |
| `model/Campaign.java` | Map `perUserLimit`. |
| `repository/RedemptionRepository.java` | `countByCampaignIdAndUserId`. |
| `service/AuditClient.java` | Include `result` and `message` in the posted event. |
| `service/VoucherService.java` | Limit check before writes; audit on that rejection and on success. |
| `VoucherServiceTest.java` | Under the limit succeeds; the next redeem for that user on that campaign is `FAILED` with that message. `user-99` on campaign 1 is already at 2 because both seed rows count. |
| `README.md` | Seed campaigns allow 2 redemptions per user, and void history counts. |

Controllers, `RedeemResponse`, and stats stay as they are. No files have been changed yet.

## User

Create a `PLAN.MD` and write down the plan and wait for instruction

## Assistant

I'll write the agreed plan into `PLAN.MD` and stop there.

### Tool Edit File V2

## Assistant

`PLAN.MD` is in the project root. It records the per-user limit, the default of 2, void history counting toward the cap, HTTP 200 with `User redemption limit reached`, no locking, and an audit event on both success and limit rejection.

No application code has been changed. Waiting for your instruction.

## User

PLAN.MD execute the decision

## Assistant

I'll implement the plan in `PLAN.MD`: campaign limit of 2, the redeem check, audit on rejection, and tests.

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\PLAN.MD","limit":40}
```

### Tool Edit File V2

### Tool Edit File V2

### Tool Edit File V2

### Tool Edit File V2

### Tool Edit File V2

### Tool Edit File V2

### Tool Edit File V2

### Tool Edit File V2

### Tool Edit File V2

### Tool Edit File V2

### Tool Glob File Search

```
{"targetDirectory":"c:\\dev\\voucher-service-takehome\\voucher-service","globPattern":"gradlew*"}
```

### Tool Run Terminal Command V2

## Assistant

The per-user redemption limit from `PLAN.MD` is in place. `./gradlew test` passed.

Each campaign now has `per_user_limit`, set to `2` for the three seeded campaigns. `VoucherService.redeem` counts existing `redemption` rows for that campaign and user, including void history, and stops before any write when the count is already at the limit. The response is still HTTP 200 with `result = "FAILED"` and message `User redemption limit reached`.

That rejection is sent to audit with `result = FAILED` and the same message. A successful redeem is audited with `result = OK`. Audit failures are still logged and ignored. Other failures, such as an unknown code or an inactive campaign, are not audited.

## User

Suggest commit message in tranches. Make sure each commit are relevant and wait for instruction

convention with prefix with feat, doc, etc... 

feat: .....
- implement...

## Assistant

I'll group the current changes into separate commits and suggest a message for each. I won't commit anything.

### Tool Run Terminal Command V2

## Assistant

Suggested commits, in this order. `bin/` is build output and should stay unstaged.

**1. `PLAN.MD`**
```
doc: record the per-user redemption limit plan
- capture the limit of 2, void history, HTTP 200, and audit-on-rejection decisions
```

**2. `schema.sql`, `data.sql`, `Campaign.java`**
```
feat: store a per-user redemption limit on each campaign
- add campaign.per_user_limit, defaulting to 2
- set the limit to 2 on the seeded campaigns
```

**3. `AuditClient.java`**
```
feat: include the redeem outcome in the audit event
- send result and message so a rejected redeem is not recorded as a success
```

**4. `RedemptionRepository.java`, `VoucherService.java`, `VoucherServiceTest.java`**
```
feat: reject redeems that exceed the campaign per-user limit
- count redemption rows for the campaign and user, including void history
- return User redemption limit reached before any write
- audit that rejection and the successful redeem
- cover a second campaign staying under its own limit and user-99 already at 2
```

**5. `README.md`**
```
doc: document the per-user redemption limit
- note that each campaign allows 2 redemptions per user and void history counts
```

Waiting for your instruction before committing.

## User

commit the changes and 5. README.md dont need to push

## Assistant

I'll commit the plan, schema, audit, and limit changes as separate commits, leave `README.md` unstaged, and not push.

### Tool Run Terminal Command V2

### Tool Run Terminal Command V2

### Tool Read File V2

```
{"path":"c:\\dev\\voucher-service-takehome\\voucher-service\\README.md","limit":12}
```

### Tool Run Terminal Command V2

## Assistant

The four commits are already on `main`, four commits ahead of `origin/main`. Nothing was pushed. `README.md` is not in any of them, and `bin/` is still unstaged.

1. `ca9bfbe` — `doc: record the per-user redemption limit plan`
2. `72081c8` — `feat: store a per-user redemption limit on each campaign`
3. `95f8f88` — `feat: include the redeem outcome in the audit event`
4. `ddcdaff` — `feat: reject redeems that exceed the campaign per-user limit`

## User

there is still 2 remain staged changes

## Assistant

I'll check what's still staged and what those two changes are.

### Tool Run Terminal Command V2

### Tool Run Terminal Command V2

### Tool Run Terminal Command V2

### Tool Run Terminal Command V2

## Assistant

The two staged `bin/` copies are committed as `ea8452c`.

```
chore: sync compiled schema and seed data
- update the tracked bin copies with campaign.per_user_limit set to 2
```

`bin/main/schema.sql` and `bin/main/data.sql` now include `per_user_limit` set to 2. The working tree is clean, and `README.md` was not included. The branch is 5 commits ahead of `origin/main` and has not been pushed.