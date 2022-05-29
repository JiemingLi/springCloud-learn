package com.geekbang.coupon.calculation.template.impl;

import com.geekbang.coupon.calculation.template.AbstractRuleTemplate;
import com.geekbang.coupon.calculation.template.CouponTemplateFactory;
import com.geekbang.coupon.calculation.template.RuleTemplate;
import com.geekbang.coupon.template.api.enums.CouponType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 打折优惠券
 */
@Slf4j
@Component
public class DiscountTemplate extends AbstractRuleTemplate implements RuleTemplate {

    @Override
    protected Long calculateNewPrice(Long totalAmount, Long shopAmount, Long quota) {
        // 计算使用优惠券之后的价格
        Long newPrice = convertToDecimal(shopAmount * (quota.doubleValue()/100));
        log.debug("original price={}, new price={}", totalAmount, newPrice);
        return newPrice;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        CouponTemplateFactory.registerCouponTemplate(this, CouponType.DISCOUNT);

    }
}
