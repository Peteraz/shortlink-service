package com.example.shortlink.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchHealthCheckRequest {

    /**
     * 待检测的短码列表。
     */
    private List<String> shortCodes;

    /**
     * 是否在整体不可达时自动标记断链。
     */
    private boolean markBroken;

}
