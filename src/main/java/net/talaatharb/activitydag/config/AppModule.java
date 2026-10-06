package net.talaatharb.activitydag.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

import net.talaatharb.activitydag.mapper.ActivityMapper;
import net.talaatharb.activitydag.mapper.ProjectMapper;
import net.talaatharb.activitydag.persistence.MapDbStorage;

import org.mapstruct.factory.Mappers;

/** Guice wiring for the application. */
public class AppModule extends AbstractModule {
    private final Path dbPath;

    public AppModule() {
        this(defaultDbPath());
    }

    public AppModule(Path dbPath) {
        this.dbPath = dbPath;
    }

    public static Path defaultDbPath() {
        String override = System.getProperty("activitydag.db");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), ".activity-dag", "activity-dag.db");
    }

    @Override
    protected void configure() {
        bind(Path.class).annotatedWith(Names.named(MapDbStorage.DB_PATH)).toInstance(dbPath);
    }

    @Provides
    @Singleton
    ProjectMapper projectMapper() {
        return Mappers.getMapper(ProjectMapper.class);
    }

    @Provides
    @Singleton
    ActivityMapper activityMapper() {
        return Mappers.getMapper(ActivityMapper.class);
    }
}
