package sample;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.*;

public class Controller {


    private final DrawState[] stateSequences = new DrawState[]{
            DrawState.START,
            DrawState.CHECKPOINT,
            DrawState.GOAL,
            DrawState.OBSTACLE,
            DrawState.FLOOR_FIELDS
//            DrawState.QUEUE_PATH,
//            DrawState.QUEUE_ENTRANCE
    };

    private boolean hasStarted;
    private DrawState drawState;
    private int sequence;
    private int index;
    private int modeIndex;

    @FXML
    private Canvas canvas;

    @FXML
    private ChoiceBox<String> drawChoiceBox;

    @FXML
    private Button startButton;

    @FXML
    private Button nextButton;

    @FXML
    private Button stepButton;

    @FXML
    private Text numAgentsText;

    @FXML
    private Pane overlay;

    @FXML
    private StackPane stackPane;

    @FXML
    private ToggleButton spawnButton;

    @FXML
    private ToggleButton trainDoorsOpenButton;

    @FXML
    private Button loadButton;

    @FXML
    private Button saveButton;

    @FXML
    private ChoiceBox<String> floorFieldChoiceBox;

    private double tileSize;
    private GraphicsContext graphicsContext;
    private List<String[]> stringChoices;

    private int numAgents;

    public Controller() {
        this.hasStarted = false;
    }

