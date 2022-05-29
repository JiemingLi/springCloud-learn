package com.geekbang.coupon.customer.service;

import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.calculation.api.beans.SimulationOrder;
import com.geekbang.coupon.calculation.api.beans.SimulationResponse;
import com.geekbang.coupon.customer.api.beans.RequestCoupon;
import com.geekbang.coupon.customer.api.beans.SearchCoupon;
import com.geekbang.coupon.customer.api.enums.CouponStatus;
import com.geekbang.coupon.customer.dao.CouponDao;
import com.geekbang.coupon.customer.dao.entity.Coupon;
import com.geekbang.coupon.customer.service.intf.CouponCustomerService;
import com.geekbang.coupon.template.api.beans.CouponInfo;
import com.geekbang.coupon.template.api.beans.CouponTemplateInfo;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

import static com.geekbang.coupon.customer.constant.Constant.TRAFFIC_VERSION;

@Slf4j
@Service
public class CouponCustomerServiceImpl  implements CouponCustomerService {
    @Autowired
    private CouponDao couponDao;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Override
    public Coupon requestCoupon(RequestCoupon request) {

        //获取模板信息
        CouponTemplateInfo templateInfo = webClientBuilder.build()
                .get()
                .uri("http://coupon-template-serv/template/getTemplate?id=" + request.getCouponTemplateId())
                .header(TRAFFIC_VERSION, request.getTrafficVersion())
                .retrieve()
                .bodyToMono(CouponTemplateInfo.class)
                .block();
        if (templateInfo == null) {
            log.error("invalid template id={}", request.getCouponTemplateId());
            throw new IllegalArgumentException("Invalid template id");
        }

        // 不能超时并且优惠券可用
        long now = Calendar.getInstance().getTimeInMillis();
        Long expireTime = templateInfo.getRule().getDeadline();
        if (expireTime != null && now > expireTime || BooleanUtils.isFalse(templateInfo.getAvailable())) {
            log.error("template is not available id={}", request.getCouponTemplateId());
            throw new IllegalArgumentException("template is unavailable");
        }

        //test-branch

        //领取数目有限
        Long count = couponDao.countByUserIdAndTemplateId(request.getUserId(), request.getCouponTemplateId());
        if (count > templateInfo.getRule().getLimitation()) {
            log.error("exceeds maximum number");
            throw new IllegalArgumentException("exceeds maximum number");
        }

        Coupon coupon = Coupon.builder()
                .templateId(templateInfo.getId())
                .shopId(templateInfo.getShopId())
                .userId(request.getUserId())
                .status(CouponStatus.AVAILABLE)
                .build();
        couponDao.save(coupon);
        return coupon;
    }

    private Coupon checkAvailableCoupon(Long userId, Long couponId) {
        Coupon example = Coupon.builder()
                .userId(userId)
                .id(couponId)
                .status(CouponStatus.AVAILABLE)
                .build();
        Coupon coupon = couponDao.findAll(Example.of(example))
                .stream()
                .findFirst()
                // 如果找不到券，就抛出异常
                .orElseThrow(() -> new RuntimeException("Coupon not found"));

        return coupon;
    }

    @Override
    public ShoppingCart placeOrder(ShoppingCart order) {
        if (CollectionUtils.isEmpty(order.getProducts())) {
            log.error("invalid check out request, order={}", order);
            throw new IllegalArgumentException("cart if empty");
        }

        // 如果有优惠券，验证是否可用，并且是当前客户的
        Coupon coupon = null;
        if (order.getCouponId() != null) {
            coupon = checkAvailableCoupon(order.getUserId(), order.getCouponId());
            CouponInfo couponInfo = CouponConverter.convertToCoupon(coupon);
            couponInfo.setTemplate(loadTemplateInfo(couponInfo.getTemplateId()));
            order.setCouponInfos(Lists.newArrayList(couponInfo));
        }
        // 购物车清算
        ShoppingCart checkoutInfo = webClientBuilder.build()
                .post()
                .uri("http://coupon-calculation-serv/calculator/checkout")
                .bodyValue(order)
                .retrieve()
                .bodyToMono(ShoppingCart.class)
                .block();
//        ShoppingCart checkoutInfo = calculationService.calculateOrderPrice(order);
        if (coupon != null) {
            if (CollectionUtils.isEmpty(checkoutInfo.getCouponInfos())) {
                log.error("cannot apply coupon to order, couponId={}", coupon.getId());
                throw new IllegalArgumentException("coupon is not applicable to this order");
            }
            log.info("update coupon status to used, couponId={}", coupon.getId());
            coupon.setStatus(CouponStatus.USED);
            couponDao.save(coupon);
        }
        return order;
    }

