package net.talaatharb.activitydag.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

/** Owns the MapDB database backed by a single persistence file. */
@Singleton
public class MapDbStorage implements AutoCloseable {
    public static final String DB_PATH = "dbPath";

    private final DB db;

    @Inject
    public MapDbStorage(@Named(DB_PATH) Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create database directory for " + file, e);
        }
        this.db = DBMaker.fileDB(file.toFile()).transactionEnable().closeOnJvmShutdown().make();
    }

    @SuppressWarnings("unchecked")
    public <T> ConcurrentMap<UUID, T> map(String name) {
        return (ConcurrentMap<UUID, T>) (ConcurrentMap<UUID, ?>) db.hashMap(name, Serializer.UUID, Serializer.JAVA)
                .createOrOpen();
    }

    public synchronized void commit() {
        db.commit();
    }

    @Override
    public synchronized void close() {
        if (!db.isClosed()) {
            db.close();
        }
    }
}
