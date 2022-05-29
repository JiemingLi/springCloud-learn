package com.geekbang.coupon.template.convert;

import com.geekbang.coupon.template.api.beans.CouponTemplateInfo;
import com.geekbang.coupon.template.dao.entity.CouponTemplate;

public class CouponTemplateConvert {
    public static CouponTemplateInfo convert(CouponTemplate couponTemplate) {
        return CouponTemplateInfo.builder()
                .id(couponTemplate.getId())
                .name(couponTemplate.getName())
                .desc(couponTemplate.getDescription())
                .type(couponTemplate.getCategory().getCode())
                .shopId(couponTemplate.getShopId())
                .available(couponTemplate.getAvailable())
                .rule(couponTemplate.getRule())
                .build();
    }
}