    @FXML
    private void initialize() {
        tileSize = canvas.getHeight() / Main.WALKWAY.getRows();
        graphicsContext = canvas.getGraphicsContext2D();

        List<String> floorFieldChoicesList = new ArrayList<>();

        for (PassengerMovement.State state : PassengerMovement.State.values()) {
            floorFieldChoicesList.add(state.name());
        }

        // Remove will queue state
        floorFieldChoicesList.remove("WILL_QUEUE");

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

        final String[] startItems = {"Start"};
//        final String[] checkpointItems = {"Waypoint", "Gate"};
        final String[] checkpointItems = {"Gate"};
        final String[] goalItems = {"Exit"};
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
    private void play() throws InterruptedException {
        this.hasStarted = true;
        stackPane.getChildren().remove(overlay);

//        // Diffuse goals
//        Main.WALKWAY.diffuseGoals();

        // For each state, normalize the floor field values
        PassengerMovement.State[] stateArray = PassengerMovement.State.values();

        for (PassengerMovement.State state : stateArray) {
            if (state != PassengerMovement.State.WILL_QUEUE) {
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
                    // Only move if the passenger is not waiting
                    if (!passenger.getPassengerMovement().isWaiting()) {
                        double headingGoal;

                        // Set the nearest goal to this passenger
                        // This internally computes for the nearest goal to it
                        passenger.getPassengerMovement().setNearestGoal();

                        // Take note of the heading towards the goal patch
                        // Get the x and y coordinates of the patch in question
                        // These coordinates should be in the center of the patch
                        headingGoal = passenger.getPassengerMovement().headingTowards(
                                passenger.getPassengerMovement().getGoal().getPatchCenterCoordinates()
                        );

                        PassengerMovement.State state = passenger.getPassengerMovement().getState();

                        // If the passenger isn't currently queueing, check if it may now be queueing
                        if (state == PassengerMovement.State.WILL_QUEUE) {
                            // Check the floor field of this patch and use it to determine if the passenger
                            // will transition into a queueing state
                            double floorField = passenger.getPassengerMovement().getCurrentPatch().getFloorFields().get(
                                    PassengerMovement.State.QUEUEING);

                            // Use the floor field value for the probability generation
                            if (new Random().nextDouble() < floorField) {
                                // If the likelihood is met, the passenger will now be queueing, instead of waiting to
                                // queue
                                passenger.getPassengerMovement().setState(PassengerMovement.State.QUEUEING);
                            }
                        }

                        // If the passenger is not queueing, just follow its current goal
                        // If the passenger is queueing, also take the heading towards the passenger at the tail of the
                        // queue into account, as well as the floor fields
                        if (state == PassengerMovement.State.WILL_QUEUE) {
                            // Face the goal
                            face(passenger, null, headingGoal);
                        } else {
                            Patch goal = passenger.getPassengerMovement().getGoal();

                            // Use the highest neighboring patch with the highest floor field to influence the
                            // heading of this passenger
                            Patch bestPatch = Main.WALKWAY.chooseBestNeighboringPatch(
                                    Main.WALKWAY.getPatch(passenger.getPassengerMovement().getFuturePosition(
                                            goal, headingGoal
                                    )),
                                    headingGoal,
                                    state
                            );

                            // Get the heading toward the best patch
                            double headingBestPatch = passenger.getPassengerMovement().headingTowards(
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
                                // Get the passenger at the tail of the queue
                                Passenger passengerAtTail = goal.getPassengersQueueing().peekFirst();

                                assert passengerAtTail != null;
//
//                                if (Double.isNaN(passengerAtTail.getPassengerMovement().getPosition().getX())) {
//                                    System.out.println("oops");
//                                }

                                // Get the heading towards that passenger
                                double headingTail = passenger.getPassengerMovement().headingTowards(
                                        passengerAtTail.getPassengerMovement().getPosition()
                                );

                                // Face towards the heading towards the tail of the queue
                                face(passenger, null, headingTail);
                            }
                        }

//                        // Try to choose a leader if this passenger doesn't already have one
//                        if (passenger.getPassengerMovement().getLeader() == null) {
//                            // Try to choose a leader
//                            boolean leaderChosen = passenger.getPassengerMovement().setLeader();
//
//                            // If a leader has been chosen, take note of the heading to that leader
//                            if (leaderChosen) {
//                                Passenger leader = passenger.getPassengerMovement().getLeader();
//
//                                // Face towards the angular mean of the headings toward the leader and the goal
//                                face(passenger, leader, headingGoal);
//                            } else {
//                                // No leader has been chosen, continue with the passenger's own knowledge of the
//                                // position of the goal
//                                face(passenger, null, headingGoal);
//                            }
//                        } else {
//                            // If the passenger already has a leader, continue with the passenger's knowledge of the
//                            // positions of the goal and its leader
//                            Passenger leader = passenger.getPassengerMovement().getLeader();
//
//                            face(passenger, leader, headingGoal);
//                        }

//                            // Choose the patch with the highest gradient
//                            Patch chosenPatch = passenger.choosePatch(
//                                    Main.WALKWAY.getRows(),
//                                    Main.WALKWAY.getColumns(),
//                                    false
//                            );
//
//                            // Take note of the heading towards the patch with the highest gradient
//                            headingBestPatch = passenger.headingTowards(chosenPatch);

                        // Set the heading to the mean of the above headings
//                            double meanHeading = Passenger.meanHeading(headingGoal, headingBestPatch);

//                            passenger.setHeading(headingGoal);

                        //

                        // Make this passenger move, if allowable
                        if (passenger.getPassengerMovement().shouldMove(10.0)) {
                            passenger.getPassengerMovement().move();
                        } else {
                            passenger.getPassengerMovement().setHeading(
                                    passenger.getPassengerMovement().getHeading()
                                            + new Random().nextGaussian() * Math.toRadians(30.0)
                            );
                        }
                    }

                    // Every movement, check if the leader, if it still exists, is still ahead
                    if (passenger.getPassengerMovement().getLeader() != null
                            && !passenger.getPassengerMovement().isWithinFieldOfView(
                            passenger.getPassengerMovement().getLeader(), Math.toRadians(20.0)
                    )) {
                        // If not, remove it as a leader
                        passenger.getPassengerMovement().clearLeader();
                    }

                    // Check if the passenger is at its goal
                    if (Main.WALKWAY.checkGoal(passenger)) {
                        // Check if the goal the passenger is on allows this passenger to pass
                        if (Main.WALKWAY.checkPass(passenger, trainDoorsOpenButton.isSelected())) {
                            // If it is, increment its goals reached counter
                            passenger.getPassengerMovement().reachGoal();

                            // Remove the passenger from the queue of its goal, if that goal has a queue
                            if (passenger.getPassengerMovement().getGoal().getPassengersQueueing() != null) {
                                passenger.getPassengerMovement().getGoal().getPassengersQueueing().remove();
                            }

                            // Restore the status of the passenger to will queue
                            passenger.getPassengerMovement().setState(PassengerMovement.State.WILL_QUEUE);

                            // If it has no more goals left, this passenger should be removed
                            if (passenger.getPassengerMovement().getGoalsLeft() == 0) {
                                passengersRemoved.add(passenger);
                            }

                            // Allow the passenger to move again
                            passenger.getPassengerMovement().setWaiting(false);
                        } else {
                            // Do not allow the passenger to move
                            passenger.getPassengerMovement().setWaiting(true);
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
            meanHeading += new Random().nextGaussian() * Math.toRadians(10);

            currentPassenger.getPassengerMovement().setHeading(meanHeading);
        } else {
            // No leader has been chosen, continue with the passenger's own knowledge of the
            // position of the goal
            // Add random perturbations for realistic movement
            headingGoal += new Random().nextGaussian() * Math.toRadians(10);

            // If a leader has not been chosen, continue moving solo
            currentPassenger.getPassengerMovement().setHeading(headingGoal);
        }
    }

    private void updateNumAgents() {
        Platform.runLater(() -> this.numAgentsText.setText(this.numAgents + " agents"));
    }

    private void drawListeners(String[] items, double tileSize) {
        for (int row = 0; row < Main.WALKWAY.getRows(); row++) {
            for (int column = 0; column < Main.WALKWAY.getColumns(); column++) {
                Rectangle rectangle = new Rectangle(column * tileSize, row * tileSize, tileSize, tileSize);
                rectangle.setFill(Color.DARKGRAY);
                rectangle.setOpacity(0.0);
                rectangle.setStyle("-fx-cursor: hand;");

                rectangle.getProperties().put("row", row);
                rectangle.getProperties().put("column", column);

                rectangle.addEventFilter(MouseEvent.MOUSE_ENTERED, e -> {
                    FadeTransition fadeTransition = new FadeTransition(Duration.millis(100), rectangle);
                    fadeTransition.setFromValue(0.0);
                    fadeTransition.setToValue(0.5);
                    fadeTransition.play();
                });

                rectangle.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
                    FadeTransition fadeTransition = new FadeTransition(Duration.millis(100), rectangle);
                    fadeTransition.setFromValue(0.5);
                    fadeTransition.setToValue(0.0);
                    fadeTransition.play();
                });

                rectangle.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                    int patchRow = (int) rectangle.getProperties().get("row");
                    int patchColumn = (int) rectangle.getProperties().get("column");

//                    String choice = drawChoiceBox.getSelectionModel().getSelectedItem();

                    switch (drawState) {
                        case START:
                            Main.WALKWAY.setType(patchRow, patchColumn, Patch.Type.START, this.sequence);

                            break;
                        case CHECKPOINT:
//                            switch (choice) {
//                                case "Waypoint":
//                                    Main.WALKWAY.setType(patchRow, patchColumn, Patch.Type.WAYPOINT, this.sequence);
//
//                                    break;
//                                case "Gate":
                            Main.WALKWAY.setType(patchRow, patchColumn, Patch.Type.GATE, this.sequence);
//
//                                    break;
//                            }

                            break;
                        case GOAL:
                            Main.WALKWAY.setType(patchRow, patchColumn, Patch.Type.EXIT, this.sequence);

                            break;
                        case OBSTACLE:
                            Main.WALKWAY.setType(patchRow, patchColumn, Patch.Type.OBSTACLE, this.sequence);

                            break;
                        case FLOOR_FIELDS:
                            Main.WALKWAY.setFloorField(patchRow, patchColumn, PassengerMovement.State.valueOf(
                                    drawChoiceBox.getSelectionModel().getSelectedItem()
                            ));

                            break;
                    }

                    // Redraw grid
                    drawInterface(graphicsContext, tileSize);
                });

                overlay.getChildren().add(rectangle);
            }
        }
    }

