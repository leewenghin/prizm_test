# Per-user redemption limit

## Goal

Each campaign stores how many vouchers one user may redeem in that campaign. A redeem past that number is rejected with a clear reason. Existing campaigns use `2`.

## Locked decisions

1. Void history counts. Every `redemption` row for that campaign and user counts, including rows whose voucher was later voided. Seed user `user-99` already has two rows on campaign 1 (`RAYA-0004` redeemed, `RAYA-0005` void), so that user is already at the default of 2 on campaign 1.
2. The cap is per user on that campaign. Count `campaign_id` + `user_id`. `client_code` is a property of the campaign, not a separate pool. The same user may redeem up to the limit on each campaign, including two campaigns that share a client.
3. Keep HTTP 200. `VoucherController.redeem` returns `RedeemResponse` with no `ResponseEntity` or `@ResponseStatus`. Spring MVC writes 200. Existing failures (`Voucher not found`, `Voucher already redeemed`, `Voucher is void`, `Campaign is not active`, `Campaign out of stock`) already use `result = "FAILED"` and `message`. The limit rejection uses that same body with message `User redemption limit reached`.
4. No API to read or change `per_user_limit`. Stats endpoints stay unchanged.
5. No transaction and no row lock. Two parallel requests can both read a count under the limit and both insert. That matches `redeem` today, which is not `@Transactional`.
6. A limit rejection is sent to audit. The payload must distinguish rejection from success. Audit errors are logged and swallowed so the caller still receives `FAILED`.

## Schema

This service has no Flyway or Liquibase. Hibernate DDL is off. `schema.sql` and `data.sql` run on startup (`spring.sql.init.mode: always`) and drop the tables first.

Add to `campaign` in `schema.sql`:

```sql
per_user_limit INT NOT NULL DEFAULT 2
```

Set `2` on campaigns 1, 2, and 3 in `data.sql`. No new table. `redemption` already has `campaign_id` and `user_id`.

## Redeem path

`POST /vouchers/{code}/redeem?userId=...` stays the only write API.

In `VoucherService.redeem`, after the existing checks (voucher present, status not `REDEEMED` or `VOID`, campaign present and active, `remainingStock > 0`) and before any save:

1. `countByCampaignIdAndUserId(campaignId, userId) >= campaign.perUserLimit`
2. If over the limit, call audit with `result = FAILED` and message `User redemption limit reached`, then return `RedeemResponse.fail("User redemption limit reached")`.
3. Otherwise keep the current sequence: save voucher as `REDEEMED`, decrement `remainingStock`, insert `redemption`, then audit with `result = OK`.

`voidVoucher` does not change.

`AuditClient.recordRedemption` today posts `clientCode`, `voucherCode`, `userId`, and `apiKey`. Add `result` and `message` so a rejected redeem is not stored as a success. Success and the limit rejection both use this method. Other failures (unknown code, already redeemed, void, inactive, out of stock) are not audited.

## Files to change

| File | Change |
|---|---|
| `src/main/resources/schema.sql` | `per_user_limit INT NOT NULL DEFAULT 2` on `campaign`. |
| `src/main/resources/data.sql` | Set `2` on campaigns 1, 2, and 3. |
| `model/Campaign.java` | Map `perUserLimit` with getter and setter. |
| `repository/RedemptionRepository.java` | `long countByCampaignIdAndUserId(Long campaignId, String userId)`. |
| `service/AuditClient.java` | Include `result` and `message` in the posted event. |
| `service/VoucherService.java` | Limit check before writes. Audit that rejection and the existing success. |
| `src/test/java/com/prizm/campaign/VoucherServiceTest.java` | A new user can redeem twice on campaign 1; the third is `FAILED` with `User redemption limit reached`. Do not reuse `RAYA-0001` / `user-1` (already consumed by `redeemActiveVoucherSucceeds` on the shared Spring context). `user-99` redeeming another campaign 1 code is already at the limit. |

Not changed: both controllers, `RedeemResponse`, `CampaignStats`, `CampaignStatsService`, `Voucher`, `Redemption`, `VoucherRepository`, `CampaignRepository`, `build.gradle`.

## Tests

- Redeem two active campaign 1 codes for a new user (`RAYA-0002`, `RAYA-0003`), then redeem `RAYA-0006` for the same user and expect `FAILED` and `User redemption limit reached`.
- Redeem an active campaign 2 code for that same user and expect `OK`, showing the count does not cross campaigns.
- `user-99` redeeming an active campaign 1 code expects `FAILED` because both seed rows count.

`redeemActiveVoucherSucceeds` mutates the shared in-memory database. New cases must use other codes or users.
