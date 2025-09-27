package application;

import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class keypadxd extends Application {
    private Text dateTimeText;
    private LockerManager lockerManager;
    private Timeline inactivityTimer;
    private Region neonBorder;
    private Region dimmer;

    @Override
    public void start(Stage primaryStage) {
        lockerManager = new LockerManager();

        BorderPane root = new BorderPane();

        String imagePath = "file:C:\\Users\\byeer\\eclipse-workspace\\CAPSTONETRY\\src\\application\\bg_image.png";
        BackgroundImage bgImage = new BackgroundImage(
            new Image(imagePath, 1024, 600, false, true),
            BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
            BackgroundPosition.DEFAULT, BackgroundSize.DEFAULT
        );
        root.setBackground(new Background(bgImage));

        VBox leftPanel = new VBox();
        leftPanel.setPrefWidth(100);
        leftPanel.setBackground(new Background(new BackgroundFill(Color.web("#38448c"), null, null)));
        leftPanel.setAlignment(Pos.BOTTOM_LEFT);

        dateTimeText = new Text();
        dateTimeText.setFont(Font.font("Arial", 14));
        dateTimeText.setFill(Color.WHITE);
        dateTimeText.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.75), 2, 0.5, 0, 0);");
        updateDateTime();

        Timeline dateTimeTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateDateTime()));
        dateTimeTimeline.setCycleCount(Timeline.INDEFINITE);
        dateTimeTimeline.play();

        Button helpButton = new Button("Help?");
        helpButton.setStyle(
            "-fx-font-size: 14px; -fx-background-color: #4A90E2; -fx-text-fill: white; " +
            "-fx-background-radius: 15px; -fx-border-radius: 15px; -fx-padding: 5px 15px;"
        );
        helpButton.setMinSize(95, 50);
        helpButton.setOnAction(e -> showHelpPopup());

        Button adminButton = new Button("Admin");
        adminButton.setStyle(
            "-fx-font-size: 14px; -fx-background-color: #f57c00; -fx-text-fill: white; " +
            "-fx-background-radius: 15px; -fx-border-radius: 15px; -fx-padding: 5px 15px;"
        );
        adminButton.setMinSize(95, 50);
        adminButton.setOnAction(e -> {
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("Enter Admin Password");

            Alert passwordDialog = new Alert(Alert.AlertType.CONFIRMATION);
            passwordDialog.setTitle("Admin Access");
            passwordDialog.setHeaderText("Admin Password Required");
            passwordDialog.setGraphic(null);

            VBox dialogContent = new VBox(10, new Label("Please enter the admin password:"), passwordField);
            dialogContent.setPadding(new Insets(10));
            passwordDialog.getDialogPane().setContent(dialogContent);

            ButtonType okButton = new ButtonType("Unlock", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            passwordDialog.getButtonTypes().setAll(okButton, cancelButton);

            // 🕒 Auto-close timer
            Timeline autoClose = new Timeline(new KeyFrame(Duration.seconds(30), ev -> {
                if (passwordDialog.isShowing()) {
                    passwordDialog.close();
                }
            }));
            autoClose.setCycleCount(1);
            autoClose.play();

            // 🖱️ Reset timer on interaction with password field
            passwordField.setOnKeyTyped(ev -> autoClose.playFromStart());

            passwordDialog.showAndWait().ifPresent(response -> {
                autoClose.stop(); // 🛑 Stop timer on decision

                if (response == okButton) {
                    if ("admin123".equals(passwordField.getText())) {
                        openAdminSettings();
                    } else {
                        Alert error = new Alert(Alert.AlertType.ERROR);
                        error.setTitle("Access Denied");
                        error.setHeaderText(null);
                        error.setContentText("Incorrect password.");
                        error.showAndWait();
                    }
                }
            });
        });

        VBox controls = new VBox(5, dateTimeText, helpButton, adminButton);
        controls.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setMargin(controls, new Insets(0, 0, 5, 5));

        leftPanel.getChildren().add(controls);
        root.setLeft(leftPanel);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(20));

        int lockerNumber = 1;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                Rectangle rect = new Rectangle(315, 135, Color.WHITE);
                rect.setStroke(Color.GREEN);
                rect.setStrokeWidth(5);
                rect.setArcWidth(10);
                rect.setArcHeight(10);

                Text lockerText = new Text("LOCKER " + lockerNumber);
                lockerText.setFont(new Font(15));
                lockerText.setFill(Color.BLACK);

                Text statusText = new Text("AVAILABLE");
                statusText.setStyle("-fx-font-weight: bold;");
                statusText.setFont(new Font(28));
                statusText.setFill(Color.GREEN);

                Text timerText = new Text("");
                timerText.setFont(new Font(13));
                timerText.setFill(Color.BLACK);
                timerText.setVisible(false);

                VBox textContainer = new VBox(lockerText, statusText, timerText);
                textContainer.setAlignment(Pos.CENTER);

                lockerManager.registerTimerText(lockerNumber, timerText);

                StackPane stack = new StackPane(rect, textContainer);
                stack.setAlignment(Pos.CENTER);
                int finalLockerNumber = lockerNumber;
                stack.setOnMouseClicked(event -> handleLockerClick(rect, statusText, finalLockerNumber));

                grid.add(stack, j, i);
                lockerNumber++;
            }
        }

        root.setCenter(grid);

        dimmer = new Region();
        dimmer.setPrefSize(1024, 600);
        dimmer.setStyle("-fx-background-color: rgba(0,0,0,0.9);");
        dimmer.setVisible(false);
        dimmer.setMouseTransparent(true);

        neonBorder = new Region();
        neonBorder.setPrefSize(1024, 600);
        neonBorder.setStyle(
            "-fx-border-color: #90EE90; -fx-border-width: 20px; -fx-border-radius: 25px; " +
            "-fx-background-radius: 25px; -fx-effect: dropshadow(gaussian, #90EE90, 40, 0.7, 0, 0); " +
            "-fx-background-color: transparent;"
        );
        neonBorder.setMouseTransparent(true);
        neonBorder.setVisible(false);

        StackPane mainPane = new StackPane(root, dimmer, neonBorder);
        mainPane.setPrefSize(1024, 600);

        Scene scene = new Scene(mainPane, 1024, 600);
        primaryStage.setTitle("Locker System");
        primaryStage.setScene(scene);
        primaryStage.initStyle(StageStyle.UNDECORATED);

        inactivityTimer = new Timeline(new KeyFrame(Duration.minutes(0.30), e -> showNeonBorderAndDim()));
        inactivityTimer.setCycleCount(1);
        resetInactivityTimer();

        scene.addEventFilter(MouseEvent.ANY, event -> {
            if (neonBorder.isVisible() || dimmer.isVisible()) {
                neonBorder.setVisible(false);
                dimmer.setVisible(false);
            }
            resetInactivityTimer();
        });

        primaryStage.show();
    }

    private void showHelpPopup() {
        Stage helpStage = new Stage(StageStyle.UNDECORATED);
        helpStage.initModality(Modality.APPLICATION_MODAL);
        helpStage.setTitle("Locker Tutorial");

        String[] imagePaths = new String[9];
        for (int i = 0; i < 9; i++) {
            imagePaths[i] = "file:src/application/help" + (i + 1) + ".jpg";
        }

        ImageView imageView = new ImageView();
        imageView.setFitWidth(600);
        imageView.setFitHeight(500);
        imageView.setPreserveRatio(true);

        final int[] currentIndex = {0};
        imageView.setImage(new Image(imagePaths[currentIndex[0]]));

        Button prevButton = new Button("⟨");
        prevButton.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-background-radius: 15px;");
        prevButton.setOnAction(ev -> {
            if (currentIndex[0] > 0) {
                currentIndex[0]--;
                imageView.setImage(new Image(imagePaths[currentIndex[0]]));
            }
        });

        Button nextButton = new Button("⟩");
        nextButton.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-background-radius: 15px;");
        nextButton.setOnAction(ev -> {
            if (currentIndex[0] < imagePaths.length - 1) {
                currentIndex[0]++;
                imageView.setImage(new Image(imagePaths[currentIndex[0]]));
            }
        });

        Button closeButton = new Button("✖ Close");
        closeButton.setStyle(
            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #e53935; " +
            "-fx-text-fill: white; -fx-background-radius: 10px; -fx-border-radius: 10px; -fx-padding: 8px 30px;"
        );
        closeButton.setOnAction(ev -> helpStage.close());

        HBox navBox = new HBox(20, prevButton, imageView, nextButton);
        navBox.setAlignment(Pos.CENTER);

        VBox layout = new VBox(20, navBox, closeButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        layout.setStyle(
            "-fx-background-color: #fff8e1; -fx-border-color: #bfa033; -fx-border-width: 5px; " +
            "-fx-border-radius: 1px; -fx-background-radius: 20px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0.2, 0, 4);"
        );

        Scene scene = new Scene(layout, 700, 550);
        helpStage.setScene(scene);
        helpStage.show();
    }

    private void openAdminSettings() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.setTitle("Admin Settings");

        // Title
        Label titleLabel = new Label("Locker Billing Settings");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #333;");

        // Input Fields
        TextField freeMinField = new TextField(String.valueOf(SettingsManager.getFreeMinutes()));
        TextField lockChargeField = new TextField(String.valueOf(SettingsManager.getLockCharge() / 100));
        TextField minsPerBlockField = new TextField(String.valueOf(SettingsManager.getMinutesPerBlock()));
        TextField costPerBlockField = new TextField(String.valueOf(SettingsManager.getChargePerBlock() / 100));

        freeMinField.setPrefWidth(80);
        lockChargeField.setPrefWidth(80);
        minsPerBlockField.setPrefWidth(80);
        costPerBlockField.setPrefWidth(80);

        GridPane formGrid = new GridPane();
        formGrid.setVgap(15);
        formGrid.setHgap(10);
        formGrid.setAlignment(Pos.CENTER);
        formGrid.addRow(0, new Label("Free Minutes:"), freeMinField);
        formGrid.addRow(1, new Label("₱ Locking Fee:"), lockChargeField);
        formGrid.addRow(2, new Label("Time Unit (mins):"), minsPerBlockField);
        formGrid.addRow(3, new Label("₱ per Unit:"), costPerBlockField);

        // Style labels
        for (int i = 0; i < 4; i++) {
            ((Label) formGrid.getChildren().get(i * 2)).setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        }

        // --- Locker Reset Controls ---
        Label resetLabel = new Label("Force Unlock a Locker");
        resetLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TextField lockerResetField = new TextField();
        lockerResetField.setPromptText("Locker Number (1-6)");
        lockerResetField.setPrefWidth(100);

        Button resetButton = new Button("Reset Locker");
        resetButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        resetButton.setOnAction(ev -> {
            try {
                int lockerNum = Integer.parseInt(lockerResetField.getText());
                if (lockerNum < 1 || lockerNum > 6) throw new NumberFormatException();

                lockerManager.forceUnlock(lockerNum);

                Alert confirm = new Alert(Alert.AlertType.INFORMATION, "Locker " + lockerNum + " reset successfully!");
                confirm.showAndWait();
            } catch (NumberFormatException ex) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Invalid locker number. Please enter a number from 1 to 6.");
                err.showAndWait();
            }
        });

        HBox resetBox = new HBox(10, lockerResetField, resetButton);
        resetBox.setAlignment(Pos.CENTER);

        // Save Button
        Button saveButton = new Button("💾 Save");
        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 10px 20px;");
        saveButton.setOnAction(e -> {
            try {
                int free = Integer.parseInt(freeMinField.getText());
                int lockFee = Integer.parseInt(lockChargeField.getText()) * 100;
                int mins = Integer.parseInt(minsPerBlockField.getText());
                int cost = Integer.parseInt(costPerBlockField.getText()) * 100;

                SettingsManager.setFreeMinutes(free);
                SettingsManager.setLockCharge(lockFee);
                SettingsManager.setMinutesPerBlock(mins);
                SettingsManager.setChargePerBlock(cost);

                stage.close();
            } catch (NumberFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid input. Please enter valid numbers.");
                alert.showAndWait();
            }
        });

        VBox layout = new VBox(20, titleLabel, formGrid, resetLabel, resetBox, saveButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));
        layout.setStyle(
            "-fx-background-color: #f9f9f9;" +
            "-fx-border-color: #dcdcdc;" +
            "-fx-border-radius: 10;" +
            "-fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0.2, 0, 4);"
        );

        Scene scene = new Scene(layout, 400, 440);
        stage.setScene(scene);
        stage.show();
    }


    private void showNeonBorderAndDim() {
        neonBorder.setVisible(true);
        dimmer.setVisible(true);
    }

    private void resetInactivityTimer() {
        inactivityTimer.stop();
        neonBorder.setVisible(false);
        dimmer.setVisible(false);
        inactivityTimer.playFromStart();
    }

    private void updateDateTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("  hh:mm a - EEEE\n  MMMM dd, yyyy");
        LocalDateTime now = LocalDateTime.now();
        dateTimeText.setStyle("-fx-font-size: 10px; -fx-padding: 10px;");
        dateTimeText.setText(now.format(formatter));
    }

    private void handleLockerClick(Rectangle rect, Text statusText, int lockerNumber) {
        animateLocker(rect);
        lockerManager.handleLockerClick(rect, statusText, lockerNumber);
    }

    private void animateLocker(Rectangle rect) {
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(200), rect);
        scaleUp.setToX(1.1);
        scaleUp.setToY(1.1);

        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(200), rect);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);

        scaleUp.setOnFinished(event -> scaleDown.play());
        scaleUp.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
