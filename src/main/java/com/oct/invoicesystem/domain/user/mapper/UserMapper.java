package com.oct.invoicesystem.domain.user.mapper;

import com.oct.invoicesystem.domain.user.dto.UserCreateRequest;
import com.oct.invoicesystem.domain.user.dto.UserDTO;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", source = "userRoles", qualifiedByName = "mapUserRolesToRoleNames")
    @Mapping(target = "active", source = "active")
    UserDTO toDto(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "password", ignore = true)
    User toEntity(UserCreateRequest request);

    @Named("mapUserRolesToRoleNames")
    default List<String> mapUserRolesToRoleNames(Set<UserRole> userRoles) {
        if (userRoles == null) {
            return Collections.emptyList();
        }
        return userRoles.stream()
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toList());
    }

    default ZonedDateTime map(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneOffset.UTC);
    }
}
