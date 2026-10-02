package com.codegym.mathclass.user.repository;

import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    @Query("SELECT rp.permission.name FROM RolePermission rp WHERE rp.role = :role")
    List<String> findPermissionNamesByRole(@Param("role") Role role);

    @Query("SELECT rp FROM RolePermission rp JOIN FETCH rp.permission WHERE rp.role = :role")
    List<RolePermission> findByRoleWithPermission(@Param("role") Role role);

    List<RolePermission> findByRole(Role role);
}
