package ksu.finalproject.domain.food.controller;

import ksu.finalproject.domain.food.dto.FoodImageAnalyzeResponseDto;
import ksu.finalproject.domain.food.service.FoodImageService;
import ksu.finalproject.global.common.CommonResponse;
import ksu.finalproject.global.common.CustomException;
import ksu.finalproject.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/food")
@RequiredArgsConstructor
public class FoodController {

	private final FoodImageService foodImageService;

	@PostMapping(value = "/image/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public CommonResponse<FoodImageAnalyzeResponseDto> analyzeFoodImage(@RequestPart("image") MultipartFile image)
			throws CustomException {
		FoodImageAnalyzeResponseDto response = foodImageService.analyzeFoodImage(image);
		return new CommonResponse<>(ResponseCode.SUCCESS_ANALYZE_FOOD_IMAGE, response);
	}
}
