package com.example.shortlink.service;

import com.example.shortlink.dto.request.BatchHealthCheckRequest;
import com.example.shortlink.dto.response.HealthCheckResponse;

import java.util.List;

public interface LinkHealthService {

    HealthCheckResponse healthCheck(String shortCode, boolean markBroken);

    List<HealthCheckResponse> batchHealthCheck(BatchHealthCheckRequest request);
}
