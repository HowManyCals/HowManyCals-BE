package ksu.finalproject.domain.user.dto;

import ksu.finalproject.domain.user.entity.enums.ActivityLevel;
import ksu.finalproject.domain.user.entity.enums.Gender;
import ksu.finalproject.domain.user.entity.enums.GoalType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UpdateProfileResponseDto {
    private String nickname;
    private Gender gender;
    private Integer height;
    private Integer weight;
    private Integer age;
    private GoalType goalType;
    private ActivityLevel activityLevel;
}

