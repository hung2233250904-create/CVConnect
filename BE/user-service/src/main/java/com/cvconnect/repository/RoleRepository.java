package com.cvconnect.repository;

import com.cvconnect.dto.role.RoleDto;
import com.cvconnect.dto.role.RoleFilterRequest;
import com.cvconnect.entity.Role;
import com.cvconnect.enums.MemberType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    @Query("SELECT r FROM Role r WHERE r.code = :code")
        Role findByCode(@Param("code") String code);

    @Modifying
    @Query("DELETE FROM Role r WHERE r.id IN :ids")
        void deleteByIds(@Param("ids") List<Long> ids);

    @Query("SELECT new com.cvconnect.dto.role.RoleDto(r.id, r.code, r.name, r.memberType, r.createdAt, r.updatedAt, r.createdBy, r.updatedBy) " +
            "FROM Role r " +
            "WHERE (:#{#request.code} IS NULL OR LOWER(r.code) LIKE LOWER(CONCAT('%', :#{#request.code}, '%'))) " +
            "AND (:#{#request.name} IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :#{#request.name}, '%'))) " +
            "AND (:#{#request.memberType == null || #request.memberType.isEmpty()} = true OR r.memberType IN :#{#request.memberType}) " +
            "AND (r.createdAt >= COALESCE(:#{#request.createdAtStart}, r.createdAt)) " +
            "AND (r.createdAt <= COALESCE(:#{#request.createdAtEnd}, r.createdAt)) " +
            "AND (COALESCE(:#{#request.updatedAtStart}, NULL) IS NULL OR (r.updatedAt IS NOT NULL AND r.updatedAt >= :#{#request.updatedAtStart})) " +
            "AND (COALESCE(:#{#request.updatedAtEnd}, NULL) IS NULL OR (r.updatedAt IS NOT NULL AND r.updatedAt <= :#{#request.updatedAtEnd})) " +
            "AND (:#{#request.createdBy} IS NULL OR LOWER(r.createdBy) LIKE LOWER(CONCAT('%', :#{#request.createdBy}, '%'))) " +
            "AND (:#{#request.updatedBy} IS NULL OR LOWER(r.updatedBy) LIKE LOWER(CONCAT('%', :#{#request.updatedBy}, '%'))) "
    )
    Page<RoleDto> filter(@Param("request") RoleFilterRequest request, Pageable pageable);

    @Query("SELECT new com.cvconnect.dto.role.RoleDto(r.id, r.code, r.name, r.memberType, ru.isDefault, r.createdAt, r.updatedAt, r.createdBy, r.updatedBy) " +
            "FROM Role r " +
            "JOIN RoleUser ru ON ru.roleId = r.id " +
            "WHERE ru.userId = :userId")
        List<RoleDto> getRoleByUserId(@Param("userId") Long userId);

    @Query("SELECT r from Role r WHERE r.memberType = :memberType")
        List<Role> findByMemberType(@Param("memberType") MemberType memberType);
}
