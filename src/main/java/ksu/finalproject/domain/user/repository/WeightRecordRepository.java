package ksu.finalproject.domain.user.repository;

import ksu.finalproject.domain.user.entity.Users;
import ksu.finalproject.domain.user.entity.WeightRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeightRecordRepository extends JpaRepository<WeightRecord, Long> {
    // 특정 날짜 기록 조회
    Optional<WeightRecord> findByUserAndRecordedDate(Users user, LocalDate recordedDate);
    // select * from weight_record where user_id = ? and recorded_date = ?

    List<WeightRecord> findByUserAndRecordedDateBetweenOrderByRecordedDateAsc(Users user, LocalDate start, LocalDate end);
    // select * from weight_record where user_id = ? and recorded_date BETWEEN ? and ? Order By recorded_date ASC;

    Optional<WeightRecord> findTopByUserOrderByRecordedDateDesc(Users user);
    // SELECT TOP 1 FROM weight_record WHERE user_id = ? ORDER BY recorded_date DESC;
}
