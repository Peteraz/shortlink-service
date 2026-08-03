package com.example.shortlink.health;

import com.example.shortlink.dto.response.UrlHealthResult;

public interface LinkHealthChecker {

    UrlHealthResult check(String url);
}
