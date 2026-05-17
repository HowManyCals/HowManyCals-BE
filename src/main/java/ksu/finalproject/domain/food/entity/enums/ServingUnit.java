package ksu.finalproject.domain.food.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ServingUnit {
    G("g", "g", "그램"),
    ML("ml", "ml", "밀리리터");
// @Deprecated
//    BOWL("공기", "공기"),
//    SERVING("인분", "인분"),
//    PORTION("회분", "회분"),
//    HAND_PORTION("회분(손바닥크기)", "회분(손바닥크기)"),
//    PIECE("개", "개"),
//    SLICE("조각", "조각"),
//    CUP("컵", "컵"),
//    DISH("그릇", "그릇"),
//    WHOLE("마리", "마리"),
//    SIDE_DISH_BOWL("반찬그릇", "반찬그릇"),
//    SLAB("절편", "절편"),
//    CHUNK("토막", "토막"),
//    PACK("팩", "팩");

    private final String label;
    private final String[] aliases;

    ServingUnit(String label, String... aliases){
        this.label = label;
        this.aliases = aliases;
    }

    @JsonCreator //Json 역직렬화
    public static ServingUnit from(String rawValue){
        if(rawValue == null || rawValue.isBlank()) throw new IllegalArgumentException("serving_unit 값이 비어있습니다.");

        String normalized = rawValue.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(u -> u.name().equals(normalized) || Arrays.stream(u.aliases).anyMatch(a -> a.equalsIgnoreCase(rawValue.trim())))
                //values() -> enum의 상수 목록 반환. u는 순회용
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 serving_unit 값입니다." + rawValue));
    }

    public String toDisplayLabel(Double weight){
        return weight + this.label; // 900+g => 900g
    }
//region [     Old Code     ]
//    private static final String DEFAULT_AMOUNT = "1";
//
//    private final String label;
//    private final String[] aliases;
//
//    ServingUnit(String label, String... aliases){ // ...은 여러 개 받을 수 있음을 의미
//        this.label = label;
//        this.aliases = aliases;
//    }
//
//    public record ParsedServing(String amount, ServingUnit unit) {
//        public String displayLabel() {
//            return unit.toDisplayLabel(amount);
//        }
//    }
//
//    // JSON 문자열 값을 {@link ServingUnit} 으로 역직렬화
//    @JsonCreator
//    public static ServingUnit from(String rawValue) {
//        return extractUnit(rawValue);
//    }
//
//    // 문자열에서 제공 단위만 추출
//    public static ServingUnit extractUnit(String rawValue) {
//        return parseServing(rawValue).unit();
//    }
//
//    /**
//     * 문자열에서 제공 수량(amount)과 제공 단위(unit)를 함께 파싱합니다.
//     * <p>
//     * 예: {@code "1공기" -> ("1", BOWL)}, {@code "0.5마리" -> ("0.5", WHOLE)}
//     * </p>
//     *
//     * @param rawValue 제공 수량/단위가 포함된 원본 문자열
//     * @return 수량과 단위가 정규화된 파싱 결과
//     * @throws IllegalArgumentException 지원하지 않는 단위거나 값이 비어 있을 때
//     */
//    public static ParsedServing parseServing(String rawValue) {
//        if (rawValue == null || rawValue.isBlank()) {
//            throw new IllegalArgumentException("serving_unit 값이 비어 있어요.");
//        }
//
//        ServingUnit exactUnit = findUnit(rawValue);
//        if (exactUnit != null) return new ParsedServing(DEFAULT_AMOUNT, exactUnit);
//
//        String compact = rawValue.trim().replace(" ", "");
//
//        int amountLength = extractLeadingNumericLength(compact);
//
//        // 수량(정수/소수), 단위 분리
//        if (amountLength > 0 && amountLength < compact.length()) {
//            return new ParsedServing(compact.substring(0, amountLength), parseUnit(compact.substring(amountLength)));
//        }
//
//        return new ParsedServing(DEFAULT_AMOUNT, parseUnit(compact));
//    }
//
//    /**
//     * 수량과 현재 단위를 사람이 읽기 쉬운 표시 문자열로 변환합니다.
//     * <p>
//     * 예: {@code amount="2", unit=SLICE -> "2조각"}
//     */
//    public String toDisplayLabel(String amount) {
//        if (amount == null || amount.isBlank()) return label;
//
//        return amount.trim() + label;
//    }
//
//    /**
//     * 입력 문자열을 정규화한 뒤, 일치하는 {@link ServingUnit} 을 반환합니다.
//     * <p>
//     * 내부적으로 {@link #findUnit(String)} 을 호출하여 enum 이름, 대표 라벨, alias 기준으로
//     * 매칭 가능한 단위를 찾습니다.
//     * </p>
//     *
//     * @param rawUnitValue 단위명 또는 enum 이름이 포함된 원본 문자열
//     * @return 정규화 후 매칭된 제공 단위 enum
//     * @throws IllegalArgumentException 정규화 후에도 일치하는 단위가 없을 때
//     */
//    private static ServingUnit parseUnit(String rawUnitValue) {
//        ServingUnit unit = findUnit(rawUnitValue);
//        // 조회 결과가 있으면 즉시 반환합니다.
//        if (unit != null) {
//            return unit;
//        }
//
//        throw new IllegalArgumentException("지원하지 않는 serving_unit 값이에요: " + rawUnitValue);
//    }
//
//    /**
//     * 문자열을 정규화한 뒤 enum 이름, 대표 라벨, alias 기준으로 단위를 탐색합니다.
//     *
//     * @param rawUnitValue 탐색할 원본 문자열 (예: "공기")
//     * @return 매칭된 단위 (예: BOWL), 없으면 {@code null}
//     */
//    private static ServingUnit findUnit(String rawUnitValue) {
//        String normalized = normalize(rawUnitValue); // 공백 없앰 + 대문자로 변환
//
//        return Arrays.stream(values())
//                .filter(unit -> unit.matches(normalized))
//                .findFirst()
//                .orElse(null);
//    }
//
//    /**
//     * 현재 enum 값이 정규화된 문자열과 같은 의미의 단위인지 검사합니다.
//     * <p>
//     * enum 이름, 대표 label, aliases 순으로 비교합니다.
//     * </p>
//     *
//     * @param normalizedValue 공백 제거 및 대문자 변환이 완료된 문자열
//     * @return 매칭 여부
//     */
//    private boolean matches(String normalizedValue) {
//        if (normalize(name()).equals(normalizedValue)) return true;
//
//        // 화면 표시용 라벨과 일치해도 true
//        if (normalize(label).equals(normalizedValue)) return true;
//
//        return Arrays.stream(aliases)
//                .map(ServingUnit::normalize)
//                .anyMatch(normalizedValue::equals);
//    }
//
//    /**
//     * 문자열 앞부분에 연속으로 등장하는 수치(정수 또는 소수)의 길이를 구합니다.
//     *
//     * @param value 검사할 문자열
//     * @return 선행 수치 길이, 없으면 0
//     */
//    private static int extractLeadingNumericLength(String value) {
//        int index = 0;
//        while (index < value.length() && Character.isDigit(value.charAt(index))) {
//            index++;
//        }
//
//        if (index > 0 && index < value.length() && value.charAt(index) == '.') {
//            int decimalIndex = index + 1;
//
//            while (decimalIndex < value.length() && Character.isDigit(value.charAt(decimalIndex))) {
//                decimalIndex++;
//            }
//
//            if (decimalIndex > index + 1) {
//                return decimalIndex;
//            }
//        }
//
//        return index;
//    }
//
//    /**
//     * 단위 비교를 위해 문자열을 정규화합니다.
//     * <p>
//     * 앞뒤 공백을 제거하고, 중간 공백을 없애고, 대문자로 변환합니다.
//     * </p>
//     *
//     * @param value 정규화할 문자열
//     * @return 정규화된 문자열
//     */
//    private static String normalize(String value) {
//        return value.trim().replace(" ", "").toUpperCase();
//    }
//endregion
}