    @Override
    public SimulationResponse simulateOrderPrice(SimulationOrder order) {
        List<CouponInfo> couponInfos = Lists.newArrayList();
        // 挨个循环，把优惠券信息加载出来
        // 高并发场景下不能这么一个个循环，更好的做法是批量查询
        // 而且券模板一旦创建不会改内容，所以在创建端做数据异构放到缓存里，使用端从缓存捞template信息
        // todo 这块如果看不懂，先看懂计算服务的具体的流程
        for (Long couponId : order.getCouponIDs()) {
            Coupon example = Coupon.builder()
                    .userId(order.getUserId())
                    .id(couponId)
                    .status(CouponStatus.AVAILABLE)
                    .build();
            Optional<Coupon> couponOptional = couponDao.findAll(Example.of(example)).stream().findFirst();
            if (couponOptional.isPresent()) {
                Coupon coupon = couponOptional.get();
                CouponInfo couponInfo = CouponConverter.convertToCoupon(coupon);
                couponInfo.setTemplate(loadTemplateInfo(couponId));
                couponInfos.add(couponInfo);
            }
        }
        order.setCouponInfos(couponInfos);
        return webClientBuilder.build().post()
                .uri("http://coupon-calculation-serv/calculator/simulate")
                .bodyValue(order)
                .retrieve()
                .bodyToMono(SimulationResponse.class)
                .block();
//        return calculationService.simulateOrder(order);
    }

    private CouponTemplateInfo loadTemplateInfo(Long templateId) {
        return webClientBuilder.build()
                .get()
                .uri("http://coupon-template-serv/template/getTemplate?id=" + templateId)
                .retrieve()
                .bodyToMono(CouponTemplateInfo.class)
                .block();
    }

    @Override
    public void deleteCoupon(Long userId, Long couponId) {
        Coupon example = Coupon.builder()
                .userId(userId)
                .id(couponId)
                .status(CouponStatus.AVAILABLE)
                .build();
        Coupon coupon = couponDao.findAll(Example.of(example))
                .stream()
                .findFirst()
                .orElseThrow(()-> new RuntimeException("Could not find available coupon"));
        coupon.setStatus(CouponStatus.INACTIVE);
        couponDao.save(coupon);
    }

    @Override
    public List<CouponInfo> findCoupon(SearchCoupon request) {
        Coupon example = Coupon.builder()
                .shopId(request.getShopId())
                .userId(request.getUserId())
                .status(CouponStatus.convert(request.getCouponStatus()))
                .build();

        List<Coupon> coupons = couponDao.findAll(Example.of(example));
        if (coupons.isEmpty()) {
            return Lists.newArrayList();
        }
        String templateIds = coupons.stream()
                .map(Coupon::getTemplateId)
                .map(String::valueOf)
                .distinct()
                .collect(Collectors.joining(","));


        Map<Long, CouponTemplateInfo> templateInfoMap = webClientBuilder.build()
                .get()
                .uri("http://coupon-template-serv/template/getBatch?ids=" + templateIds)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<Long, CouponTemplateInfo>>(){})
                .block();
//        Map<Long, CouponTemplateInfo> templateInfoMap = couponTemplateService.getTemplateInfoMap(templateIds);
        coupons.stream().forEach(coupon ->  coupon.setTemplateInfo(templateInfoMap.get(coupon.getTemplateId())));
        return coupons.stream().map(CouponConverter::convertToCoupon).collect(Collectors.toList());
    }
}
