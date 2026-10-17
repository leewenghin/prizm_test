package com.prizm.campaign.service;

import com.prizm.campaign.dto.RedeemResponse;
import com.prizm.campaign.model.Campaign;
import com.prizm.campaign.model.Redemption;
import com.prizm.campaign.model.Voucher;
import com.prizm.campaign.repository.CampaignRepository;
import com.prizm.campaign.repository.RedemptionRepository;
import com.prizm.campaign.repository.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private RedemptionRepository redemptionRepository;

    @Autowired
    private AuditClient auditClient;

    public RedeemResponse redeem(String code, String userId) {

        Voucher voucher = voucherRepository.findByCode(code);
        if (voucher == null) {
            return RedeemResponse.fail("Voucher not found");
        }

        if ("REDEEMED".equals(voucher.getStatus())) {
            return RedeemResponse.fail("Voucher already redeemed");
        }

        if ("VOID".equals(voucher.getStatus())) {
            return RedeemResponse.fail("Voucher is void");
        }

        Campaign campaign = campaignRepository.findById(voucher.getCampaignId()).get();

        if (!campaign.isActive()) {
            return RedeemResponse.fail("Campaign is not active");
        }

        if (campaign.getRemainingStock() <= 0) {
            return RedeemResponse.fail("Campaign out of stock");
        }

        long redeemedByUser = redemptionRepository.countByCampaignIdAndUserId(
                campaign.getId(), userId);
        if (redeemedByUser >= campaign.getPerUserLimit()) {
            audit("FAILED", "User redemption limit reached", campaign, voucher, userId);
            return RedeemResponse.fail("User redemption limit reached");
        }

        voucher.setStatus("REDEEMED");
        voucher.setRedeemedBy(userId);
        voucher.setRedeemedAt(new Date());
        voucherRepository.save(voucher);

        campaign.setRemainingStock(campaign.getRemainingStock() - 1);
        campaignRepository.save(campaign);

        Redemption redemption = new Redemption(
                voucher.getId(), campaign.getId(), userId, new Date());
        redemptionRepository.save(redemption);

        audit("OK", "", campaign, voucher, userId);

        return RedeemResponse.ok(voucher.getCode(), campaign.getRemainingStock());
    }

    private void audit(String result, String message, Campaign campaign, Voucher voucher, String userId) {
        try {
            auditClient.recordRedemption(
                    campaign.getClientCode(), voucher.getCode(), userId, result, message);
        } catch (Exception e) {
            log.error("audit call failed");
        }
    }

    public RedeemResponse voidVoucher(String code) {
        Voucher voucher = voucherRepository.findByCode(code);
        if (voucher == null) {
            return RedeemResponse.fail("Voucher not found");
        }
        voucher.setStatus("VOID");
        voucherRepository.save(voucher);
        return RedeemResponse.ok(voucher.getCode(), null);
    }
}
