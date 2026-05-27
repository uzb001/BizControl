package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
