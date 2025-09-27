package application;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class WebViewPopup {

    public static void show(String url, Runnable onClose) {
        // Step 1: Show a loading popup immediately
        Stage loadingStage = new Stage();
        loadingStage.initModality(Modality.APPLICATION_MODAL);
        loadingStage.setTitle("Loading...");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(80, 80);
        Label loadingLabel = new Label("Preparing payment page...");
        VBox loadingBox = new VBox(10, spinner, loadingLabel);
        loadingBox.setStyle("-fx-padding: 20px; -fx-alignment: center;");
        loadingLabel.setStyle("-fx-font-size: 14px;");

        Scene loadingScene = new Scene(loadingBox, 250, 150);
        loadingStage.setScene(loadingScene);
        loadingStage.setResizable(false);
        loadingStage.show();

        // Step 2: Load WebView in background
        Platform.runLater(() -> {
            Stage webStage = new Stage();
            webStage.initModality(Modality.APPLICATION_MODAL);
            webStage.setTitle("Complete Payment");

            WebView webView = new WebView();
            webView.setPrefSize(800, 550);
            WebEngine engine = webView.getEngine();

            ProgressIndicator fullSpinner = new ProgressIndicator();
            fullSpinner.setMaxSize(80, 80);
            fullSpinner.setStyle("-fx-progress-color: #d4af37;");
            StackPane webRoot = new StackPane(webView, fullSpinner);

            final boolean[] alreadyHandled = {false};
            final boolean[] userClosedManually = {false};

            webStage.setOnCloseRequest(event -> {
                userClosedManually[0] = true;
            });

            engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == State.SUCCEEDED) {
                    fullSpinner.setVisible(false);

                    Platform.runLater(() -> {
                        loadingStage.close();
                        webStage.show();

                        // Scroll down slightly to center QR code
                        Timeline scrollDelay = new Timeline(new KeyFrame(Duration.millis(500), ev -> {
                            try {
                                engine.executeScript("window.scrollBy(0, 220);");
                            } catch (Exception ex) {
                                System.err.println("⚠️ Scroll failed: " + ex.getMessage());
                            }
                        }));
                        scrollDelay.setCycleCount(1);
                        scrollDelay.play();
                    });
                }
            });

            engine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
                if (!alreadyHandled[0] && newLoc.contains("success.local")) {
                    alreadyHandled[0] = true;
                    System.out.println("✅ Payment successful redirect detected!");
                    Platform.runLater(() -> {
                        webStage.close();
                        onClose.run();
                    });
                }
            });

            // ⏱️ Auto-close WebView after 120 seconds if no success
            new Timeline(new KeyFrame(Duration.seconds(120), e -> {
                if (!alreadyHandled[0] && !userClosedManually[0]) {
                    System.out.println("⏳ Payment timeout reached. Closing WebView.");
                    Platform.runLater(() -> {
                        webStage.close();

                        // Show alert that also closes after 30s
                        Alert timeoutAlert = new Alert(Alert.AlertType.WARNING);
                        timeoutAlert.setTitle("Timeout");
                        timeoutAlert.setHeaderText("Payment Gateway Timed Out");
                        timeoutAlert.setContentText("Please try again. The session expired after 2 minutes.");

                        Timeline autoCloseAlert = new Timeline(new KeyFrame(Duration.seconds(30), ev -> {
                            if (timeoutAlert.isShowing()) {
                                timeoutAlert.close();
                            }
                        }));
                        autoCloseAlert.setCycleCount(1);
                        autoCloseAlert.play();

                        timeoutAlert.showAndWait();
                    });
                }
            })).play();

            Scene webScene = new Scene(webRoot, 800, 550);
            webStage.setScene(webScene);
            webStage.setResizable(false);
            engine.load(url);
        });
    }
}
