package com.crowdsimulation.controller.screen.feature.portal.setup;

import com.crowdsimulation.controller.screen.main.service.InitializeElevatorSetupService;
import com.crowdsimulation.controller.screen.main.service.InitializeStairSetupService;
import com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.gate.portal.stairs.StairShaft;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class StairSetupController extends PortalSetupController {
    public static final String OUTPUT_KEY = "stair_shaft";

    @FXML
    private Text promptText;

    @FXML
    private CheckBox stairEnableCheckBox;

    @FXML
    private Label stairMoveLabel;

    @FXML
    private Spinner<Integer> stairMoveSpinner;

    @FXML
    private Button proceedButton;

    @FXML
    public void proceedAction() {
        Stage stage = (Stage) proceedButton.getScene().getWindow();

        // Take note of the values in the form
        boolean enabled = stairEnableCheckBox.isSelected();
        int moveTime = stairMoveSpinner.getValue();

        // Prepare the provisional stair shaft
        // If the user chooses not to go through with the stair, this shaft will
        // simply be discarded
        StairShaft.StairShaftFactory stairShaftFactory =
                new StairShaft.StairShaftFactory();

        StairShaft stairShaft = stairShaftFactory.create(
                null,
                enabled,
                moveTime
        );

        this.getWindowOutput().put(OUTPUT_KEY, stairShaft);

        // Close the window
        this.setClosedWithAction(true);
        stage.close();
    }

    public void setElements() {
        InitializeStairSetupService.initializeStairSetup(
                promptText,
                stairEnableCheckBox,
                stairMoveLabel,
                stairMoveSpinner,
                proceedButton
        );
    }

    @Override
    protected void closeAction() {

    }
}
