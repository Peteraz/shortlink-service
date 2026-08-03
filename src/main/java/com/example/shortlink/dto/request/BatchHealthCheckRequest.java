package com.example.shortlink.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotEmpty(message = "shortCodes must not be empty")
    @Size(max = 100, message = "shortCodes must not contain more than 100 items")
    private List<@NotBlank(message = "shortCode must not be blank") String> shortCodes;

    /**
     * 是否在整体不可达时自动标记断链。
     */
    private boolean markBroken;

}
