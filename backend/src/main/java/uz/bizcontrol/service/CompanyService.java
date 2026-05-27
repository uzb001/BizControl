package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.CompanyRepository;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    public Company save(Company company) {
        return companyRepository.save(company);
    }

    public Company getById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Company"));
    }

    @Transactional
    public Company update(Long id, Company updates) {
        Company company = getById(id);
        if (updates.getName() != null) company.setName(updates.getName());
        if (updates.getPhone() != null) company.setPhone(updates.getPhone());
        if (updates.getAddress() != null) company.setAddress(updates.getAddress());
        if (updates.getBusinessType() != null) company.setBusinessType(updates.getBusinessType());
        if (updates.getMainCurrency() != null) company.setMainCurrency(updates.getMainCurrency());
        if (updates.getTaxId() != null) company.setTaxId(updates.getTaxId());
        if (updates.getLogoUrl() != null) company.setLogoUrl(updates.getLogoUrl());
        return companyRepository.save(company);
    }
}
