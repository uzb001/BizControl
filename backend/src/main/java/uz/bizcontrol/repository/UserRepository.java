package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    @Query("SELECT u FROM User u WHERE u.email = :login OR u.phone = :login")
    Optional<User> findByEmailOrPhone(String login);

    /** Lightweight projection for per-request token validation (avoids loading full entity). */
    @Query("SELECT u.status AS status, u.tokenVersion AS tokenVersion FROM User u WHERE u.id = :id")
    Optional<UserStatusProjection> findStatusById(@org.springframework.data.repository.query.Param("id") Long id);

    /** Projection interface — only status and tokenVersion are fetched. */
    interface UserStatusProjection {
        String getStatus();
        int    getTokenVersion();
    }
}
