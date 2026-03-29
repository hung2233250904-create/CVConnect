package com.cvconnect.repository;

import com.cvconnect.dto.user.UserFilterRequest;
import com.cvconnect.dto.user.UserProjection;
import com.cvconnect.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.accessMethod LIKE '%LOCAL%'")
    Optional<User> findByUsernameLogin(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(ru) > 0 THEN true ELSE false END " +
            "FROM RoleUser ru " +
            "JOIN Role r ON ru.roleId = r.id " +
            "JOIN OrgMember om ON ru.userId = om.userId AND om.isActive = true " +
            "WHERE ru.userId = :userId AND r.code = :roleCode AND om.orgId = :orgId")
            Boolean checkOrgUserRole(@Param("userId") Long userId, @Param("roleCode") String roleCode, @Param("orgId") Long orgId);

    @Query("SELECT DISTINCT u FROM User u " +
            "JOIN RoleUser ru ON u.id = ru.userId " +
            "JOIN Role r ON ru.roleId = r.id " +
            "JOIN OrgMember om ON ru.userId = om.userId AND (:active IS NULL OR om.isActive = :active) " +
            "WHERE r.code = :roleCode AND om.orgId = :orgId"
    )
            List<User> getUsersByRoleCodeOrg(@Param("roleCode") String roleCode, @Param("orgId") Long orgId, @Param("active") Boolean active);

    @Query("""
        SELECT u FROM User u
        WHERE
        (:#{#request.memberTypes} IS NULL OR u.id NOT IN (
            SELECT ru1.userId FROM RoleUser ru1
            JOIN Role r1 ON ru1.roleId = r1.id
            WHERE r1.memberType IN :#{#request.memberTypes}
        ))
        AND (:#{#request.email} IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :#{#request.email}, '%')))
    """)
    Page<User> findNotOrgMember(@Param("request") UserFilterRequest request, Pageable pageable);

    @Query(value = """
        SELECT distinct u.id AS id,
               u.username AS username,
               u.email AS email,
               u.fullName AS fullName,
               u.phoneNumber AS phoneNumber,
               u.dateOfBirth AS dateOfBirth,
               u.accessMethod AS accessMethod,
               u.isEmailVerified AS isEmailVerified,
               u.avatarId AS avatarId,
               u.isActive AS isActive,
               u.createdAt AS createdAt,
               u.createdBy AS createdBy,
               u.updatedAt AS updatedAt,
               u.updatedBy AS updatedBy
        FROM User u
        JOIN RoleUser ru ON u.id = ru.userId
        WHERE (:#{#request.username} IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :#{#request.username}, '%')))
          AND (:#{#request.email} IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :#{#request.email}, '%')))
          AND (:#{#request.fullName} IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :#{#request.fullName}, '%')))
          AND (:#{#request.phoneNumber} IS NULL OR LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :#{#request.phoneNumber}, '%')))
          AND (COALESCE(:#{#request.dateOfBirthStart}, NULL) IS NULL OR (u.dateOfBirth IS NOT NULL AND u.dateOfBirth >= :#{#request.dateOfBirthStart}))
          AND (COALESCE(:#{#request.dateOfBirthEnd}, NULL) IS NULL OR (u.dateOfBirth IS NOT NULL AND u.dateOfBirth <= :#{#request.dateOfBirthEnd}))
          AND (:#{#request.accessMethod?.name()} IS NULL OR lower(u.accessMethod) like lower(concat('%', :#{#request.accessMethod?.name()}, '%')))
          AND (:#{#request.isEmailVerified} IS NULL OR u.isEmailVerified = :#{#request.isEmailVerified})
          and (:#{#request.roleIds == null || #request.roleIds.isEmpty()} = true or ru.roleId in :#{#request.roleIds})
          AND (:#{#request.isActive} IS NULL OR u.isActive = :#{#request.isActive})
          AND (u.createdAt >= COALESCE(:#{#request.createdAtStart}, u.createdAt))
          AND (u.createdAt <= COALESCE(:#{#request.createdAtEnd}, u.createdAt))
          AND (COALESCE(:#{#request.updatedAtStart}, NULL) IS NULL OR (u.updatedAt IS NOT NULL AND u.updatedAt >= :#{#request.updatedAtStart}))
          AND (COALESCE(:#{#request.updatedAtEnd}, NULL) IS NULL OR (u.updatedAt IS NOT NULL AND u.updatedAt <= :#{#request.updatedAtEnd}))
          AND (:#{#request.createdBy} IS NULL OR LOWER(u.createdBy) LIKE LOWER(CONCAT('%', :#{#request.createdBy}, '%')))
          AND (:#{#request.updatedBy } IS NULL OR LOWER(u.updatedBy) LIKE LOWER(CONCAT('%', :#{#request.updatedBy}, '%')))
    """,
    countQuery = """
        SELECT distinct u.id AS id
        FROM User u
        JOIN RoleUser ru ON u.id = ru.userId
        WHERE (:#{#request.username} IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :#{#request.username}, '%')))
          AND (:#{#request.email} IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :#{#request.email}, '%')))
          AND (:#{#request.fullName} IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :#{#request.fullName}, '%')))
          AND (:#{#request.phoneNumber} IS NULL OR LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :#{#request.phoneNumber}, '%')))
          AND (COALESCE(:#{#request.dateOfBirthStart}, NULL) IS NULL OR (u.dateOfBirth IS NOT NULL AND u.dateOfBirth >= :#{#request.dateOfBirthStart}))
          AND (COALESCE(:#{#request.dateOfBirthEnd}, NULL) IS NULL OR (u.dateOfBirth IS NOT NULL AND u.dateOfBirth <= :#{#request.dateOfBirthEnd}))
          AND (:#{#request.accessMethod} IS NULL OR lower(u.accessMethod) like lower(concat('%', :#{#request.accessMethod?.name()}, '%')))
          AND (:#{#request.isEmailVerified} IS NULL OR u.isEmailVerified = :#{#request.isEmailVerified})
          and (:#{#request.roleIds == null || #request.roleIds.isEmpty()} = true or ru.roleId in :#{#request.roleIds})
          AND (:#{#request.isActive} IS NULL OR u.isActive = :#{#request.isActive})
          AND (u.createdAt >= COALESCE(:#{#request.createdAtStart}, u.createdAt))
          AND (u.createdAt <= COALESCE(:#{#request.createdAtEnd}, u.createdAt))
          AND (COALESCE(:#{#request.updatedAtStart}, NULL) IS NULL OR (u.updatedAt IS NOT NULL AND u.updatedAt >= :#{#request.updatedAtStart}))
          AND (COALESCE(:#{#request.updatedAtEnd}, NULL) IS NULL OR (u.updatedAt IS NOT NULL AND u.updatedAt <= :#{#request.updatedAtEnd}))
          AND (:#{#request.createdBy} IS NULL OR LOWER(u.createdBy) LIKE LOWER(CONCAT('%', :#{#request.createdBy}, '%')))
          AND (:#{#request.updatedBy } IS NULL OR LOWER(u.updatedBy) LIKE LOWER(CONCAT('%', :#{#request.updatedBy}, '%')))
    """
    )
    Page<UserProjection> filter(@Param("request") UserFilterRequest request, Pageable pageable);

    @Query("SELECT DISTINCT u FROM User u " +
            "JOIN RoleUser ru ON u.id = ru.userId " +
            "JOIN Role r ON ru.roleId = r.id " +
            "WHERE r.code = :roleCode and (:active IS NULL OR u.isActive = :active)"
    )
    List<User> getUsersByRoleCode(@Param("roleCode") String roleCode, @Param("active") Boolean active);

}
