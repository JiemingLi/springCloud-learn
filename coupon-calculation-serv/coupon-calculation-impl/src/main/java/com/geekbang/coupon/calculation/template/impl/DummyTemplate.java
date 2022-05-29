package com.geekbang.coupon.calculation.template.impl;

import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.calculation.template.AbstractRuleTemplate;
import com.geekbang.coupon.calculation.template.CouponTemplateFactory;
import com.geekbang.coupon.calculation.template.RuleTemplate;
import com.geekbang.coupon.template.api.enums.CouponType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 空实现
 */
@Slf4j
@Component
public class DummyTemplate extends AbstractRuleTemplate implements RuleTemplate {

    @Override
    public ShoppingCart calculate(ShoppingCart order) {
        // 获取订单总价
        Long orderTotalAmount = getTotalPrice(order.getProducts());

        order.setCost(orderTotalAmount);
        return order;
    }


    @Override
    protected Long calculateNewPrice(Long orderTotalAmount, Long shopTotalAmount, Long quota) {
        return orderTotalAmount;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        CouponTemplateFactory.registerCouponTemplate(this, CouponType.UNKNOWN);
    }

}
