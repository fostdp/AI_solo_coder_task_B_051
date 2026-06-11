package com.saltdamage.repository;

import com.saltdamage.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByPhone(String phone);

    Optional<SysUser> findByEmail(String email);

    List<SysUser> findByRole(String role);

    List<SysUser> findByStatus(String status);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    List<SysUser> findByNameContaining(String keyword);
}
