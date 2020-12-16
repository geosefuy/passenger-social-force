package sample;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.*;

public class Controller {
    private final DrawState[] stateSequences = new DrawState[]{
            DrawState.SPAWN,
            DrawState.CHECKPOINT,
            DrawState.DESPAWN,
            DrawState.OBSTACLE,
            DrawState.FLOOR_FIELDS
    };

    private boolean hasStarted;
    private boolean isDrawingFloorFields;
    private DrawState drawState;
    private int sequence;
    private int index;
    private int modeIndex;

    private Orientation orientation;
    private Rectangle currentExtraRectangle;
    private Rectangle previousExtraRectangle;
    private boolean isValidDraw;

    @FXML
    private Canvas canvas;

    @FXML
    private Button startButton;

    @FXML
    private Button nextButton;

    @FXML
    private Button stepButton;

    @FXML
    private Text promptText;

    @FXML
    private Pane overlay;

    @FXML
    private StackPane stackPane;

    @FXML
    private ToggleButton spawnButton;

    @FXML
    private ToggleButton trainDoorsOpenButton;

    @FXML
    private Label stateLabel;

    @FXML
    private Label targetLabel;

    @FXML
    private Label drawLabel;

    @FXML
    private Button loadButton;

    @FXML
    private Button saveButton;

    @FXML
    private ChoiceBox<String> floorFieldChoiceBox;

    @FXML
    private ChoiceBox<String> targetChoiceBox;

    @FXML
    private ChoiceBox<String> drawChoiceBox;

    private double tileSize;
    private GraphicsContext graphicsContext;
    private List<String[]> stringChoices;

    private int numAgents;

    public Controller() {
        this.hasStarted = false;
        this.isDrawingFloorFields = false;

        this.orientation = Orientation.UP;
        this.previousExtraRectangle = null;
        this.currentExtraRectangle = null;
        this.isValidDraw = true;
    }

    @FXML
    private void initialize() {
        tileSize = canvas.getHeight() / Main.WALKWAY.getRows();
        graphicsContext = canvas.getGraphicsContext2D();

        List<String> floorFieldChoicesList = new ArrayList<>();

        floorFieldChoicesList.add("Queueing area");
        floorFieldChoicesList.add("Train waiting area");

        List<String> stateList = new ArrayList<>(floorFieldChoicesList);

        Object[] stateListArray = stateList.toArray();

        floorFieldChoicesList.add(0, "None");

        Object[] floorFieldChoicesArray = floorFieldChoicesList.toArray();

        String[] floorFieldChoices = Arrays.copyOf(
                floorFieldChoicesArray,
                floorFieldChoicesArray.length,
                String[].class);

        floorFieldChoiceBox.setItems(FXCollections.observableArrayList(floorFieldChoices));

        floorFieldChoiceBox.getSelectionModel().selectedIndexProperty().addListener(
                (observableValue, number, number2) -> drawInterface(graphicsContext, tileSize)
        );

        final String[] startItems = {"Spawn point"};
        final String[] checkpointItems = {"Security entrance", "Ticket booth", "Turnstile"};
        final String[] goalItems = {"Despawn point"};
        final String[] floorFieldItems = Arrays.copyOf(stateListArray, stateListArray.length, String[].class);
        final String[] obstacleItems = {"Obstacle"};

        stringChoices = new ArrayList<>();

        stringChoices.add(startItems);
        stringChoices.add(checkpointItems);
        stringChoices.add(goalItems);
        stringChoices.add(obstacleItems);
        stringChoices.add(floorFieldItems);

        // Chronological number of the goal
        this.sequence = -1;

        // The index of an optional goal within a chronological position
        this.index = -1;

        // The index of the mode
        this.modeIndex = 0;

        this.drawState = stateSequences[modeIndex];

        floorFieldChoiceBox.getSelectionModel().select(0);

        drawChoiceBox.setItems(FXCollections.observableArrayList(stringChoices.get(modeIndex)));
        drawChoiceBox.getSelectionModel().select(0);

        // Draw visible grid
//        graphicsContext.setFill(Color.TURQUOISE);
//        graphicsContext.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Set labels
        stateLabel.setLabelFor(floorFieldChoiceBox);
        targetLabel.setLabelFor(targetChoiceBox);
        drawLabel.setLabelFor(drawChoiceBox);

        drawInterface(graphicsContext, tileSize);
        drawListeners(startItems, tileSize);
    }

