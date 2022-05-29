package com.geekbang.coupon.calculation.template;

import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.template.api.beans.CouponTemplateInfo;
import com.geekbang.coupon.template.api.enums.CouponType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CouponTemplateFactory {

    private static Map<String, RuleTemplate> category2CouponTemplate = new ConcurrentHashMap<>();

    public static void registerCouponTemplate(RuleTemplate template, CouponType couponType) {
        category2CouponTemplate.put(couponType.getCode(), template);
    }

    public static RuleTemplate getTemplate(ShoppingCart order) {
        CouponTemplateInfo template = order.getCouponInfos().get(0).getTemplate();
        return category2CouponTemplate.get(template.getType());
    }
}
