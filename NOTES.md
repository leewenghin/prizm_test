## What you changed and why
1. Add per_user_limit INT NOT NULL DEFAULT 2 to db schema
2. Add default per_user_limit & default value to seed data
3. Add perUserLimit to Campaign.java entity with getter and setter
4. Add query countByCampaignIdAndUserId RedemptionRepository.java
5. Add result & message to auditClient.java
6. Add validation to exceed redeem max, if failed return with `User redemption limit reached`

- Mainly changed for handle max redeem purpose, and audit payload for better understanding.

## Anything you noticed but deliberately **did not** change, and your reasoning

1.  Void does not restore stock in Campaign nor remove the redemption. Which cause a result of a redeemed voucher and still have redemption.

- I would not change the suspect logic as it might be a business decision / nature which allow to void a redeemed voucher. But how to handle the void redeemed voucher afterward are questionable.

2. RedeemCount follows the redemption table, not the voucher status. Redeemed count and status can disagree.

- I would not change this as it might have business logic involves / to cater edge cases, so I will question if the voucher status are ACTIVE / VOID is that still consider as redeemed?

3. Move `AUDIT_URL` and `API_KEY` to the configuration, as `AUDIT_URL` should be easy to configure and `API_KEY` should be managed securely.

4. Failed cases doesn't include audit log which may difficult to trace and investigate.

## What you'd do next if you had another day
1. Clarify with question above and improve it.
2. Write documentation for all the table & endpoint specification.
3. Expand test coverage.

## Which AI suggestion did you reject, and why?
Non relevant improvements to core feature requirement as I'm not sure if it breaks the code or business decision and so will keep the minimal changes.

## What took you longest?
Review AI changes,  need to think if it breaks anything and if the technical decision are acceptable.

