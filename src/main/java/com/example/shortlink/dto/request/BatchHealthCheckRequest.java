package com.example.shortlink.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchHealthCheckRequest {

    /**
     * 待检测的完整短链列表。
     */
    @NotEmpty(message = "短链列表不能为空")
    private List<@NotBlank(message = "短链不能为空") String> shortUrls;

    /**
     * 是否在整体不可达时自动标记断链。
     */
    private boolean markBroken;

}
