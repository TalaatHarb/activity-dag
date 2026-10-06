package net.talaatharb.activitydag.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import net.talaatharb.activitydag.dto.ProjectDto;
import net.talaatharb.activitydag.model.ProjectModel;

@Mapper
public interface ProjectMapper {
    ProjectDto toDto(ProjectModel model);

    ProjectModel toModel(ProjectDto dto);

    List<ProjectDto> toDtos(List<ProjectModel> models);
}
