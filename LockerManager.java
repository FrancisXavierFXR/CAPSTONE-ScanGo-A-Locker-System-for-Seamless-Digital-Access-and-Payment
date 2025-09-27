package application;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.stage.Modality;
import java.time.temporal.ChronoUnit;
import javafx.stage.Window;
import javafx.scene.Node;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class LockerManager {
    private final Map<Integer, LockerData> lockerDataMap = new HashMap<>();
    private final Map<Integer, Timeline> lockTimers = new HashMap<>();
    private final Map<Integer, LocalDateTime> lockStartTimes = new HashMap<>();
    private final Map<Integer, Text> timerTexts = new HashMap<>();
    private TextField activeField;

    public static class LockerData {
        String studentYear, studentId, passcode;
        LockerData(String studentYear, String studentId, String passcode) {
            this.studentYear = studentYear;
            this.studentId = studentId;
            this.passcode = passcode;
        }
    }

    public void registerTimerText(int lockerNumber, Text timerText) {
        timerTexts.put(lockerNumber, timerText);
    }

    public void handleLockerClick(Rectangle rect, Text statusText, int lockerNumber) {
        if ("AVAILABLE".equals(statusText.getText())) {
            lockLocker(rect, statusText, lockerNumber);
        } else {
            unlockLocker(rect, statusText, lockerNumber);
        }
    }
    
    public void forceUnlock(int lockerNumber) {
        lockerDataMap.remove(lockerNumber);
        lockStartTimes.remove(lockerNumber);

        Timeline timeline = lockTimers.get(lockerNumber);
        if (timeline != null) {
            timeline.stop();
            lockTimers.remove(lockerNumber);
        }

        Text timerText = timerTexts.get(lockerNumber);
        if (timerText != null) {
            Platform.runLater(() -> {
                timerText.setVisible(false);
                timerText.setText("");
            });
        }

        // Update database
        LocalDateTime unlockTime = LocalDateTime.now();
        DatabaseHelper.updateLockerUnlockTime(lockerNumber, unlockTime);
        DatabaseHelper.deleteLockerUsage(lockerNumber);

        // 🟢 Update UI — find matching UI node and reset visual state
        Platform.runLater(() -> {
            Scene scene = Stage.getWindows().stream()
                .filter(Window::isShowing)
                .findFirst()
                .map(Window::getScene)
                .orElse(null);

            if (scene == null) return;

            Node rootNode = scene.getRoot();
            if (rootNode instanceof StackPane stackPane) {
                BorderPane borderPane = (BorderPane) stackPane.getChildren().get(0);
                GridPane grid = (GridPane) borderPane.getCenter();

                for (Node node : grid.getChildren()) {
                    if (node instanceof StackPane stack) {
                        VBox vbox = (VBox) stack.getChildren().get(1);
                        Text lockerLabel = (Text) vbox.getChildren().get(0);
                        Text statusText = (Text) vbox.getChildren().get(1);
                        Text timer = (Text) vbox.getChildren().get(2);

                        if (lockerLabel.getText().endsWith(String.valueOf(lockerNumber))) {
                            Rectangle rect = (Rectangle) stack.getChildren().get(0);
                            statusText.setText("AVAILABLE");
                            statusText.setFill(Color.GREEN);
                            rect.setStroke(Color.GREEN);
                            timer.setVisible(false);
                            timer.setText("");
                            break;
                        }
                    }
                }
            }
        });

    }

    
    private void unlockWithoutPayment(Rectangle rect, Text statusText, int lockerNumber) {
        statusText.setText("AVAILABLE");
        statusText.setFill(Color.GREEN);
        rect.setStroke(Color.GREEN);
        lockerDataMap.remove(lockerNumber);
        lockStartTimes.remove(lockerNumber);

        Timeline timeline = lockTimers.get(lockerNumber);
        if (timeline != null) {
            timeline.stop();
            lockTimers.remove(lockerNumber);
        }

        Text timerText = timerTexts.get(lockerNumber);
        if (timerText != null) {
            timerText.setVisible(false);
            timerText.setText("");
        }

        LocalDateTime unlockTime = LocalDateTime.now();
        //    Pi4JTest.triggerLockerLED(lockerNumber); // ✅ Solenoid
        DatabaseHelper.updateLockerUnlockTime(lockerNumber, unlockTime);
        DatabaseHelper.deleteLockerUsage(lockerNumber);
    }

    private void lockLocker(Rectangle rect, Text statusText, int lockerNumber) {
        LockerData inputData = showTwoStageKeypadInput("Lock Locker " + lockerNumber, lockerNumber);
        if (inputData == null) {
            System.out.println("❌ Input data is null. User canceled input.");
            return;
        }

        // Debug: Check input contents
        System.out.println("🎯 Received input:");
        System.out.println("Locker: " + lockerNumber);
        System.out.println("Student Year: " + inputData.studentYear);
        System.out.println("Student ID: " + inputData.studentId);
        System.out.println("Passcode: " + inputData.passcode);

        if (inputData.studentYear.isEmpty() || inputData.studentId.isEmpty() || inputData.passcode.isEmpty()) {
            System.out.println("❌ One or more input fields are empty. Aborting.");
            showError("Please complete all fields.");
            return;
        }

        int lockCharge = SettingsManager.getLockCharge();
        String redirectUrl = PaymentHandler.initiatePayment(lockCharge);
        if (redirectUrl == null) {
            showError("Failed to initiate payment.");
            return;
        }

        WebViewPopup.show(redirectUrl, () -> {
            Platform.runLater(() -> {
                lockerDataMap.put(lockerNumber, inputData);
                lockStartTimes.put(lockerNumber, LocalDateTime.now());

             // 🕒 Start periodic stop_time updater
                Timeline stopTimeHeartbeat = new Timeline(
                    new KeyFrame(Duration.seconds(10), event -> {
                        DatabaseHelper.updateLockerStopTime(lockerNumber, LocalDateTime.now());
                    })
                );
                stopTimeHeartbeat.setCycleCount(Timeline.INDEFINITE);
                stopTimeHeartbeat.play();
                lockTimers.put(lockerNumber, stopTimeHeartbeat);  // ✅ reuse lockTimers map

                // Final debug before DB insert
                System.out.println("🚀 Attempting to insert lock event into DB...");
                DatabaseHelper.insertLockEvent(
                    lockerNumber,
                    inputData.studentYear,
                    inputData.studentId,
                    inputData.passcode,
                    lockStartTimes.get(lockerNumber)
                );

                Text timerText = timerTexts.get(lockerNumber);
                if (timerText != null) {
                    timerText.setVisible(true);
                    Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                        LocalDateTime now = LocalDateTime.now();
                        java.time.Duration elapsed = java.time.Duration.between(lockStartTimes.get(lockerNumber), now);
                        long hours = elapsed.toHours();
                        long minutes = elapsed.toMinutes() % 60;
                        long seconds = elapsed.getSeconds() % 60;
                        timerText.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                    }));
                    timeline.setCycleCount(Timeline.INDEFINITE);
                    timeline.play();
                    lockTimers.put(lockerNumber, timeline);
                }

                statusText.setText("LOCKED");
                statusText.setFill(Color.RED);
                rect.setStroke(Color.RED);

            //    Pi4JTest.triggerLockerLED(lockerNumber); // ✅ Solenoid
            });
        });
    }

    private void unlockLocker(Rectangle rect, Text statusText, int lockerNumber) {
        LockerData storedData = lockerDataMap.get(lockerNumber);
        if (storedData == null) return;

        LockerData inputData = showTwoStageKeypadInput("Unlock Locker " + lockerNumber, lockerNumber);
        if (inputData == null) return;

        boolean credentialsMatch = storedData.studentYear.equals(inputData.studentYear)
                && storedData.studentId.equals(inputData.studentId)
                && storedData.passcode.equals(inputData.passcode);

        if (!credentialsMatch) {
            showError("Incorrect details! Please try again.");
            return;
        }

        LocalDateTime startTime = lockStartTimes.get(lockerNumber);
        if (startTime == null) startTime = LocalDateTime.now();

        long totalMinutes = ChronoUnit.MINUTES.between(startTime, LocalDateTime.now());
     
        int freeMin = SettingsManager.getFreeMinutes();
        int minsPerBlock = SettingsManager.getMinutesPerBlock();
        int chargePerBlock = SettingsManager.getChargePerBlock();

        if (totalMinutes < freeMin) {
            // ✅ Free unlock
          //  Pi4JTest.triggerLockerLED(lockerNumber); // Unlock solenoid

            statusText.setText("AVAILABLE");
            statusText.setFill(Color.GREEN);
            rect.setStroke(Color.GREEN);
            lockerDataMap.remove(lockerNumber);
            lockStartTimes.remove(lockerNumber);

            Timeline timeline = lockTimers.get(lockerNumber);
            if (timeline != null) {
                timeline.stop();
                lockTimers.remove(lockerNumber);
            }

            Text timerText = timerTexts.get(lockerNumber);
            if (timerText != null) {
                timerText.setVisible(false);
                timerText.setText("");
            }

            LocalDateTime unlockTime = LocalDateTime.now();
            //  Pi4JTest.triggerLockerLED(lockerNumber); // Unlock solenoid

            DatabaseHelper.updateLockerUnlockTime(lockerNumber, unlockTime);
        	DatabaseHelper.deleteLockerUsage(lockerNumber);

        } else {
            long overtime = totalMinutes - freeMin;
            long blocks = (long) Math.ceil((double) overtime / minsPerBlock);
            int amount = (int)(blocks * chargePerBlock);

            if (overtime <= 0) {
                unlockWithoutPayment(rect, statusText, lockerNumber);
                return;
            }

            String redirectUrl = PaymentHandler.initiatePayment(amount);
            if (redirectUrl == null) {
                showError("Failed to initiate overtime payment.");
                return;
            }

            WebViewPopup.show(redirectUrl, () -> {
                Platform.runLater(() -> {
                    statusText.setText("AVAILABLE");
                    statusText.setFill(Color.GREEN);
                    rect.setStroke(Color.GREEN);
                    lockerDataMap.remove(lockerNumber);
                    lockStartTimes.remove(lockerNumber);

                    Timeline timeline = lockTimers.get(lockerNumber);
                    if (timeline != null) {
                        timeline.stop();
                        lockTimers.remove(lockerNumber);
                    }

                    Text timerText = timerTexts.get(lockerNumber);
                    if (timerText != null) {
                        timerText.setVisible(false);
                        timerText.setText("");
                    }

                    LocalDateTime unlockTime = LocalDateTime.now();
                    DatabaseHelper.updateLockerUnlockTime(lockerNumber, unlockTime);
                    DatabaseHelper.deleteLockerUsage(lockerNumber);
                });
            });
        }
    }

    // Modified to also accept the locker number and check confirmation.
    private LockerData showTwoStageKeypadInput(String title, int lockerNumber) {
        while (true) {
            // Step 1: Student Year & ID input
            String[] userCredentials = showCredentialsInput(title);
            if (userCredentials == null) return null;

            String studentId = userCredentials[0];
            String studentYear = userCredentials[1];

            // ✅ Validate Student Year
            try {
                int year = Integer.parseInt(studentYear);
                if (year < 2010 || year > 2025) {
                    showError("Student Year must be between 2010 and 2025.");
                    continue;
                }
            } catch (NumberFormatException ex) {
                showError("Invalid Student Year format.");
                continue;
            }

            // ✅ Validate Student ID
            if (studentId.length() != 6) {
                showError("Student ID must be exactly 6 digits.");
                continue;
            }

            // Step 2: Passcode input
            while (true) {
                String passcode = showPasscodeInput(title);
                if (passcode == null) return null; // ⛔ user cancelled or timed out

                // Step 3: Confirmation stage
                Boolean confirmed = showConfirmationStage(lockerNumber, studentYear, studentId, passcode);

                if (confirmed == null) {
                    return null; // ⛔ confirmation timed out
                } else if (confirmed) {
                    return new LockerData(studentYear, studentId, passcode); // ✅ Done!
                } else {
                    // 🔁 Back button pressed on confirmation, return to passcode input
                    continue;
                }
            }
        }
    }



    private String[] showCredentialsInput(String title) {
        Stage keypadStage = new Stage(StageStyle.TRANSPARENT);

        TextField studentYearField = new TextField();
        TextField studentIdField = new TextField();

        studentYearField.setEditable(false);
        studentIdField.setEditable(false);
        studentYearField.setAlignment(Pos.CENTER);
        studentIdField.setAlignment(Pos.CENTER);

        configureTextField(studentYearField, 4, "Year");
        configureTextField(studentIdField, 6, "Student ID");

        studentYearField.setPrefSize(160, 50);
        studentIdField.setPrefSize(160, 50);

        studentYearField.setStyle(
            "-fx-font-size: 22px; -fx-font-weight: bold;" +
            "-fx-border-radius: 8px; -fx-background-radius: 8px;" +
            "-fx-border-color: #bfa033; -fx-border-width: 2px;" +
            "-fx-text-fill: #333;"
        );
        studentIdField.setStyle(
            "-fx-font-size: 22px; -fx-font-weight: bold;" +
            "-fx-border-radius: 8px; -fx-background-radius: 8px;" +
            "-fx-border-color: #bfa033; -fx-border-width: 2px;" +
            "-fx-text-fill: #333;"
        );

        HBox inputFields = new HBox(15, studentYearField, studentIdField);
        inputFields.setAlignment(Pos.CENTER);
        inputFields.setPadding(new Insets(10));

        VBox inputWrapper = new VBox(inputFields);
        inputWrapper.setAlignment(Pos.CENTER);

        Text titleText = new Text("Enter Student Year & ID");
        titleText.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-fill: #333;");
        VBox titleBox = new VBox(titleText);
        titleBox.setAlignment(Pos.CENTER);

        activeField = studentYearField;

        // 🕒 Auto-close timer
        Timeline autoClose = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (keypadStage.isShowing()) {
                keypadStage.close();
            }
        }));
        autoClose.setCycleCount(1);
        autoClose.play();

        // Keypad with auto-close tracking
        GridPane keypad = createKeypad(studentYearField, studentIdField, null, keypadStage, true, autoClose);

        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle(
            "-fx-font-size: 18px; -fx-font-weight: bold;" +
            "-fx-background-color: #e53935; -fx-text-fill: white;" +
            "-fx-background-radius: 10px; -fx-border-radius: 10px;" +
            "-fx-padding: 10px 30px;"
        );
        cancelButton.setOnAction(e -> {
            studentYearField.setText("");
            studentIdField.setText("");
            keypadStage.close();
        });

        HBox buttonBox = new HBox(cancelButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        VBox root = new VBox(5, titleBox, inputWrapper, keypad, buttonBox);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(25));
        root.setStyle(
            "-fx-background-color: #fff8e1;" +
            "-fx-border-color: #bfa033;" +
            "-fx-border-width: 3px;" +
            "-fx-border-radius: 20px;" +
            "-fx-background-radius: 20px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.3, 0, 4);"
        );

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 480, 520);
        scene.setFill(Color.TRANSPARENT);
        keypadStage.setScene(scene);
        keypadStage.setTitle(title);
        keypadStage.initModality(Modality.APPLICATION_MODAL);

        keypadStage.showAndWait();

        if (!studentYearField.getText().isEmpty() && !studentIdField.getText().isEmpty()) {
            return new String[]{studentIdField.getText(), studentYearField.getText()};
        }

        return null;
    }


    private String showPasscodeInput(String title) {
        Stage keypadStage = new Stage(StageStyle.TRANSPARENT);
        TextField passcodeField = new TextField();
        passcodeField.setEditable(false);
        passcodeField.setAlignment(Pos.CENTER);
        activeField = passcodeField;

        final boolean[] timedOut = {false};

        configureTextField(passcodeField, 4, "PASSCODE");

        passcodeField.setPrefSize(300, 50);
        passcodeField.setStyle(
            "-fx-font-size: 22px; -fx-font-weight: bold;" +
            "-fx-border-radius: 8px; -fx-background-radius: 8px;" +
            "-fx-border-color: #bfa033; -fx-border-width: 2px;" +
            "-fx-text-fill: #333;"
        );

        Text titleText = new Text("Enter 4-Digit Passcode");
        titleText.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-fill: #333;");
        VBox titleBox = new VBox(titleText);
        titleBox.setAlignment(Pos.CENTER);
        VBox.setMargin(titleBox, new Insets(5, 0, 10, 0));

        HBox passcodeBox = new HBox(passcodeField);
        passcodeBox.setAlignment(Pos.CENTER);

        // 🕒 Auto-close after 30s of inactivity
        Timeline autoClose = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (keypadStage.isShowing()) {
                timedOut[0] = true;
                keypadStage.close();
            }
        }));
        autoClose.setCycleCount(1);
        autoClose.play();

        GridPane keypad = createKeypad(null, null, passcodeField, keypadStage, false, autoClose);

        Button backButton = new Button("↩ Back");
        backButton.setStyle(
            "-fx-font-size: 18px; -fx-font-weight: bold;" +
            "-fx-background-color: #e53935; -fx-text-fill: white;" +
            "-fx-background-radius: 10px; -fx-border-radius: 10px;" +
            "-fx-padding: 10px 30px;"
        );
        backButton.setOnAction(e -> {
            passcodeField.setText("");
            keypadStage.close();
        });

        HBox buttonBox = new HBox(backButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        VBox root = new VBox(5, titleBox, passcodeBox, keypad, buttonBox);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(25));
        root.setStyle(
            "-fx-background-color: #fff8e1;" +
            "-fx-border-color: #bfa033;" +
            "-fx-border-width: 3px;" +
            "-fx-border-radius: 20px;" +
            "-fx-background-radius: 20px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.3, 0, 4);"
        );

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 480, 520);
        scene.setFill(Color.TRANSPARENT);
        keypadStage.setScene(scene);
        keypadStage.setTitle(title);
        keypadStage.initModality(Modality.APPLICATION_MODAL);
        keypadStage.showAndWait();

        if (timedOut[0]) {
            return null;
        }

        if (passcodeField.getText().length() == 4) {
            return passcodeField.getText();
        } else {
            return null;
        }
    }



    private GridPane createKeypad(TextField studentIdField, TextField studentYearField, TextField passcodeField, Stage stage, boolean isFirstStage, Timeline autoClose)
 {
        GridPane keypad = new GridPane();
        keypad.setHgap(1);
        keypad.setVgap(1);
        keypad.setPadding(new Insets(1));
        keypad.setAlignment(Pos.CENTER);

        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"DEL", "0", "OK"}};

        for (int row = 0; row < keys.length; row++) {
            for (int col = 0; col < keys[row].length; col++) {
                final int r = row;
                final int c = col;
                Button button = new Button(keys[r][c]);
                button.setMinSize(100, 70);
                button.setMaxSize(100, 70);
                button.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID,
                        new CornerRadii(5), new BorderWidths(3))));

                String baseStyle = "-fx-font-size: 22px; -fx-background-radius: 10px; -fx-background-insets: 1; -fx-font-weight: bold;";
                String style;
                if (keys[r][c].equals("DEL")) {
                    style = "-fx-font-size: 22px; -fx-background-color: #ffcc00; -fx-background-radius: 10px; -fx-background-insets: 1; -fx-font-weight: bold;";
                } else if (keys[r][c].equals("OK")) {
                    style = "-fx-font-size: 22px; -fx-background-color: #66bb6a; -fx-text-fill: white; -fx-background-radius: 10px; -fx-background-insets: 1; -fx-font-weight: bold;";
                } else {
                    style = baseStyle + " -fx-background-color: white;";
                }
                button.setStyle(style);

                if (isFirstStage)
                    button.setOnAction(e -> handleFirstStageKeypadInput(keys[r][c], studentIdField, studentYearField, stage));
                else
                    button.setOnAction(e -> handleSecondStageKeypadInput(keys[r][c], passcodeField, stage));

                keypad.add(button, c, row);
            }
        }
        return keypad;
    }


    // Updated setupStage to use rounded corners.
    private void setupStage(VBox root, Stage stage) {
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        CornerRadii radii = new CornerRadii(5);
        BackgroundFill backgroundFill = new BackgroundFill(Color.web("#d4af37"), radii, new Insets(3));
        root.setBackground(new Background(backgroundFill));
        root.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, radii, new BorderWidths(5))));
        
        // Apply a clip to achieve rounded corners.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        clip.setArcWidth(15);
        clip.setArcHeight(15);
        root.setClip(clip);
    }

    private void configureTextField(TextField textField, int maxLength, String promptText) {
        textField.setPromptText(promptText);
        textField.setStyle("-fx-font-size: 24px; -fx-pref-width: 200px;");
        
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                textField.setText(oldValue);  // Only allow digits
            }
            if (newValue.length() > maxLength) {
                textField.setText(oldValue);  // Limit to maxLength
            }
        });
    }

    private void handleFirstStageKeypadInput(String input, TextField studentYearField, TextField studentIdField, Stage stage) {
        if (input.equals("OK")) {
            if (!studentYearField.getText().isEmpty() && !studentIdField.getText().isEmpty()) {
                stage.close();
            }
            return;
        }
        if (input.equals("DEL")) {
            // If active field is Student ID and it's empty, switch to Student Year
            if (activeField == studentIdField && studentIdField.getText().isEmpty()) {
                activeField = studentYearField;
            } 
            // If active field is Student Year and it's empty, keep it on Student Year
            // (No switching to ensure we always start at Student Year when empty)
            
            // Remove the last character from whichever field is active (if not already empty)
            if (activeField != null && !activeField.getText().isEmpty()) {
                activeField.setText(activeField.getText().substring(0, activeField.getText().length() - 1));
            }
            return;
        }
        if (activeField != null) {
            activeField.setText(activeField.getText() + input);
            // Automatically switch from Student Year to Student ID if Student Year has reached its max length (4 digits).
            if (activeField == studentYearField && studentYearField.getText().length() == 4) {
                activeField = studentIdField;
            }
        }
    }

    private void handleSecondStageKeypadInput(String input, TextField passcodeField, Stage stage) {
        if (input.equals("OK")) {
            if (!passcodeField.getText().isEmpty() && passcodeField.getText().length() == 4) {
                stage.close();
            }
            return;
        }
        if (input.equals("DEL")) {
            if (!passcodeField.getText().isEmpty()) {
                passcodeField.setText(passcodeField.getText().substring(0, passcodeField.getText().length() - 1));
            }
            return;
        }
        if (passcodeField != null) {
            passcodeField.setText(passcodeField.getText() + input);
        }
    }

    // New confirmation stage that displays locker number, Student Year, Student ID, and passcode.
    // It now includes both a Confirm and a Back button.
    private Boolean showConfirmationStage(int lockerNumber, String studentYear, String studentId, String passcode) {
        Stage confirmationStage = new Stage(StageStyle.TRANSPARENT);
        final boolean[] confirmed = {false};
        final boolean[] timedOut = {false}; // ⏱️ timeout flag

        Text lockerTitle = new Text("LOCKER " + lockerNumber);
        lockerTitle.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-fill: #333;");
        lockerTitle.setWrappingWidth(260);
        lockerTitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox headerBox = new VBox(lockerTitle);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(10, 0, 10, 0));

        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(10);
        detailsGrid.setVgap(12);
        detailsGrid.setAlignment(Pos.CENTER_LEFT);

        String labelStyle = "-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #444;";
        String valueStyle = "-fx-font-size: 18px; -fx-text-fill: #000;";

        detailsGrid.addRow(0, createLabel("Student Year:", labelStyle), createLabel(studentYear, valueStyle));
        detailsGrid.addRow(1, createLabel("Student ID:", labelStyle), createLabel(studentId, valueStyle));
        detailsGrid.addRow(2, createLabel("Passcode:", labelStyle), createLabel(passcode, valueStyle));

        Button confirmButton = new Button("✅ Ok");
        confirmButton.setStyle(
            "-fx-font-size: 18px; -fx-font-weight: bold;" +
            "-fx-background-color: #4CAF50; -fx-text-fill: white;" +
            "-fx-background-radius: 12px; -fx-border-radius: 12px;" +
            "-fx-padding: 10px 30px;"
        );
        confirmButton.setOnAction(e -> {
            confirmed[0] = true;
            confirmationStage.close();
        });

        Button backButton = new Button("↩ Back");
        backButton.setStyle(
            "-fx-font-size: 18px; -fx-font-weight: bold;" +
            "-fx-background-color: #e53935; -fx-text-fill: white;" +
            "-fx-background-radius: 12px; -fx-border-radius: 12px;" +
            "-fx-padding: 10px 30px;"
        );
        backButton.setOnAction(e -> confirmationStage.close());

        HBox buttonBox = new HBox(20, backButton, confirmButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        VBox root = new VBox(20, headerBox, detailsGrid, buttonBox);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(25));
        root.setStyle(
            "-fx-background-color: #fff8e1;" +
            "-fx-border-color: #bfa033;" +
            "-fx-border-width: 3px;" +
            "-fx-border-radius: 20px;" +
            "-fx-background-radius: 20px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.3, 0, 4);"
        );

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        clip.setArcWidth(40);
        clip.setArcHeight(40);
        root.setClip(clip);

        Scene scene = new Scene(root, 350, 320);
        scene.setFill(Color.TRANSPARENT);
        confirmationStage.setScene(scene);
        confirmationStage.initStyle(StageStyle.TRANSPARENT);
        confirmationStage.initModality(Modality.APPLICATION_MODAL);
        confirmationStage.setTitle("Confirmation");

        // ⏱️ Auto-close after 30 seconds of inactivity
        Timeline autoClose = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (confirmationStage.isShowing()) {
                timedOut[0] = true;
                confirmationStage.close();
            }
        }));
        autoClose.setCycleCount(1);
        autoClose.play();

        confirmationStage.showAndWait();

        // ⛔ If inactive, do not go back to passcode input
        if (timedOut[0]) return null;
        return confirmed[0] ? Boolean.TRUE : Boolean.FALSE;

    }


    private Text createLabel(String text, String style) {
        Text label = new Text(text);
        label.setStyle(style);
        return label;
    }


    private void showError(String message) {
        Stage errorStage = new Stage();
        errorStage.initStyle(StageStyle.UNDECORATED);
        errorStage.setAlwaysOnTop(true);
        errorStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(400);
        messageLabel.setPrefWidth(400); // makes it fill horizontally
        messageLabel.setAlignment(Pos.CENTER); // centers content inside the label
        messageLabel.setStyle(
            "-fx-text-fill: #b71c1c;" +
            "-fx-font-size: 24px;" +
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-weight: bold;"
        );

        messageLabel.setMaxWidth(400);

        Button okButton = new Button("OK");
        okButton.setStyle(
            "-fx-background-color: #e53935;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 28px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 12px;" +
            "-fx-border-radius: 12px;" +
            "-fx-padding: 20px 40px;"
        );
        okButton.setOnAction(e -> errorStage.close());

        VBox layout = new VBox(50, messageLabel, okButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(40));
        layout.setStyle(
            "-fx-background-color: #fff0f0;" +
            "-fx-border-color: #e53935;" +
            "-fx-border-width: 3px;" +
            "-fx-border-radius: 15px;" +
            "-fx-background-radius: 15px;"
        );

        Scene scene = new Scene(layout, 500, 330);
        errorStage.setScene(scene);

        // ⏱ Auto-close after 30 seconds — works even with showAndWait()
        new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (errorStage.isShowing()) {
                errorStage.close();
            }
        })).play();

        errorStage.showAndWait(); // ✅ blocks everything until closed
    }


}