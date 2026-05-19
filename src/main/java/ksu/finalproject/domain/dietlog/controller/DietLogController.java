package ksu.finalproject.domain.dietlog.controller;

import ksu.finalproject.domain.dietlog.dto.DietLogResponseDto;
import ksu.finalproject.domain.dietlog.service.DietLogService;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dietlog")
@RequiredArgsConstructor
public class DietLogController {

    private final DietLogService dietLogService;

    private Long extractUserId(Authentication authentication) throws CustomException {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new CustomException(ResponseCode.UNAUTHORIZED);
        }
        return userId;
    }

    @GetMapping("/stats")
    public CommonResponse<DietLogResponseDto> getStats(Authentication authentication) throws CustomException {
        return new CommonResponse<DietLogResponseDto>(ResponseCode.SUCCESS_GET_DIET_STATS,
                dietLogService.getStats(extractUserId(authentication)));
    }
}
