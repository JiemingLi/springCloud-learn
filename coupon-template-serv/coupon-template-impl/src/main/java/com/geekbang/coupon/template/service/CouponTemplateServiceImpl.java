package com.geekbang.coupon.template.service;

import com.geekbang.coupon.template.api.beans.CouponTemplateInfo;
import com.geekbang.coupon.template.api.beans.PagedCouponTemplateInfo;
import com.geekbang.coupon.template.api.beans.TemplateSearchParams;
import com.geekbang.coupon.template.api.enums.CouponType;
import com.geekbang.coupon.template.convert.CouponTemplateConvert;
import com.geekbang.coupon.template.dao.CouponTemplateDao;
import com.geekbang.coupon.template.dao.entity.CouponTemplate;
import com.geekbang.coupon.template.service.intf.CouponTemplateService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CouponTemplateServiceImpl implements CouponTemplateService {


    @Autowired
    private CouponTemplateDao couponTemplateDao;

    @Override
    public CouponTemplateInfo createTemplate(CouponTemplateInfo request) {
        if (request.getShopId() != null) {
            // 单个门店最多可以创建100张优惠券模板
            Integer count = couponTemplateDao.countByShopIdAndAvailable(request.getShopId(), true);
            if (count > 100) {
                log.error("the totals of coupon template exceeds maximum number");
                throw new UnsupportedOperationException("exceeded the maximum of coupon templates that you can create");
            }
        }

        // 创建优惠券
        CouponTemplate couponTemplate = CouponTemplate.builder()
                .name(request.getName())
                .description(request.getDesc())
                .category(CouponType.convert(request.getType()))
                .available(true)
                .shopId(request.getShopId())
                .rule(request.getRule())
                .build();
        CouponTemplate template = couponTemplateDao.save(couponTemplate);
        return CouponTemplateConvert.convert(template);
    }

    @Override
    public CouponTemplateInfo cloneTemplate(Long templateId) {
        CouponTemplate couponTemplate = couponTemplateDao.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("invalid template ID"));

        CouponTemplate target = new CouponTemplate();
        BeanUtils.copyProperties(couponTemplate, target);
        target.setAvailable(true);
        target.setId(null);

        couponTemplateDao.save(target);
        return CouponTemplateConvert.convert(target);
    }

    @Override
    public CouponTemplateInfo loadTemplateInfo(Long id) {
        Optional<CouponTemplate> res = couponTemplateDao.findById(id);
        return res.isPresent()?CouponTemplateConvert.convert(res.get()):null;
    }

    @Override
    public void deleteTemplate(Long id) {
        int rows = couponTemplateDao.makeCouponUnavailable(id);
        if (rows == 0) {
            throw new IllegalArgumentException("Template Not Found: " + id);
        }

    }

    @Override
    public PagedCouponTemplateInfo search(TemplateSearchParams request) {
        CouponTemplate example = CouponTemplate.builder()
                .shopId(request.getShopId())
                .category(CouponType.convert(request.getType()))
                .available(request.getAvailable())
                .name(request.getName())
                .build();


        PageRequest page = PageRequest.of(request.getPage(), request.getPageSize());
        Page<CouponTemplate> result = couponTemplateDao.findAll(Example.of(example), page);

        List<CouponTemplateInfo> couponTemplateInfos = result.stream()
                .map(CouponTemplateConvert::convert)
                .collect(Collectors.toList());
        PagedCouponTemplateInfo response = PagedCouponTemplateInfo.builder()
                .templates(couponTemplateInfos)
                .page(request.getPageSize())
                .total(result.getTotalElements())
                .build();
        return response;
    }

    @Override
    public Map<Long, CouponTemplateInfo> getTemplateInfoMap(Collection<Long> ids) {
        List<CouponTemplate> templates = couponTemplateDao.findAllById(ids);
        return templates.stream()
                .map((template)-> CouponTemplateConvert.convert(template))
                .collect(Collectors.toMap(((couponTemplateInfo) -> couponTemplateInfo.getId()), Function.identity()));
    }
}
