package net.talaatharb.activitydag.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import net.talaatharb.activitydag.dto.ActivityDto;
import net.talaatharb.activitydag.model.ActivityModel;

@Mapper
public interface ActivityMapper {
    ActivityDto toDto(ActivityModel model);

    ActivityModel toModel(ActivityDto dto);

    List<ActivityDto> toDtos(List<ActivityModel> models);

    List<ActivityModel> toModels(List<ActivityDto> dtos);
}
