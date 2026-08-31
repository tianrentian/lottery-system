package com.example.lotterysystem.service;

import com.example.lotterysystem.controller.param.DemoVisitEventParam;
import com.example.lotterysystem.service.dto.DemoVisitStatisticsDTO;

public interface DemoVisitService {

    void reportEvent(DemoVisitEventParam param);

    DemoVisitStatisticsDTO getStatistics(String range);
}
