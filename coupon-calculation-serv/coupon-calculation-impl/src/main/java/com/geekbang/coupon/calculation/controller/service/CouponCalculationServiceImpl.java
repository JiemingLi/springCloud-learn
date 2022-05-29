package com.geekbang.coupon.calculation.controller.service;

import com.alibaba.fastjson.JSON;
import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.calculation.api.beans.SimulationOrder;
import com.geekbang.coupon.calculation.api.beans.SimulationResponse;
import com.geekbang.coupon.calculation.controller.service.intf.CouponCalculationService;
import com.geekbang.coupon.calculation.template.CouponTemplateFactory;
import com.geekbang.coupon.calculation.template.RuleTemplate;
import com.geekbang.coupon.template.api.beans.CouponInfo;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CouponCalculationServiceImpl implements CouponCalculationService {


    @Override
    public ShoppingCart calculateOrderPrice(ShoppingCart cart) {
        log.info("calculate order price: {}", JSON.toJSONString(cart));
        RuleTemplate ruleTemplate = CouponTemplateFactory.getTemplate(cart);
        return ruleTemplate.calculate(cart);
    }

    @Override
    public SimulationResponse simulateOrder(SimulationOrder order) {
        SimulationResponse response = new SimulationResponse();
        Long minOrderPrice = Long.MAX_VALUE;

        for (CouponInfo coupon: order.getCouponInfos()) {
            ShoppingCart cart = new ShoppingCart();
            cart.setProducts(order.getProducts());
            cart.setCouponInfos(Lists.newArrayList(coupon));

            cart = CouponTemplateFactory.getTemplate(cart).calculate(cart);


            Long couponId = coupon.getId();
            Long orderPrice = cart.getCost();

            // 设置当前优惠券对应的订单价格
            response.getCouponToOrderPrice().put(couponId, orderPrice);

            // 比较订单价格，设置当前最优优惠券的ID
            if (minOrderPrice > orderPrice) {
                response.setBestCouponId(coupon.getId());
                minOrderPrice = orderPrice;
            }
        }
        return response;
    }
}
