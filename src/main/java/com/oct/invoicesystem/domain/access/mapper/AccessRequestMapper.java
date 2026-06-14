package com.oct.invoicesystem.domain.access.mapper;

import com.oct.invoicesystem.domain.access.dto.AccessRequestDTO;
import com.oct.invoicesystem.domain.access.model.AccessRequest;
import com.oct.invoicesystem.domain.user.model.User;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.Instant;

/**
 * Hand-written mapper for {@link AccessRequest}. Done by hand (not MapStruct) because the DTO
 * flattens the requester / reviewer associations into denormalised display fields (full name),
 * which MapStruct cannot express without brittle expression strings.
 */
@Component
public class AccessRequestMapper {

    public AccessRequestDTO toDto(AccessRequest entity) {
        User reviewer = entity.getReviewedBy();
        return new AccessRequestDTO(
                entity.getId(),
                entity.getRequester().getId(),
                entity.getRequester().getUsername(),
                fullName(entity.getRequester()),
                entity.getRequestedRole(),
                entity.getReason(),
                entity.getStatus(),
                reviewer != null ? reviewer.getId() : null,
                reviewer != null ? fullName(reviewer) : null,
                entity.getReviewComment(),
                toZoned(entity.getCreatedAt()),
                toZoned(entity.getReviewedAt())
        );
    }

    private String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private ZonedDateTime toZoned(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneOffset.UTC);
    }
}