    private void drawInterface(GraphicsContext graphicsContext, double tileSize) {
//        System.out.println(canvas.getWidth() + "x" + canvas.getHeight());
//        System.out.println(((BorderPane) canvas.getParent().getParent()).getPrefWidth() + "x" + ((BorderPane) canvas.getParent().getParent()).getPrefHeight());

        Platform.runLater(() -> {
            graphicsContext.setFill(Color.WHITE);

            for (int row = 0; row < Main.WALKWAY.getRows(); row++) {
                for (int column = 0; column < Main.WALKWAY.getColumns(); column++) {
                    switch (Main.WALKWAY.getPatch(row, column).getType()) {
                        case CLEAR:
                            PassengerMovement.State state = null;

                            // Get the selected state
                            String selectedItemName = floorFieldChoiceBox.getSelectionModel().getSelectedItem();

                            if (!selectedItemName.equals("None")) {
                                state = PassengerMovement.State.valueOf(
                                        selectedItemName
                                );
                            }

                            // Get the patch referenced
                            Patch patch = Main.WALKWAY.getPatch(row, column);

                            if (state == PassengerMovement.State.QUEUEING) {
                                Color color;

                                // Show floor field value
                                double floorField = patch.getFloorFields().get(PassengerMovement.State.QUEUEING);

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
                        case START:
                            graphicsContext.setFill(Color.BLUE);

                            break;
                        case WAYPOINT:
                            graphicsContext.setFill(Color.GRAY);

                            break;
                        case GATE:
                            graphicsContext.setFill(Color.GREEN);

                            break;
                        case EXIT:
                            graphicsContext.setFill(Color.YELLOW);

                            break;
                        case OBSTACLE:
                            graphicsContext.setFill(Color.BLACK);

                            break;
                    }

                    graphicsContext.fillRect(column * tileSize, row * tileSize, tileSize, tileSize);
                }
            }

            // Draw passengers, if any
            final double passengerRadius = tileSize / 2.0;

            for (Passenger passenger : Main.WALKWAY.getPassengers()) {
//                graphicsContext.setFill(passenger.getColor());
                switch (passenger.getPassengerMovement().getState()) {
                    case WILL_QUEUE:
                        graphicsContext.setFill(Color.BLACK);

                        break;
                    case QUEUEING:
                        graphicsContext.setFill(Color.ORANGE);

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

                } else if (modeIndex == 2) {
                    // Draw goals
                    // Do not let the user go to the next step or mode if no goals have been added yet for this stop
                    nextButton.setDisable(sequence == Main.WALKWAY.getGoals().size());
                    stepButton.setDisable(true);
                } else if (modeIndex == 3) {
                    // Draw obstacles
                    stepButton.setDisable(true);
                    nextButton.setDisable(false);
                } else if (modeIndex == 4) {
                    // Draw floor fields
                    nextButton.setDisable(true);
                    startButton.setDisable(false);
                }
            }

//            // Check whether it is ready to start
//            if (Main.WALKWAY.getGoals().size() == 0) {
//                startButton.setDisable(true);
//            } else {
//                startButton.setDisable(Main.WALKWAY.getGoals().get(Main.WALKWAY.getGoals().size() - 1).size() == 0 || hasStarted);
//            }

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

    public enum DrawState {
        START,
        CHECKPOINT,
        GOAL,
        OBSTACLE,
        FLOOR_FIELDS
    }
}
