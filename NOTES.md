1.  Void does not restore stock in Campaign nor remove the redemption. Which cause a result of a redeemed voucher and still have redemption.

- I would not change the suspect logic as it might be a business decision / nature which allow to void a redeemed voucher. But how to handle the void redeemed voucher afterward are questionable.

2. RedeemCount follows the redemption table, not the voucher status. Redeemed count and status can disagree.

- I would not change this as it might have business logic involves / to cater edge cases, so I will question if the voucher status are ACTIVE / VOID is that still consider as redeemed?

3. Move `AUDIT_URL` and `API_KEY` to the configuration, as `AUDIT_URL` should be easy to configure and `API_KEY` should be managed securely.
