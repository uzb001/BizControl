package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.Warehouse;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findByCompanyIdOrderByNameAsc(Long companyId);
    List<Warehouse> findByCompanyIdAndStatus(Long companyId, String status);
    Optional<Warehouse> findByCompanyIdAndId(Long companyId, Long id);
    Optional<Warehouse> findFirstByCompanyIdAndCode(Long companyId, String code);
    boolean existsByCompanyIdAndCode(Long companyId, String code);
    long countByCompanyId(Long companyId);
    long countByCompanyIdAndCountryId(Long companyId, Long countryId);
}
