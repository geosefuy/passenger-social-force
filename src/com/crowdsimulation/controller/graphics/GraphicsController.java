package com.crowdsimulation.controller.graphics;

import com.crowdsimulation.controller.Controller;
import com.crowdsimulation.controller.Main;
import com.crowdsimulation.controller.screen.feature.main.MainScreenController;
import com.crowdsimulation.model.core.agent.passenger.Passenger;
import com.crowdsimulation.model.core.environment.station.Floor;
import com.crowdsimulation.model.core.environment.station.patch.Patch;
import com.crowdsimulation.model.core.environment.station.patch.floorfield.headful.QueueingFloorField;
import com.crowdsimulation.model.core.environment.station.patch.location.Location;
import com.crowdsimulation.model.core.environment.station.patch.location.MatrixPosition;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.Amenity;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.obstacle.TicketBooth;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.obstacle.Wall;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.Queueable;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.gate.Portal;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.gate.StationGate;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.gate.TrainDoor;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.gate.portal.elevator.ElevatorPortal;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.gate.portal.escalator.EscalatorPortal;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.gate.portal.stairs.StairPortal;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.goal.TicketBoothTransactionArea;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.goal.blockable.Security;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.goal.blockable.Turnstile;
import com.crowdsimulation.model.simulator.Simulator;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GraphicsController extends Controller {
    private static final HashMap<Class<?>, Color> PATCH_COLORS = new HashMap<>();
    private static final int FLOOR_FIELD_COLOR_HUE = 115;

    private static final String TOOLTIP_TEMPLATE = "Row %r, column %c\n\n%p";
    private static Patch markedPatch;
    private static boolean showTooltip;
    private static Tooltip tooltip;

    public static TicketBoothTransactionArea.DrawOrientation drawTicketBoothOrientation;
    public static Group extraGroup;
    public static Rectangle extraRectangle;
    public static Patch extraPatch;
    public static boolean validTicketBoothDraw;

    public static Floor floorNextPortal;
    public static MatrixPosition firstPortalDrawnPosition;

    public static double tileSize;

    static {
        GraphicsController.markedPatch = null;

        GraphicsController.showTooltip = false;
        GraphicsController.tooltip = new Tooltip(GraphicsController.TOOLTIP_TEMPLATE);

        GraphicsController.drawTicketBoothOrientation = TicketBoothTransactionArea.DrawOrientation.UP;
        GraphicsController.extraGroup = null;
        GraphicsController.extraRectangle = null;
        GraphicsController.extraPatch = null;
        GraphicsController.validTicketBoothDraw = true;

        GraphicsController.floorNextPortal = null;
        GraphicsController.firstPortalDrawnPosition = null;

        // The designated colors of the patch amenities (or lack thereof)
        PATCH_COLORS.put(null, Color.WHITE); // Empty patch

        PATCH_COLORS.put(StationGate.class, Color.BLUE); // Station gate
        PATCH_COLORS.put(TrainDoor.class, Color.RED); // Train door

        PATCH_COLORS.put(StairPortal.class, Color.VIOLET); // Stairs
        PATCH_COLORS.put(EscalatorPortal.class, Color.DARKVIOLET); // Escalator
        PATCH_COLORS.put(ElevatorPortal.class, Color.DEEPPINK); // Elevator

        PATCH_COLORS.put(Security.class, Color.DEEPSKYBLUE); // Security gate
        PATCH_COLORS.put(TicketBooth.class, Color.GREEN); // Ticket booth
        PATCH_COLORS.put(TicketBoothTransactionArea.class, Color.GRAY); // Ticket booth transaction area
        PATCH_COLORS.put(Turnstile.class, Color.ORANGE); // Turnstile

        PATCH_COLORS.put(Wall.class, Color.BLACK); // Wall
    }

    // Send a request to draw the station view on the canvas
    public static void requestDrawStationView(
            StackPane canvases,
            Floor floor,
            boolean background) {
        javafx.application.Platform.runLater(() -> {
            // Tell the JavaFX thread that we'd like to draw on the canvas
            drawStationView(canvases, floor, background);
        });
    }

    // Send a request to draw the mouse listeners on top of the canvases
    public static void requestDrawListeners(StackPane canvases, Pane overlay, Floor floor) {
        javafx.application.Platform.runLater(() -> {
            // Tell the JavaFX thread that we'd like to draw on the canvas
            drawListeners(canvases, overlay, floor);
        });
    }

    // Draw all that is needed on the station view on the canvases
    private static void drawStationView(StackPane canvases, Floor floor, boolean background) {
        // Get the canvases and their graphics contexts
        final Canvas backgroundCanvas = (Canvas) canvases.getChildren().get(0);
        final Canvas foregroundCanvas = (Canvas) canvases.getChildren().get(1);

        final GraphicsContext backgroundGraphicsContext = backgroundCanvas.getGraphicsContext2D();
        final GraphicsContext foregroundGraphicsContext = foregroundCanvas.getGraphicsContext2D();

        // Get the height and width of the canvases
        final double canvasWidth = backgroundCanvas.getWidth();
        final double canvasHeight = backgroundCanvas.getHeight();

        // Clear everything in the foreground canvas, if all dynamic elements are to be drawn
        if (!background) {
            foregroundGraphicsContext.clearRect(0, 0, canvasWidth, canvasHeight);
        }

        backgroundGraphicsContext.setFont(new Font(9.0));

        // Draw all the patches of this floor
        for (int row = 0; row < floor.getRows(); row++) {
            for (int column = 0; column < floor.getColumns(); column++) {
                // Get the current patch
                Patch currentPatch = Main.simulator.getCurrentFloor().getPatch(row, column);

                // Draw only the background (the environment) if requested
                // Draw only the foreground (the passengers) if otherwise
                if (background) {
                    // Draw graphics corresponding to whatever is in the content of the patch
                    // If the patch has no amenity on it, just draw a blank patch
                    Amenity patchAmenity = currentPatch.getAmenity();
                    Color patchColor;

                    if (patchAmenity == null) {
                        // Draw the marker for first portal reference, if any has been drawn
                        if (!currentPatch.getMatrixPosition().equals(GraphicsController.firstPortalDrawnPosition)) {
                            // There isn't an amenity on this patch, so just use the color corresponding to a blank patch
                            patchColor = PATCH_COLORS.get(null);

                            // Show the floor fields of the current target with the current floor field state
                            Queueable target = Main.simulator.getCurrentFloorFieldTarget();
                            QueueingFloorField.FloorFieldState floorFieldState
                                    = Main.simulator.getCurrentFloorFieldState();

                            Map<Queueable, Map<QueueingFloorField.FloorFieldState, Double>> floorFieldValues
                                    = currentPatch.getFloorFieldValues();
                            Map<QueueingFloorField.FloorFieldState, Double> floorFieldStateDoubleMap
                                    = floorFieldValues.get(target);

                            // Draw something if there is a target associated with this patch
                            if (floorFieldStateDoubleMap != null) {
                                // If the current patch's floor field state matches the current floor field state, draw
                                // a green patch
                                if (floorFieldStateDoubleMap.get(floorFieldState) != null) {
                                    double value = floorFieldStateDoubleMap.get(floorFieldState);

                                    // Map the colors of this patch to the its field value's intensity
                                    patchColor = Color.hsb(
                                            FLOOR_FIELD_COLOR_HUE,
                                            Main.simulator.isFloorFieldDrawing() ? value : 0.1,
                                            1.0
                                    );
                                } else {
                                    // There is a floor field value here with the same target, but it is not of the
                                    // current floor field state
                                    // Hence, just draw an unsaturated patch
                                    patchColor = Color.hsb(
                                            FLOOR_FIELD_COLOR_HUE,
                                            0.1,
                                            1.0
                                    );
                                }
                            } else if (!floorFieldValues.isEmpty()) {
                                // If there isn't a floor field with the current target, but the list of floor field
                                // values isn't empty, there are still other floor field values on this patch
                                // Hence, just draw an unsaturated patch
                                patchColor = Color.hsb(
                                        FLOOR_FIELD_COLOR_HUE,
                                        0.1,
                                        1.0
                                );
                            }
                        } else {
                            // Draw a de-saturated version of the first portal here
                            patchColor = PATCH_COLORS.get(Main.simulator.getFirstPortal().getClass());
                            double hue = patchColor.getHue();

                            patchColor = Color.hsb(
                                    hue,
                                    0.1,
                                    1.0
                            );
                        }
                    } else {
                        // There is an amenity on this patch, so draw it according to its corresponding color
                        patchColor = PATCH_COLORS.get(patchAmenity.getClass());

                        // If floor field drawing is on, only color amenities which are of the current class
                        if (Main.simulator.isFloorFieldDrawing()) {
                            // Only color the current amenity - unsaturate the rest
                            if (!patchAmenity.equals(Main.simulator.getCurrentFloorFieldTarget())) {
                                double hue = patchColor.getHue();

                                patchColor = Color.hsb(
                                        hue,
                                        0.15,
                                        1.0
                                );
                            }
                        }
                    }

                    // Set the color
                    backgroundGraphicsContext.setFill(patchColor);

                    // Draw the patch
                    backgroundGraphicsContext.fillRect(column * tileSize, row * tileSize, tileSize, tileSize);

                    // If this amenity is a portal, draw the floor it connects to
                    if (patchAmenity instanceof Portal) {
                        Portal pair = ((Portal) patchAmenity).getPair();

                        if (pair != null) {
                            Floor pairFloorServed = pair.getFloorServed();
                            int pairFloorNumber = Main.simulator.getStation().getFloors().indexOf(pairFloorServed) + 1;

                            backgroundGraphicsContext.strokeText(
                                    String.valueOf(pairFloorNumber),
                                    column * tileSize + tileSize * 0.25,
                                    row * tileSize - tileSize * 0.2
                            );
                        }
                    }
                } else {
                    // Draw passengers, if any
                    final double passengerDiameter = tileSize * 0.5;

                    for (Passenger passenger : Main.simulator.getCurrentFloor().getPassengersInFloor()) {
                        // TODO: Draw passengers
                        foregroundGraphicsContext.fillOval(
                                passenger.getPassengerMovement().getPosition().getX() * tileSize - passengerDiameter * 0.5,
                                passenger.getPassengerMovement().getPosition().getY() * tileSize - passengerDiameter * 0.5,
                                passengerDiameter,
                                passengerDiameter
                        );
                    }
                }
            }
        }

        // If this amenity is also the currently selected amenity in the simulator, draw a circle around
        // said amenity
        final double CIRCLE_DIAMETER = 100.0;

        Amenity currentAmenity = Main.simulator.getCurrentAmenity();

        if (currentAmenity != null
                && currentAmenity.getPatch().getFloor() == Main.simulator.getCurrentFloor()
                && !Main.simulator.isFloorFieldDrawing()) {
            double row = currentAmenity.getPatch().getMatrixPosition().getRow();
            double column = currentAmenity.getPatch().getMatrixPosition().getColumn();

            backgroundGraphicsContext.strokeOval(
                    (column * tileSize - CIRCLE_DIAMETER * 0.5 + tileSize * 0.5),
                    (row * tileSize - CIRCLE_DIAMETER * 0.5 + tileSize * 0.5),
                    CIRCLE_DIAMETER,
                    CIRCLE_DIAMETER
            );
        }
    }

    // Draw the mouse listeners over the canvases
    // These listeners allows the user to graphically interact with the station amenities
    private static void drawListeners(StackPane canvases, Pane overlay, Floor floor) {
        // Get the background canvas
        final Canvas backgroundCanvas = (Canvas) canvases.getChildren().get(0);

        // Draw listeners for the canvas (used for the detection of the orientation when drawing ticket booths)
        backgroundCanvas.getScene().setOnKeyPressed(e -> {
            switch (e.getCode()) {
                // Build mode-related shortcut keys
                case DIGIT1:
                    // Draw mode
                    if (!Main.simulator.getRunning().get()
                            && !Main.simulator.isFloorFieldDrawing()
                            && !Main.simulator.isPortalDrawing()) {
                        MainScreenController.switchBuildMode(Simulator.BuildState.DRAWING);
                    }

                    break;
                case DIGIT2:
                    // Edit one mode
                    if (!Main.simulator.getRunning().get()
                            && !Main.simulator.isFloorFieldDrawing()
                            && !Main.simulator.isPortalDrawing()) {
                        MainScreenController.switchBuildMode(Simulator.BuildState.EDITING_ONE);
                    }

                    break;
                case DIGIT3:
                    // Edit all mode
                    if (!Main.simulator.getRunning().get()
                            && !Main.simulator.isFloorFieldDrawing()
                            && !Main.simulator.isPortalDrawing()) {
                        MainScreenController.switchBuildMode(Simulator.BuildState.EDITING_ALL);
                    }

                    break;
                // Ticket booth-related shortcut keys
                case W:
                    // Rotate up
                    if (Main.simulator.getBuildSubcategory() == Simulator.BuildSubcategory.TICKET_BOOTH
                            && Main.simulator.getBuildState() == Simulator.BuildState.DRAWING) {
                        GraphicsController.drawTicketBoothOrientation = TicketBoothTransactionArea.DrawOrientation.UP;
                    }

                    break;
                case D:
                    // Rotate right
                    if (Main.simulator.getBuildSubcategory() == Simulator.BuildSubcategory.TICKET_BOOTH
                            && Main.simulator.getBuildState() == Simulator.BuildState.DRAWING) {
                        GraphicsController.drawTicketBoothOrientation = TicketBoothTransactionArea.DrawOrientation.RIGHT;
                    }

                    break;
                case S:
                    // Rotate down
                    if (Main.simulator.getBuildSubcategory() == Simulator.BuildSubcategory.TICKET_BOOTH
                            && Main.simulator.getBuildState() == Simulator.BuildState.DRAWING) {
                        GraphicsController.drawTicketBoothOrientation = TicketBoothTransactionArea.DrawOrientation.DOWN;
                    }

                    break;
                case A:
                    // Rotate left
                    if (Main.simulator.getBuildSubcategory() == Simulator.BuildSubcategory.TICKET_BOOTH
                            && Main.simulator.getBuildState() == Simulator.BuildState.DRAWING) {
                        GraphicsController.drawTicketBoothOrientation = TicketBoothTransactionArea.DrawOrientation.LEFT;
                    }

                    break;
                // Edit one/all shortcut keys
                case ENTER:
                    // Save amenity
                    if (Main.simulator.getBuildState() != Simulator.BuildState.DRAWING
                            && !Main.simulator.isPortalDrawing()
                            && !Main.simulator.isFloorFieldDrawing()
                            && Main.simulator.getBuildCategory() != Simulator.BuildCategory.WALLS) {
                        if (Main.simulator.getBuildState() == Simulator.BuildState.EDITING_ONE
                                && Main.simulator.getCurrentAmenity() != null
                                || Main.simulator.getBuildState() == Simulator.BuildState.EDITING_ALL) {
                            if (Main.simulator.getBuildCategory() != Simulator.BuildCategory.STAIRS_AND_ELEVATORS) {
                                // Non-portal amenities
                                Main.mainScreenController.saveAmenityAction();
                            } else {
                                // Portal amenities
                                try {
                                    Main.mainScreenController.editPortalAction();
                                } catch (IOException ioException) {
                                    ioException.printStackTrace();
                                }
                            }
                        }
                    }

                    break;
                case DELETE:
                case BACK_SPACE:
                    // Delete amenity
                    if (Main.simulator.getBuildState() != Simulator.BuildState.DRAWING
                            && !Main.simulator.isPortalDrawing()
                            && !Main.simulator.isFloorFieldDrawing()) {
                        if (Main.simulator.getBuildState() == Simulator.BuildState.EDITING_ONE
                                && Main.simulator.getCurrentAmenity() != null
                                || Main.simulator.getBuildState() == Simulator.BuildState.EDITING_ALL) {
                            if (Main.simulator.getBuildCategory() != Simulator.BuildCategory.STAIRS_AND_ELEVATORS) {
                                // Non-portal amenities
                                Main.mainScreenController.deleteAmenityAction();
                            } else {
                                // Portal amenities
                                Main.mainScreenController.deletePortalAction();
                            }
                        }
                    }

                    break;
            }
        });

        // Initialize the crosshair matrix to be used as markers when the user hovers over a patch
        final Group[][] crosshairs
                = new Group[Main.simulator.getCurrentFloor().getRows()][Main.simulator.getCurrentFloor().getColumns()];

        backgroundCanvas.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            // If there are no subcategories, erase all markings
            // Update the visual markings
            Patch currentPatch = retrievePatchFromMouseClick(event);

            if (currentPatch != null) {
                updateMarkings(backgroundCanvas, crosshairs, currentPatch);
            }
        });

        // Draw listeners for the drawing mechanisms
        backgroundCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            // If there are no subcategories, erase all markings
            if (Main.simulator.getBuildSubcategory() != Simulator.BuildSubcategory.NONE) {
                // Get the patch coordinates from the mouse click coordinates
                Patch currentPatch = retrievePatchFromMouseClick(event);

                // Special case: if a ticket booth is currently being drawn get the extra patch as well, assuming
                // a valid ticket booth drawing spot
                adjustIfTicketBooth();

                // Set the amenity on the patch as the current amenity of the simulation
                assert currentPatch != null;

                Main.simulator.setCurrentAmenity(currentPatch.getAmenity());

                // Actions for left click
                if (event.getButton() == MouseButton.PRIMARY) {
                    // Commence building or editing on that patch
                    try {
                        Main.mainScreenController.buildOrEdit(currentPatch);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Redraw the station view
                    drawStationView(canvases, floor, true);
                }
            }
        });

        backgroundCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            // If there are no subcategories, erase all markings
            if (Main.simulator.getBuildSubcategory() != Simulator.BuildSubcategory.NONE) {
                // Only allow dragging when drawing walls
                if (Main.simulator.getBuildSubcategory() == Simulator.BuildSubcategory.WALL) {
                    // Get the patch coordinates from the mouse click coordinates
                    Patch currentPatch = retrievePatchFromMouseClick(event);

                    // Only proceed when the mouse is dragged within bounds
                    if (currentPatch != null) {
                        // Update the visual markings
                        updateMarkings(backgroundCanvas, crosshairs, currentPatch);

                        // Special case: if a ticket booth is currently being drawn get the extra patch as well, assuming
                        // a valid ticket booth drawing spot
                        adjustIfTicketBooth();

                        // Set the amenity on the patch as the current amenity of the simulation
                        Main.simulator.setCurrentAmenity(currentPatch.getAmenity());

                        // When dragging, only draw on patches without amenities on them
                        if (Main.simulator.getCurrentAmenity() == null) {
                            // Actions for left click
                            if (event.getButton() == MouseButton.PRIMARY) {
                                // Commence building or editing on that patch
                                try {
                                    Main.mainScreenController.buildOrEdit(currentPatch);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                // Redraw the station view
                                drawStationView(canvases, floor, true);
                            }
                        }
                    }
                }
            }
        });

        backgroundCanvas.setOnScroll(event -> {
            // Use the scroll wheel to set the intensity value
            if (Main.simulator.isFloorFieldDrawing()) {
                final double zoomFactor = 0.00075;
                double newValue
                        = MainScreenController.normalFloorFieldController.getIntensity()
                        + event.getDeltaY() * zoomFactor;

                if (newValue < 0.1) {
                    newValue = 0.1;
                } else if (newValue > 1.0) {
                    newValue = 1.0;
                }

                MainScreenController.normalFloorFieldController.setIntensity(newValue);
            }
        });

        // Prepare the information displayed in each patch
        for (int row = 0; row < Main.simulator.getCurrentFloor().getRows(); row++) {
            for (int column = 0; column < Main.simulator.getCurrentFloor().getColumns(); column++) {
                // Create the rectangle
                Rectangle rectangle = new Rectangle(column * tileSize, row * tileSize, tileSize, tileSize);
                rectangle.setFill(Color.LIGHTGRAY);

                rectangle.getProperties().put("row", row);
                rectangle.getProperties().put("column", column);

                // Create the horizontal line
                Line horizontalLine = new Line();

                horizontalLine.setStartX(0);
                horizontalLine.setStartY(row * tileSize + tileSize * 0.5);

                horizontalLine.setEndX(backgroundCanvas.getWidth());
                horizontalLine.setEndY(row * tileSize + tileSize * 0.5);

                // Create the vertical line
                Line verticalLine = new Line();

                verticalLine.setStartX(column * tileSize + tileSize * 0.5);
                verticalLine.setStartY(0);

                verticalLine.setEndX(column * tileSize + tileSize * 0.5);
                verticalLine.setEndY(backgroundCanvas.getHeight());

                // Individually initialize each crosshair in the matrix
                Group crosshair = new Group(rectangle, horizontalLine, verticalLine);

                crosshair.setOpacity(0.0);
                crosshair.setMouseTransparent(false);

                crosshairs[row][column] = crosshair;

                overlay.getChildren().add(crosshair);
            }
        }
    }

    private static void updateMarkings(Canvas backgroundCanvas, Group[][] crosshairs, Patch currentPatch) {
        // If there are no subcategories, erase all markings
        if (Main.simulator.getBuildSubcategory() != Simulator.BuildSubcategory.NONE) {
            // Show the tooltip if drawing is active
            if (!GraphicsController.showTooltip) {
                GraphicsController.showTooltip = true;
                Tooltip.install(backgroundCanvas, GraphicsController.tooltip);
            }

            if (GraphicsController.markedPatch == null) {
                // Update the tooltip content
                updateTooltip(currentPatch);

                // Light this patch up
                GraphicsController.markedPatch = currentPatch;

                markPatch(crosshairs, currentPatch);
            } else {
                if (GraphicsController.markedPatch != currentPatch) {
                    // Unmark the previously marked patch
                    unmarkPatch(crosshairs, GraphicsController.markedPatch);

                    // Update the tooltip content
                    updateTooltip(currentPatch);

                    // Then mark this patch
                    GraphicsController.markedPatch = currentPatch;

                    markPatch(crosshairs, currentPatch);
                }
            }
        } else {
            // Erase crosshair
            if (GraphicsController.markedPatch != null) {
                unmarkPatch(crosshairs, GraphicsController.markedPatch);

                GraphicsController.markedPatch = null;
            }

            // Erase tooltip
            GraphicsController.showTooltip = false;
            Tooltip.uninstall(backgroundCanvas, GraphicsController.tooltip);
        }
    }

    private static void updateTooltip(Patch currentPatch) {
        // Set the tooltip text
        String newText = GraphicsController.TOOLTIP_TEMPLATE;

        newText = newText.replace(
                "%r",
                String.valueOf(currentPatch.getMatrixPosition().getRow())
        );

        newText = newText.replace(
                "%c",
                String.valueOf(currentPatch.getMatrixPosition().getColumn())
        );

        Amenity amenity = currentPatch.getAmenity();

        if (amenity == null) {
            newText = newText.replace("%p", "Empty patch");
        } else {
            newText = newText.replace("%p", amenity.toString());
        }

        GraphicsController.tooltip.setText(newText);
        GraphicsController.tooltip.setX(currentPatch.getPatchCenterCoordinates().getX() * tileSize);
        GraphicsController.tooltip.setY(currentPatch.getPatchCenterCoordinates().getY() * tileSize);
    }

    private static void adjustIfTicketBooth() {
        // Special case: if a ticket booth is currently being drawn get the extra patch as well, assuming
        // a valid ticket booth drawing spot
        if (
                GraphicsController.validTicketBoothDraw
                        && Main.simulator.getBuildSubcategory() == Simulator.BuildSubcategory.TICKET_BOOTH
                        && extraRectangle != null
        ) {
            int extraPatchRow = (int) GraphicsController.extraRectangle.getProperties().get("row");
            int extraPatchColumn = (int) GraphicsController.extraRectangle.getProperties().get("column");

            GraphicsController.extraPatch = Main.simulator.getCurrentFloor().getPatch(
                    extraPatchRow,
                    extraPatchColumn
            );
        }
    }

    private static Patch retrievePatchFromMouseClick(MouseEvent event) {
        // Get the patch coordinates from the mouse click coordinates
        MatrixPosition matrixPosition = Location.screenCoordinatesToMatrixPosition(
                event.getX(),
                event.getY(),
                tileSize
        );

        // When the position given is a null, this means the mouse has been dragged out of bounds
        if (matrixPosition != null) {
            // Retrieve the patch at that location
            return Main.simulator.getCurrentFloor().getPatch(matrixPosition);
        } else {
            return null;
        }
    }

    private static void markPatch(Group[][] crosshairs, Patch patch) {
        // Retrieve the crosshair at that patch location
        int row = patch.getMatrixPosition().getRow();
        int column = patch.getMatrixPosition().getColumn();

        Group crosshair = crosshairs[row][column];

        crosshair.setOpacity(0.5);

        if (Main.simulator.getBuildSubcategory() == Simulator.BuildSubcategory.TICKET_BOOTH
                && Main.simulator.getBuildState() == Simulator.BuildState.DRAWING) {
            switch (GraphicsController.drawTicketBoothOrientation) {
                case UP:
                    if (row - 1 >= 0) {
                        Group extraCrosshair = crosshairs[row - 1][column];
                        extraCrosshair.setOpacity(1.0);

                        GraphicsController.extraPatch = Main.simulator.getCurrentFloor().getPatch(
                                row - 1,
                                column
                        );

                        GraphicsController.extraRectangle = (Rectangle) extraCrosshair.getChildren().get(0);
                        GraphicsController.extraGroup = extraCrosshair;
                        GraphicsController.validTicketBoothDraw
                                = patch.getAmenity() == null
                                && extraPatch.getAmenity() == null
                                && patch.getFloorFieldValues().isEmpty()
                                && extraPatch.getFloorFieldValues().isEmpty();
                    } else {
                        GraphicsController.validTicketBoothDraw = false;
                    }

                    break;
                case RIGHT:
                    if (column + 1 < Main.simulator.getCurrentFloor().getColumns()) {
                        Group extraCrosshair = crosshairs[row][column + 1];
                        extraCrosshair.setOpacity(1.0);

                        GraphicsController.extraPatch = Main.simulator.getCurrentFloor().getPatch(
                                row,
                                column + 1
                        );

                        GraphicsController.extraRectangle = (Rectangle) extraCrosshair.getChildren().get(0);
                        GraphicsController.extraGroup = extraCrosshair;
                        GraphicsController.validTicketBoothDraw
                                = patch.getAmenity() == null && extraPatch.getAmenity() == null;
                    } else {
                        GraphicsController.validTicketBoothDraw = false;
                    }

                    break;
                case DOWN:
                    if (row + 1 < Main.simulator.getCurrentFloor().getRows()) {
                        Group extraCrosshair = crosshairs[row + 1][column];
                        extraCrosshair.setOpacity(1.0);

                        GraphicsController.extraPatch = Main.simulator.getCurrentFloor().getPatch(
                                row + 1,
                                column
                        );

                        GraphicsController.extraRectangle = (Rectangle) extraCrosshair.getChildren().get(0);
                        GraphicsController.extraGroup = extraCrosshair;
                        GraphicsController.validTicketBoothDraw
                                = patch.getAmenity() == null && extraPatch.getAmenity() == null;
                    } else {
                        GraphicsController.validTicketBoothDraw = false;
                    }

                    break;
                case LEFT:
                    if (column - 1 >= 0) {
                        Group extraCrosshair = crosshairs[row][column - 1];
                        extraCrosshair.setOpacity(1.0);

                        GraphicsController.extraPatch = Main.simulator.getCurrentFloor().getPatch(
                                row,
                                column - 1
                        );

                        GraphicsController.extraRectangle = (Rectangle) extraCrosshair.getChildren().get(0);
                        GraphicsController.extraGroup = extraCrosshair;
                        GraphicsController.validTicketBoothDraw
                                = patch.getAmenity() == null && extraPatch.getAmenity() == null;
                    } else {
                        GraphicsController.validTicketBoothDraw = false;
                    }

                    break;
            }
        }
    }

    private static void unmarkPatch(Group[][] crosshairs, Patch patch) {
        // Retrieve the crosshair at that patch location
        int row = patch.getMatrixPosition().getRow();
        int column = patch.getMatrixPosition().getColumn();

        Group crosshair = crosshairs[row][column];

        crosshair.setOpacity(0.0);

        if (extraGroup != null) {
            extraGroup.setOpacity(0.0);
        }
    }
}
