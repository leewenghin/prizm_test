package com.prizm.campaign;

import com.prizm.campaign.dto.RedeemResponse;
import com.prizm.campaign.service.VoucherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class VoucherServiceTest {

    @Autowired
    private VoucherService voucherService;

    @Test
    void redeemActiveVoucherSucceeds() {
        RedeemResponse res = voucherService.redeem("RAYA-0001", "user-1");
        assertEquals("OK", res.getResult());
    }

    @Test
    void redeemAlreadyRedeemedVoucherFails() {
        RedeemResponse res = voucherService.redeem("RAYA-0004", "user-2");
        assertEquals("FAILED", res.getResult());
    }

    @Test
    void redeemUnknownCodeFails() {
        RedeemResponse res = voucherService.redeem("NOPE-9999", "user-3");
        assertEquals("FAILED", res.getResult());
    }

    @Test
    void userCannotRedeemMoreThanCampaignLimit() {
        assertEquals("OK", voucherService.redeem("RAYA-0002", "user-7").getResult());
        assertEquals("OK", voucherService.redeem("RAYA-0003", "user-7").getResult());

        RedeemResponse otherCampaign = voucherService.redeem("MRDK-0001", "user-7");
        assertEquals("OK", otherCampaign.getResult());

        RedeemResponse overLimit = voucherService.redeem("RAYA-0006", "user-7");
        assertEquals("FAILED", overLimit.getResult());
        assertEquals("User redemption limit reached", overLimit.getMessage());
    }

    @Test
    void voidHistoryCountsTowardLimit() {
        RedeemResponse res = voucherService.redeem("RAYA-0006", "user-99");
        assertEquals("FAILED", res.getResult());
        assertEquals("User redemption limit reached", res.getMessage());
    }
}
