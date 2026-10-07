package net.talaatharb.activitydag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.Guice;
import com.google.inject.Injector;

import net.talaatharb.activitydag.config.AppModule;
import net.talaatharb.activitydag.dto.ActivityDto;
import net.talaatharb.activitydag.mapper.ActivityMapper;
import net.talaatharb.activitydag.model.ActivityModel;
import net.talaatharb.activitydag.model.DurationUnit;
import net.talaatharb.activitydag.model.ProjectModel;
import net.talaatharb.activitydag.persistence.MapDbStorage;
import net.talaatharb.activitydag.service.ActivityService;
import net.talaatharb.activitydag.service.ActivityTransferService;
import net.talaatharb.activitydag.service.ProjectService;

class PersistenceAndServiceTest {
    @TempDir
    Path dir;
    Path db;
    Injector injector;

    @BeforeEach
    void setUp() {
        db = dir.resolve("test.db");
        injector = Guice.createInjector(new AppModule(db));
    }

    @AfterEach
    void tearDown() {
        injector.getInstance(MapDbStorage.class).close();
    }

    private ActivityModel activity(ProjectModel p, String name, ActivityModel... deps) {
        ActivityModel a = new ActivityModel();
        a.setProjectId(p.getId());
        a.setName(name);
        a.setDuration(1);
        for (ActivityModel d : deps) {
            a.getDependencies().add(d.getId());
        }
        return a;
    }

    @Test
    void dataSurvivesReopeningTheFile() {
        ProjectService projects = injector.getInstance(ProjectService.class);
        ActivityService activities = injector.getInstance(ActivityService.class);
        ProjectModel p = new ProjectModel();
        p.setName("P");
        p = projects.save(p);
        ActivityModel a = activities.save(activity(p, "A"));
        a.getMetadata().put("k", "v");
        activities.save(a);
        injector.getInstance(MapDbStorage.class).close();

        injector = Guice.createInjector(new AppModule(db));
        ActivityModel loaded = injector.getInstance(ActivityService.class).findByProject(p.getId()).get(0);
        assertEquals("A", loaded.getName());
        assertEquals("v", loaded.getMetadata().get("k"));
        assertTrue(loaded.getUpdatedAt() != null && loaded.getCreatedAt() != null);
    }

    @Test
    void dependentsCyclesAndDeletion() {
        ProjectService projects = injector.getInstance(ProjectService.class);
        ActivityService activities = injector.getInstance(ActivityService.class);
        ProjectModel p = new ProjectModel();
        p.setName("P");
        p = projects.save(p);
        ActivityModel a = activities.save(activity(p, "A"));
        ActivityModel b = activities.save(activity(p, "B", a));
        ActivityModel c = activities.save(activity(p, "C", b));

        assertEquals(Set.of(b.getId(), c.getId()), activities.findDependents(a.getId()));

        a.getDependencies().add(c.getId());
        ActivityModel cyclic = a;
        assertThrows(IllegalArgumentException.class, () -> activities.save(cyclic));

        activities.delete(b.getId());
        assertTrue(activities.findById(c.getId()).orElseThrow().getDependencies().isEmpty());

        projects.delete(p.getId());
        assertTrue(activities.findByProject(p.getId()).isEmpty());
    }

    @Test
    void duplicateProjectCopiesActivitiesWithFreshIds() {
        ProjectService projects = injector.getInstance(ProjectService.class);
        ActivityService activities = injector.getInstance(ActivityService.class);
        ProjectModel p = new ProjectModel();
        p.setName("P");
        p = projects.save(p);
        ActivityModel a = activities.save(activity(p, "A"));
        ActivityModel b = activities.save(activity(p, "B", a));

        ProjectModel copy = injector.getInstance(ActivityTransferService.class).duplicateProject(p.getId(), "P2");

        assertNotEquals(p.getId(), copy.getId());
        assertEquals("P2", copy.getName());
        List<ActivityModel> copied = activities.findByProject(copy.getId());
        assertEquals(List.of("A", "B"), copied.stream().map(ActivityModel::getName).toList());
        Set<UUID> originalIds = Set.of(a.getId(), b.getId());
        copied.forEach(c -> assertFalse(originalIds.contains(c.getId())));
        assertEquals(Set.of(copied.get(0).getId()), copied.get(1).getDependencies());
        assertEquals(Set.of(a.getId()), activities.findById(b.getId()).orElseThrow().getDependencies());
        assertEquals(2, activities.findByProject(p.getId()).size());
    }

    @Test
    void exportImportRoundTripsWithoutIds() {
        ProjectService projects = injector.getInstance(ProjectService.class);
        ActivityService activities = injector.getInstance(ActivityService.class);
        ActivityTransferService transfer = injector.getInstance(ActivityTransferService.class);
        ProjectModel p = new ProjectModel();
        p.setName("P");
        p = projects.save(p);
        ActivityModel a = activity(p, "A");
        a.setDurationUnit(DurationUnit.HOURS);
        a.setStartDate(LocalDate.of(2026, 1, 2));
        a.getMetadata().put("owner", "me");
        a = activities.save(a);
        activities.save(activity(p, "B", a));

        String json = transfer.exportJson(p.getId());
        assertFalse(json.contains(a.getId().toString()));
        assertFalse(json.contains("\"id\""));
        assertTrue(json.trim().startsWith("["));

        ProjectModel target = new ProjectModel();
        target.setName("T");
        target = projects.save(target);
        assertEquals(2, transfer.importJson(target.getId(), json));
        List<ActivityModel> imported = activities.findByProject(target.getId());
        ActivityModel ia = imported.stream().filter(x -> x.getName().equals("A")).findFirst().orElseThrow();
        ActivityModel ib = imported.stream().filter(x -> x.getName().equals("B")).findFirst().orElseThrow();
        assertNotEquals(a.getId(), ia.getId());
        assertEquals(DurationUnit.HOURS, ia.getDurationUnit());
        assertEquals(LocalDate.of(2026, 1, 2), ia.getStartDate());
        assertEquals("me", ia.getMetadata().get("owner"));
        assertEquals(Set.of(ia.getId()), ib.getDependencies());
    }

    @Test
    void invalidImportWritesNothing() {
        ProjectService projects = injector.getInstance(ProjectService.class);
        ActivityTransferService transfer = injector.getInstance(ActivityTransferService.class);
        ProjectModel p = new ProjectModel();
        p.setName("P");
        UUID id = projects.save(p).getId();
        String unknownDep = "[{\"name\":\"A\",\"duration\":1},{\"name\":\"B\",\"duration\":1,\"dependsOn\":[\"X\"]}]";
        String cycle = "[{\"name\":\"A\",\"dependsOn\":[\"B\"]},{\"name\":\"B\",\"dependsOn\":[\"A\"]}]";
        assertThrows(IllegalArgumentException.class, () -> transfer.importJson(id, unknownDep));
        assertThrows(IllegalArgumentException.class, () -> transfer.importJson(id, cycle));
        assertThrows(IllegalArgumentException.class, () -> transfer.importJson(id, "{not json"));
        assertTrue(injector.getInstance(ActivityService.class).findByProject(id).isEmpty());
    }

    @Test
    void mapperRoundTrips() {
        ActivityMapper mapper = injector.getInstance(ActivityMapper.class);
        ActivityModel m = new ActivityModel();
        m.setName("X");
        m.setResources(4);
        m.getMetadata().put("a", "b");
        ActivityDto dto = mapper.toDto(m);
        assertEquals(4, dto.getResources());
        assertEquals("b", dto.getMetadata().get("a"));
        assertEquals("X", mapper.toModel(dto).getName());
    }
}
