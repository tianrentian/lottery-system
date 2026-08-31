package com.example.lotterysystem.controller;

import com.example.lotterysystem.common.pojo.CommonResult;
import com.example.lotterysystem.controller.param.DemoVisitEventParam;
import com.example.lotterysystem.service.DemoVisitService;
import com.example.lotterysystem.service.dto.DemoVisitStatisticsDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo-visits")
public class DemoVisitController {

    @Autowired
    private DemoVisitService demoVisitService;

    @PostMapping("/events")
    public CommonResult<Boolean> reportEvent(
            @Validated @RequestBody DemoVisitEventParam param) {
        demoVisitService.reportEvent(param);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/statistics")
    public CommonResult<DemoVisitStatisticsDTO> getStatistics(
            @RequestParam(defaultValue = "ALL") String range) {
        return CommonResult.success(demoVisitService.getStatistics(range));
    }
}
