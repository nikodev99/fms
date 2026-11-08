package org.gu.fms.desktop;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class FmsApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Label label = new Label("Welcome to FMS");

        StackPane root = new StackPane(label);
        Scene scene = new Scene(root, 800, 600);

        stage.setTitle("FMS - Fleet Management System");
        stage.setScene(scene);
        stage.show();
    }
}
