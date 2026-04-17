package com.oct.invoicesystem.domain.purchasing.mapper;

import com.oct.invoicesystem.domain.purchasing.dto.MatchingConfigDTO;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for MatchingConfig.
 */
@Mapper(componentModel = "spring")
public interface MatchingConfigMapper {

    @Mapping(target = "updatedBy", expression = "java(mc.getUpdatedBy().getId())")
    MatchingConfigDTO toDTO(MatchingConfig mc);
}
