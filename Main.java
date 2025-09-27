package application;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.List;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            // ⏺ Check for interrupted lockers on launch
            List<Integer> interruptedLockers = DatabaseHelper.getInterruptedLockers();
            if (!interruptedLockers.isEmpty()) {
                System.out.println("⚠️ Interrupted lockers found: " + interruptedLockers);

                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Interrupted Lockers Detected");
                alert.setHeaderText("Some lockers may not have been closed properly.");
                alert.setContentText("Interrupted lockers: " + interruptedLockers);
                alert.showAndWait();
            }

            // 🔧 UI Setup
            BorderPane root = new BorderPane();
            Scene scene = new Scene(root, 400, 400);
            scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.setTitle("ScaNGo Locker System");
            primaryStage.show();
            
            

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
