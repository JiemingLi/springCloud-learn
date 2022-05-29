package com.geekbang.coupon.calculation.template;

import com.geekbang.coupon.calculation.api.beans.Product;
import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.template.api.beans.CouponTemplateInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractRuleTemplate implements RuleTemplate, InitializingBean {
    @Override
    public ShoppingCart calculate(ShoppingCart order) {
        //获取订单总价格
        Long orderTotalAmount = getTotalPrice(order.getProducts());
        //以shopId为维度的订单价格
        Map<Long, Long> sumAmount = getTotalPriceGroupByShop(order.getProducts());
//        if (order.getCouponId() == null || order.getCouponInfos() == null) {
//            log.warn("没有使用优惠券可以使用");
//            order.setCost(orderTotalAmount);
//            return order;
//        }
        //获取优惠券的规则，目前只支持单张优惠券
        CouponTemplateInfo templateInfo = order.getCouponInfos().get(0).getTemplate();
        //获取最低消费
        Long threshold = templateInfo.getRule().getDiscount().getThreshold();
        //获取打折优惠和减扣的金额
        Long quote = templateInfo.getRule().getDiscount().getQuota();
        // 当前优惠券适用的门店ID，如果为空则作用于全店券
        Long shopId = templateInfo.getShopId();
        // 如果优惠券未指定shopId，shopTotalAmount=orderTotalAmount
        // 如果指定了shopId，则shopTotalAmount=对应门店下商品总价
        Long shopTotalAmount = shopId == null ? orderTotalAmount : sumAmount.get(shopId);
        // 如果不符合优惠券使用标准, 则直接按原价走，不使用优惠券
        if (shopTotalAmount == null || shopTotalAmount < threshold) {
            log.warn("Totals of amount not meet, ur coupons are not applicable to this order");
            order.setCost(orderTotalAmount);
            order.setCouponInfos(Collections.emptyList());
            return order;
        }
        Long newCost = calculateNewPrice(orderTotalAmount, shopTotalAmount, quote);
        if (newCost < minCost()) {
            newCost = minCost();
        }
        order.setCost(newCost);
        log.debug("original price={}, new price={}", orderTotalAmount, newCost);
        return order;
    }

    // 金额计算具体逻辑，延迟到子类实现
    abstract protected Long calculateNewPrice(Long orderTotalAmount, Long shopTotalAmount, Long quota);

    // 计算订单总价
    protected Long getTotalPrice(List<Product> productList) {
        return productList.stream()
                .mapToLong(product -> product.getPrice() * product.getCount())
                .sum();
    }

    // 根据门店维度计算每个门店下商品价格
    // key = shopId
    // value = 门店商品总价
    protected Map<Long,Long> getTotalPriceGroupByShop(List<Product> productList) {
        return productList.stream()
                .collect(Collectors.groupingBy(product -> product.getShopId(),
                        Collectors.summingLong(p -> p.getCount() * p.getPrice())));
    }

    // 每个订单最少必须支付1分钱
    protected long minCost() {
        return 1L;
    }

    protected long convertToDecimal(Double value) {
        return new BigDecimal(value).setScale(0, RoundingMode.HALF_UP).longValue();
    }


}