    @FXML
    private void step() {
        nextStep();

        drawInterface(graphicsContext, tileSize);
    }

    @FXML
    private void next() {
        nextMode();

        this.drawState = stateSequences[modeIndex];

        drawChoiceBox.setItems(FXCollections.observableArrayList(stringChoices.get(modeIndex)));
        drawChoiceBox.getSelectionModel().select(0);

        drawInterface(graphicsContext, tileSize);
    }

    @FXML
    private void play() {
        this.hasStarted = true;
        stackPane.getChildren().remove(overlay);

//        // Diffuse goals
//        Main.WALKWAY.diffuseGoals();

        // For each state, normalize the floor field values
        PassengerMovement.State[] stateArray = PassengerMovement.State.values();

        // TODO: Normalize other states
        for (PassengerMovement.State state : stateArray) {
            if (state == PassengerMovement.State.IN_QUEUE) {
                Main.WALKWAY.normalizeFloorFields(state);
            }
        }

        // Take note of the passengers removed from the simulation
        List<Passenger> passengersRemoved = new ArrayList<>();

        // Start running
        // Prepare the random number generator
        Random rng = new Random();

        // 10% chance of passengers being generated
        final double CHANCE_PER_TICK = 0.1;

        new Thread(() -> {
            while (true) {
                // Make the starting patches randomly generate passengers
                // But only do it when there is no passenger at the start patch
                for (Patch start : Main.WALKWAY.getStarts()) {
                    if (spawnButton.isSelected() && rng.nextDouble() < CHANCE_PER_TICK) {
                        generatePassenger(start);
                    }
                }

                // Update the number of agents displayed in the interface
                updateNumAgents();

                // Make each passenger move
                for (Passenger passenger : Main.WALKWAY.getPassengers()) {
                    PassengerMovement passengerMovement = passenger.getPassengerMovement();

                    // Only move if the passenger is not waiting
                    if (!passengerMovement.isWaiting()) {
                        double headingGoal;

                        // Set the nearest goal to this passenger
                        // This internally computes for the nearest goal to it
                        passengerMovement.setNearestGoal();

//                        // Take note of the heading towards the goal patch
//                        // Get the x and y coordinates of the patch in question
//                        // These coordinates should be in the center of the patch
                        headingGoal = passenger.getPassengerMovement().headingTowards(
                                passenger.getPassengerMovement().getGoal().getPatchCenterCoordinates()
                        );

                        // Get the nearest patch with a floor field value greater than a certain threshold
                        final double threshold = 0.0;

                        Patch nearestPatchAboveThreshold = passengerMovement.nearestPatchAboveThreshold(threshold);

                        // Then take note of the heading towards that patch
                        double headingQueueArea = passengerMovement.headingTowards(
                                nearestPatchAboveThreshold.getPatchCenterCoordinates()
                        );

                        // Retrieve the state and action of the current passenger
                        PassengerMovement.State state = passengerMovement.getState();
                        PassengerMovement.Action action = passengerMovement.getAction();

                        // If the passenger is walking, check if it has entered
                        // a queueing area
                        // TODO: Check if the passenger has entered other states
                        //  Also make sure that the state to be entered is consistent with the part of the passenger's
                        //  journey (e.g., the passenger in a platform should already have gotten its ticket)
                        if (state == PassengerMovement.State.WALKING) {
                            Patch currentPatch = passengerMovement.getCurrentPatch();

                            // Check if the passenger has stepped on a floor field associated with its goal
                            // If yes, check for the state of the floor field, then enter that state
                            double floorFieldValue = currentPatch.getFloorFieldValues().get(
                                    PassengerMovement.State.IN_QUEUE
                            ).getValue();

                            if (floorFieldValue > 0.0
                                    && currentPatch.getFloorFieldValues().get(PassengerMovement.State.IN_QUEUE)
                                    .getAssociation() == passengerMovement.getGoal()) {
                                passengerMovement.setState(PassengerMovement.State.IN_QUEUE);
                                passengerMovement.setAction(PassengerMovement.Action.QUEUEING);
                            }
                        }

                        // Queueing behavior
                        if (state == PassengerMovement.State.IN_QUEUE) {
                            // If the passenger is queueing, also take the heading towards the passenger at the tail of the
                            // queue into account, as well as the floor fields
                            Patch goal = passengerMovement.getGoal();

                            // Use the highest neighboring patch with the highest floor field to influence the
                            // heading of this passenger
                            Patch bestPatch = Main.WALKWAY.chooseBestNeighboringPatch(
                                    Main.WALKWAY.getPatch(passengerMovement.getFuturePosition(
                                            goal, headingGoal
                                    )),
                                    headingGoal,
                                    state
                            );

                            // Get the heading toward the best patch
                            double headingBestPatch = passengerMovement.headingTowards(
                                    bestPatch.getPatchCenterCoordinates()
                            );

                            Queue<Passenger> queueAtGoal = goal.getPassengersQueueing();

                            // Check if there is someone queueing for this passenger's chosen goal
                            // If the queue is not empty, but contains this passenger, and that passenger is the head
                            // of the queue, then be the leader of the queue
                            if (queueAtGoal.isEmpty()
                                    || queueAtGoal.contains(passenger) && queueAtGoal.peek() == passenger) {
                                // Leader role: use the floor fields ahead to find the way to the goal
                                // Face towards the best patch
                                face(passenger, null, headingBestPatch);

                                // If the queue was empty, add this passenger to the queue
                                if (queueAtGoal.isEmpty()) {
                                    queueAtGoal.add(passenger);
                                }
                            } else {
                                // If the queue is not empty, and this passenger is not yet in that queue, join the
                                // queue as a follower
                                // Follower role: use the floor fields behind the passenger at the tail of the queue of
                                // the pursued goal
                                // Get the passenger at the tail of the queue, if it hasn't gotten it yet already
                                Passenger passengerAtTail = passengerMovement.getFollowed();

                                if (passengerAtTail == null) {
                                    passengerAtTail = goal.getPassengersQueueing().peekFirst();
                                }

                                assert passengerAtTail != null;

                                // Get the heading towards that passenger
                                double headingTail = passengerMovement.headingTowards(
                                        passengerAtTail.getPassengerMovement().getPosition()
                                );

                                // Face towards the heading towards the tail of the queue
                                face(passenger, null, headingTail);
                            }

                            /*                        // Try to choose a leader if this passenger doesn't already have one
                        if (passenger.getPassengerMovement().getLeader() == null) {
                            // Try to choose a leader
                            boolean leaderChosen = passenger.getPassengerMovement().setLeader();

                            // If a leader has been chosen, take note of the heading to that leader
                            if (leaderChosen) {
                                Passenger leader = passenger.getPassengerMovement().getLeader();

                                // Face towards the angular mean of the headings toward the leader and the goal
                                face(passenger, leader, headingGoal);
                            } else {
                                // No leader has been chosen, continue with the passenger's own knowledge of the
                                // position of the goal
                                face(passenger, null, headingGoal);
                            }
                        } else {
                            // If the passenger already has a leader, continue with the passenger's knowledge of the
                            // positions of the goal and its leader
                            Passenger leader = passenger.getPassengerMovement().getLeader();

                            face(passenger, leader, headingGoal);
                        }

                            // Take note of the heading towards the patch with the highest gradient
                            headingBestPatch = passenger.headingTowards(chosenPatch);

                         Set the heading to the mean of the above headings
                            double meanHeading = Passenger.meanHeading(headingGoal, headingBestPatch);

                            passenger.setHeading(headingGoal);*/


                        } else if (state == PassengerMovement.State.AT_PLATFORM) {
                            // TODO: Train platform behavior
                        } else {
                            // Walking behavior
                            // Just face the goal
                            face(passenger, null, headingQueueArea);
                        }

                        // Make this passenger move, if allowable
                        // Within a certain number of tries, randomly perturb the heading, then try moving again
                        // If the tries are exhausted, don't move at all
                        final int totalTries = 1;

                            /*                            for (int tries = 0; tries < totalTries; tries++) {
                                if (passengerMovement.shouldMove(2.0, Math.toRadians(30.0))) {
                                    passengerMovement.move();

                                    break;
                                } else {
                                    passengerMovement.setHeading(
                                            (passengerMovement.getHeading()
                                                    + new Random().nextGaussian() * Math.toRadians(20.0))
                                    );
                                }
                            }*/

                        if (passengerMovement.shouldMove(1.5, Math.toRadians(30.0))) {
                            passengerMovement.move();
                        }

//                            // Every movement, check if the leader, if it still exists, is still ahead
//                            if (passengerMovement.getLeader() != null
//                                    && !passengerMovement.isWithinFieldOfView(
//                                    passengerMovement.getLeader(), Math.toRadians(20.0)
//                            )) {
//                                // If not, remove it as a leader
//                                passengerMovement.clearLeader();
//                            }
                    }

                    // Check if the passenger is at its goal
                    if (Main.WALKWAY.checkGoal(passenger)) {
                        // Check if the goal the passenger is on allows this passenger to pass
                        if (Main.WALKWAY.checkPass(passenger, trainDoorsOpenButton.isSelected())) {
                            // If it is, increment its goals reached counter
                            passengerMovement.reachGoal();

                            // Remove the passenger from the queue of its goal, if that goal has a queue
                            if (passengerMovement.getGoal().getPassengersQueueing() != null) {
                                passengerMovement.getGoal().getPassengersQueueing().remove();
                            }

                            // Restore the status and action of the passenger
                            passengerMovement.setState(PassengerMovement.State.WALKING);
                            passengerMovement.setAction(PassengerMovement.Action.WILL_QUEUE);

                            // If it has no more goals left, this passenger should be removed
                            if (passengerMovement.getGoalsLeft() == 0) {
                                passengersRemoved.add(passenger);
                            }

                            // Allow the passenger to move again
                            passengerMovement.setWaiting(false);
                        } else {
                            // Do not allow the passenger to move
                            passengerMovement.setWaiting(true);
                        }
                    }
                }

                // Remove all passengers in goals
                for (Passenger removedPassenger : passengersRemoved) {
//                        // Remove the passenger from the world
//                        Main.WALKWAY.removePassenger(passenger);

                    // Remove the passenger from its current patch
                    removedPassenger.getPassengerMovement().getCurrentPatch().getPassengers().remove(removedPassenger);

                    // Remove the passenger from the passengers list
                    Main.WALKWAY.getPassengers().remove(removedPassenger);

                    // TODO: Optimize
                    // Passengers whose leader is this removed passenger should also have their references to that
                    // leader cleared
//                    for (Passenger passenger : Main.WALKWAY.getPassengers()) {
//                        if (passenger.getPassengerMovement().getLeader() == removedPassenger) {
//                            passenger.getPassengerMovement().clearLeader();
//                        }
//                    }

                    this.numAgents--;
                }

                // Clear the list of passengers to be removed, as they have already been removed
                passengersRemoved.clear();

                // Update the number of agents displayed in the interface
                updateNumAgents();

                // Print the region
//                    Main.WALKWAY.printRegion(0);
                drawInterface(graphicsContext, tileSize);

                try {
                    Thread.sleep(Main.DELAY_IN_MS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

//                    System.out.println("clear");
            }
        }).start();
    }

    private void generatePassenger(Patch start) {
        // Generate a passenger
        Passenger passenger = new Passenger(
                start.getPatchCenterCoordinates().getX(),
                start.getPatchCenterCoordinates().getY(),
                Main.WALKWAY.getNumGoals()
        );

//                            // Position the newly created passenger in the coordinates of the start row and column
//                            Main.WALKWAY.positionPassenger(passenger, startRow, startColumn);

        // Add the newly created passenger to the list of passengers
        Main.WALKWAY.getPassengers().add(passenger);

        // Increment the current number of passengers
        this.numAgents++;
    }

    private void face(Passenger currentPassenger, Passenger leader, double headingGoal) {
        // If a leader was chosen, face towards the angular mean of the headings toward the leader and the goal
        if (leader != null) {
            double headingLeader = currentPassenger.getPassengerMovement().headingTowards(
                    leader.getPassengerMovement().getPosition()
            );

            // Set this passenger's final heading to the angular mean of the two headings
            double meanHeading = PassengerMovement.meanHeading(headingGoal, headingLeader);

            // Add random perturbations for realistic movement
            meanHeading += new Random().nextGaussian() * Math.toRadians(5);

            currentPassenger.getPassengerMovement().setHeading(meanHeading);
        } else {
            // No leader has been chosen, continue with the passenger's own knowledge of the
            // position of the goal
            // Add random perturbations for realistic movement
            headingGoal += new Random().nextGaussian() * Math.toRadians(5);

            // If a leader has not been chosen, continue moving solo
            currentPassenger.getPassengerMovement().setHeading(headingGoal);
        }
    }

    private void updateNumAgents() {
        Platform.runLater(() -> this.promptText.setText(this.numAgents + " agents"));
    }

    private void drawListeners(String[] items, double tileSize) {
        Platform.runLater(() -> {
            // Draw listeners for the canvas (used for the detection of the orientation)
            canvas.getScene().setOnKeyPressed(e -> {
                switch (e.getCode()) {
                    case W:
                        this.orientation = Orientation.UP;
//                        drawInterface(graphicsContext, tileSize);

                        break;
                    case D:
                        this.orientation = Orientation.RIGHT;
//                        drawInterface(graphicsContext, tileSize);

                        break;
                    case S:
                        this.orientation = Orientation.DOWN;
//                        drawInterface(graphicsContext, tileSize);

                        break;
                    case A:
                        this.orientation = Orientation.LEFT;
//                        drawInterface(graphicsContext, tileSize);

                        break;
                }
            });

            Rectangle[][] rectangles = new Rectangle[Main.WALKWAY.getRows()][Main.WALKWAY.getColumns()];

            // Draw listeners for each patch
            for (int row = 0; row < Main.WALKWAY.getRows(); row++) {
                for (int column = 0; column < Main.WALKWAY.getColumns(); column++) {
                    rectangles[row][column]
                            = new Rectangle(column * tileSize, row * tileSize, tileSize, tileSize);

                    Rectangle rectangle = rectangles[row][column];

                    rectangle.setFill(Color.DARKGRAY);
                    rectangle.setOpacity(0.0);
                    rectangle.setStyle("-fx-cursor: hand;");
                    rectangle.getProperties().put("row", row);
                    rectangle.getProperties().put("column", column);

                    final int columnCopy = column;
                    final int rowCopy = row;

                    rectangle.addEventFilter(MouseEvent.MOUSE_ENTERED, e -> {
                        rectangle.setOpacity(1.0);

                        if (drawState == DrawState.CHECKPOINT) {
                            Rectangle extraRectangle;

                            switch (this.orientation) {
                                case UP:
                                    if (rowCopy - 1 >= 0) {
                                        extraRectangle = rectangles[rowCopy - 1][columnCopy];
                                        extraRectangle.setOpacity(1.0);
                                        extraRectangle.setFill(Color.LIGHTGRAY);

                                        this.previousExtraRectangle = extraRectangle;
                                        this.isValidDraw = true;
                                    } else {
                                        this.isValidDraw = false;
                                    }

                                    break;
                                case RIGHT:
                                    if (columnCopy + 1 < Main.WALKWAY.getColumns()) {
                                        extraRectangle = rectangles[rowCopy][columnCopy + 1];
                                        extraRectangle.setOpacity(1.0);
                                        extraRectangle.setFill(Color.LIGHTGRAY);

                                        this.previousExtraRectangle = extraRectangle;
                                        this.isValidDraw = true;
                                    } else {
                                        this.isValidDraw = false;
                                    }

                                    break;
                                case DOWN:
                                    if (rowCopy + 1 < Main.WALKWAY.getRows()) {
                                        extraRectangle = rectangles[rowCopy + 1][columnCopy];
                                        extraRectangle.setOpacity(1.0);
                                        extraRectangle.setFill(Color.LIGHTGRAY);

                                        this.previousExtraRectangle = extraRectangle;
                                        this.isValidDraw = true;
                                    } else {
                                        this.isValidDraw = false;
                                    }

                                    break;
                                case LEFT:
                                    if (columnCopy - 1 >= 0) {
                                        extraRectangle = rectangles[rowCopy][columnCopy - 1];
                                        extraRectangle.setOpacity(1.0);
                                        extraRectangle.setFill(Color.LIGHTGRAY);

                                        this.previousExtraRectangle = extraRectangle;
                                        this.isValidDraw = true;
                                    } else {
                                        this.isValidDraw = false;
                                    }

                                    break;
                            }
                        }
                    });

                    rectangle.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
                        rectangle.setOpacity(0.0);

                        if (drawState == DrawState.CHECKPOINT) {
                            previousExtraRectangle.setOpacity(0.0);
                            previousExtraRectangle.setFill(Color.DARKGRAY);
                        }
                    });

                    rectangle.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                        int patchRow;
                        int patchColumn;

                        int extraPatchRow;
                        int extraPatchColumn;

                        patchRow = (int) rectangle.getProperties().get("row");
                        patchColumn = (int) rectangle.getProperties().get("column");

                        switch (drawState) {
                            case SPAWN:
                                Main.WALKWAY.setType(patchRow, patchColumn, Patch.Type.SPAWN, this.sequence);

                                break;
                            case CHECKPOINT:
                                if (isValidDraw) {
                                    extraPatchRow = (int) previousExtraRectangle.getProperties().get("row");
                                    extraPatchColumn = (int) previousExtraRectangle.getProperties().get("column");

                                    // TODO: There are more types that ticket booths
                                    Main.WALKWAY.setType(
                                            patchRow,
                                            patchColumn,
                                            Patch.Type.TICKET_BOOTH,
                                            this.sequence
                                    );

                                    Main.WALKWAY.setType(
                                            extraPatchRow,
                                            extraPatchColumn,
                                            Patch.Type.TRANSACTION_AREA,
                                            this.sequence
                                    );
                                }

                                break;
                            case DESPAWN:
                                Main.WALKWAY.setType(patchRow, patchColumn, Patch.Type.DESPAWN, this.sequence);

                                break;
                            case OBSTACLE:
                                Main.WALKWAY.setType(patchRow, patchColumn, Patch.Type.OBSTACLE, this.sequence);

                                break;
                            case FLOOR_FIELDS:
                                // TODO: Consider other floor fields
                                Main.WALKWAY.setType(patchRow, patchColumn, Patch.Type.CLEAR, this.sequence);

                                PassengerMovement.State state;

                                state = matchState(drawChoiceBox.getSelectionModel().getSelectedItem());

                                assert state != null;

                                Main.WALKWAY.setFloorField(
                                        patchRow,
                                        patchColumn,
                                        state,
                                        Main.WALKWAY.getGoalFromGoalId(
                                                targetChoiceBox.getSelectionModel().getSelectedItem()
                                        )
                                );

                                break;
                        }

                        // Redraw grid
                        drawInterface(graphicsContext, tileSize);
                    });

                    overlay.getChildren().add(rectangle);
                }
            }
        });
    }

    private PassengerMovement.State matchState(String selectedItem) {
        // Match the selected value at the checkbox with the appropriate state
        // TODO: Add more states
        switch (selectedItem) {
            case "Queueing area":
                return PassengerMovement.State.IN_QUEUE;
            case "Train waiting area":
                return PassengerMovement.State.AT_PLATFORM;
        }

        return null;
    }

    private void drawInterface(GraphicsContext graphicsContext, double tileSize) {
        Platform.runLater(() -> {
            graphicsContext.setFill(Color.WHITE);
            graphicsContext.setFont(Font.font(7.0));

            boolean isGateOrExit = false;

            for (int row = 0; row < Main.WALKWAY.getRows(); row++) {
                for (int column = 0; column < Main.WALKWAY.getColumns(); column++) {
                    Patch patch = Main.WALKWAY.getPatch(row, column);

                    switch (patch.getType()) {
                        case CLEAR:
                            PassengerMovement.State state = null;

                            isGateOrExit = false;

                            // Get the selected state
                            String selectedItemName = floorFieldChoiceBox.getSelectionModel().getSelectedItem();

                            if (!selectedItemName.equals("None")) {
                                state = matchState(selectedItemName);
                            }

                            if (state == PassengerMovement.State.IN_QUEUE) {
                                Color color;

                                // Show floor field value
                                double floorField = patch.getFloorFieldValues().get(PassengerMovement.State.IN_QUEUE)
                                        .getValue();

                                if (floorField == 0.0) {
                                    color = Color.WHITE;
                                } else {
                                    color = Color.hsb(120.0,
                                            floorField / Main.WALKWAY.getMaximumFloorFieldValue(state),
                                            1.0
                                    );
                                }

                                graphicsContext.setFill(color);
                            } else {
                                graphicsContext.setFill(Color.WHITE);
                            }

                            break;
                        case SPAWN:
                            graphicsContext.setFill(Color.BLUE);

                            isGateOrExit = false;

                            break;
                        case TRANSACTION_AREA:
                            graphicsContext.setFill(Color.ORANGE);

                            isGateOrExit = true;

                            break;
                        case TICKET_BOOTH:
                            graphicsContext.setFill(Color.DARKGREEN);

                            isGateOrExit = false;

                            break;
                        case DESPAWN:
                            graphicsContext.setFill(Color.YELLOW);

                            isGateOrExit = true;

                            break;
                        case OBSTACLE:
                            graphicsContext.setFill(Color.BLACK);

                            isGateOrExit = false;

                            break;
                    }

                    graphicsContext.fillRect(column * tileSize, row * tileSize, tileSize, tileSize);

                    if (isGateOrExit && !hasStarted) {
                        if (patch.getType() == Patch.Type.TRANSACTION_AREA) {
                            graphicsContext.setFill(Color.WHITE);
                        } else {
                            graphicsContext.setFill(Color.BLACK);
                        }

                        graphicsContext.fillText(patch.getGoalId(),
                                column * tileSize,
                                (row + 1) * tileSize,
                                tileSize
                        );
                    }
                }
            }

            // Draw passengers, if any
            final double passengerRadius = tileSize / 2.0;

            for (Passenger passenger : Main.WALKWAY.getPassengers()) {
                switch (passenger.getPassengerMovement().getAction()) {
                    case WILL_QUEUE:
                        graphicsContext.setFill(Color.BLACK);

                        break;
                    case ASSEMBLING:
                        graphicsContext.setFill(Color.YELLOW);

                        break;
                    case QUEUEING:
                        graphicsContext.setFill(Color.ORANGE);

                        break;
                    case TRANSACTING:
                        graphicsContext.setFill(Color.RED);

                        break;
                }

                graphicsContext.fillOval(
                        passenger.getPassengerMovement().getPosition().getX() * tileSize - passengerRadius / 2.0
                        /*- passengerRadius / 2.0*/,
                        passenger.getPassengerMovement().getPosition().getY() * tileSize - passengerRadius / 2.0
                        /*- passengerRadius / 2.0*/, passengerRadius, passengerRadius
                );
//            System.out.println(passenger.getX() + ", " + passenger.getY());
            }

            if (!hasStarted) {
                // Check whether it is ready to go to the next step or mode
                if (modeIndex == 0) {
                    // Draw starts
                    stepButton.setDisable(true);
                    nextButton.setDisable(Main.WALKWAY.getStarts().size() == 0);

                    floorFieldChoiceBox.setDisable(true);
                    targetChoiceBox.setDisable(true);
                    drawChoiceBox.setDisable(true);
                } else if (modeIndex == 1) {
                    // Draw gates
                    // Do not let the user go to the next step or mode if no gates have been added yet for this step
                    if (sequence == Main.WALKWAY.getGoals().size()) {
                        stepButton.setDisable(true);
                        nextButton.setDisable(true);

                    } else {
                        // Gates have been previously added, including in this step, so let the user go into the next step
                        // or mode
                        stepButton.setDisable(false);
                        nextButton.setDisable(false);

                    }

                    floorFieldChoiceBox.setDisable(true);
                    targetChoiceBox.setDisable(true);
                    drawChoiceBox.setDisable(true);
                } else if (modeIndex == 2) {
                    // Draw goals
                    // Do not let the user go to the next step or mode if no goals have been added yet for this stop
                    nextButton.setDisable(sequence == Main.WALKWAY.getGoals().size());
                    stepButton.setDisable(true);

                    floorFieldChoiceBox.setDisable(true);
                    targetChoiceBox.setDisable(true);
                    drawChoiceBox.setDisable(true);
                } else if (modeIndex == 3) {
                    // Draw obstacles
                    stepButton.setDisable(true);
                    nextButton.setDisable(false);

                    floorFieldChoiceBox.setDisable(true);
                    targetChoiceBox.setDisable(true);
                    drawChoiceBox.setDisable(true);
                } else if (modeIndex == 4) {
                    if (!isDrawingFloorFields) {
                        // Draw floor fields
                        nextButton.setDisable(true);
                        startButton.setDisable(false);

                        floorFieldChoiceBox.setDisable(false);
                        targetChoiceBox.setDisable(false);
                        drawChoiceBox.setDisable(false);

                        // Add to the targets choice box
                        List<Patch> flattenedGoals = Main.WALKWAY.getGoalsFlattened();

                        List<String> flattenedGoalIds = new ArrayList<>();

                        for (Patch goal : flattenedGoals) {
                            flattenedGoalIds.add(goal.getGoalId());
                        }

                        targetChoiceBox.setItems(FXCollections.observableArrayList(flattenedGoalIds));
                        targetChoiceBox.getSelectionModel().select(0);

                        this.isDrawingFloorFields = true;
                    }
                }
            }

            boolean enableStart = true;

            // Check whether all goals have floor fields attached to them
            for (Patch goal : Main.WALKWAY.getGoalsFlattened()) {
                if (goal.getAssociatedPatches().isEmpty()) {
                    enableStart = false;

                    break;
                }
            }

            startButton.setDisable(!enableStart);

            spawnButton.setDisable(!hasStarted);
            trainDoorsOpenButton.setDisable(!hasStarted);
            drawChoiceBox.setDisable(hasStarted);
        });
    }

    private void nextMode() {
        nextStep();

        this.modeIndex++;
    }

    private void nextStep() {
        this.sequence++;
        this.index = 0;
    }

    public enum Orientation {
        UP,
        RIGHT,
        DOWN,
        LEFT;
    }

    public enum DrawState {
        SPAWN,
        CHECKPOINT,
        DESPAWN,
        OBSTACLE,
        FLOOR_FIELDS;
    }
}
