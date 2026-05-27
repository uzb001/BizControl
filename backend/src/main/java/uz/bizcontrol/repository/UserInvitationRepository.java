package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.UserInvitation;
import java.util.List;
import java.util.Optional;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {
    Optional<UserInvitation> findByToken(String token);
    List<UserInvitation> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    boolean existsByCompanyIdAndEmailAndStatus(Long companyId, String email, String status);
    boolean existsByCompanyIdAndPhoneAndStatus(Long companyId, String phone, String status);
}
