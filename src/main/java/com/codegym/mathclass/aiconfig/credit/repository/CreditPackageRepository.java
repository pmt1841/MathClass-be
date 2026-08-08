package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.CreditPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditPackageRepository extends JpaRepository<CreditPackage, Long> {

    List<CreditPackage> findByEnabledTrueOrderBySortOrderAsc();

    List<CreditPackage> findAllByOrderBySortOrderAsc();
}
