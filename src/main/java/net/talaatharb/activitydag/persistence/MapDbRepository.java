package net.talaatharb.activitydag.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import net.talaatharb.activitydag.model.BaseModel;

/** Generic MapDB backed repository; every write is committed immediately. */
public abstract class MapDbRepository<T extends BaseModel> {
    private final MapDbStorage storage;
    private final ConcurrentMap<UUID, T> map;

    protected MapDbRepository(MapDbStorage storage, String mapName) {
        this.storage = storage;
        this.map = storage.map(mapName);
    }

    public T save(T entity) {
        Instant now = Instant.now();
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        map.put(entity.getId(), entity);
        storage.commit();
        return entity;
    }

    public Optional<T> findById(UUID id) {
        return Optional.ofNullable(map.get(id));
    }

    public List<T> findAll() {
        List<T> all = new ArrayList<>(map.values());
        all.sort(Comparator.comparing(BaseModel::getCreatedAt));
        return all;
    }

    public void deleteById(UUID id) {
        map.remove(id);
        storage.commit();
    }

    protected void deleteAll(List<UUID> ids) {
        ids.forEach(map::remove);
        storage.commit();
    }
}
