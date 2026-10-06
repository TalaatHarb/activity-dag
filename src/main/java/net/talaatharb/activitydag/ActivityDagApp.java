package net.talaatharb.activitydag;

import java.io.IOException;

import com.google.inject.Guice;
import com.google.inject.Injector;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.talaatharb.activitydag.config.AppModule;
import net.talaatharb.activitydag.persistence.MapDbStorage;

public class ActivityDagApp extends Application {
    private Injector injector;

    @Override
    public void init() {
        injector = Guice.createInjector(new AppModule());
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("ui/main.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent root = loader.load();
        stage.setTitle("Activity DAG");
        stage.setScene(new Scene(root, 1240, 760));
        stage.show();
    }

    @Override
    public void stop() {
        injector.getInstance(MapDbStorage.class).close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
