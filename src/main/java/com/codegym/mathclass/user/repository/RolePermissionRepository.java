package com.codegym.mathclass.user.repository;

import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    @Query("SELECT rp.permission.name FROM RolePermission rp WHERE rp.role = :role")
    List<String> findPermissionNamesByRole(@Param("role") Role role);

    List<RolePermission> findByRole(Role role);
}
