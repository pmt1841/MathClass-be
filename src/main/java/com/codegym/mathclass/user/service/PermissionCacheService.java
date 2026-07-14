package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionCacheService {

    private final RolePermissionRepository rolePermissionRepository;

    @Cacheable(value = "rolePermissions", key = "#role.name()")
    public List<String> getPermissionsByRole(Role role) {
        log.debug("Fetching permissions from DB for role: {}", role);
        return rolePermissionRepository.findPermissionNamesByRole(role);
    }

    @CacheEvict(value = "rolePermissions", key = "#role.name()")
    public void evictPermissionsCache(Role role) {
        log.debug("Evicting permissions cache for role: {}", role);
    }
    
    @CacheEvict(value = "rolePermissions", allEntries = true)
    public void evictAllPermissionsCache() {
        log.debug("Evicting all permissions cache");
    }
}
