package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.Country;

import java.util.List;
import java.util.Optional;

public interface CountryRepository extends JpaRepository<Country, Long> {
    List<Country> findByCompanyIdOrderByNameAsc(Long companyId);
    List<Country> findByCompanyIdAndStatusOrderByNameAsc(Long companyId, String status);
    Optional<Country> findByCompanyIdAndId(Long companyId, Long id);
    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);
    long countByCompanyId(Long companyId);
}
