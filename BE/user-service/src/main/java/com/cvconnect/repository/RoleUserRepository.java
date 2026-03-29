package com.cvconnect.repository;

import com.cvconnect.dto.roleUser.RoleUserProjection;
import com.cvconnect.entity.RoleUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleUserRepository extends JpaRepository<RoleUser, Long> {
    @Query("SELECT ru FROM RoleUser ru WHERE ru.userId = :userId AND ru.roleId = :roleId")
        RoleUser findByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Query("SELECT ru.id AS id, ru.userId AS userId, r.id AS roleId, r.name AS roleName, r.code AS roleCode, r.memberType AS memberType, ru.isDefault AS isDefault " +
            "FROM RoleUser ru " +
            "JOIN Role r ON r.id = ru.roleId " +
            "WHERE ru.userId IN :userIds")
    List<RoleUserProjection> findRoleUseByUserId(@Param("userIds") List<Long> userIds);

    @Query("SELECT ru FROM RoleUser ru WHERE ru.userId = :userId")
    List<RoleUser> findByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM RoleUser ru WHERE ru.userId = :userId AND ru.roleId IN :roleIds")
        void deleteByUserIdAndRoleIds(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Query("SELECT CASE WHEN COUNT(ru) > 0 THEN true ELSE false END " +
            "FROM RoleUser ru " +
            "join User u on u.id = ru.userId " +
            "WHERE ru.roleId = :roleId " +
            "and u.isActive = true and u.isEmailVerified = true ")
        Boolean existsUserActiveByRoleId(@Param("roleId") Long roleId);
}
