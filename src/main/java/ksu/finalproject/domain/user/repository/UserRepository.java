package ksu.finalproject.domain.user.repository;

import ksu.finalproject.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Users, Long>{
    // 이메일 값 = nullable이므로 Optional 추가
    Optional<Users> findByEmail(String email);
}
