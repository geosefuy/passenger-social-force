package com.crowdsimulation.controller.screen.service.floorfield;

import com.crowdsimulation.controller.Main;
import com.crowdsimulation.controller.screen.feature.floorfield.NormalFloorFieldController;
import com.crowdsimulation.controller.screen.feature.main.MainScreenController;
import com.crowdsimulation.model.core.environment.station.patch.floorfield.headful.QueueingFloorField;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.text.Text;

public class InitializeNormalFloorFieldService {
    private static final BooleanBinding DRAWING_FLOOR_FIELD_MODE;

    static {
        DRAWING_FLOOR_FIELD_MODE = Bindings.notEqual(
                MainScreenController.normalFloorFieldController.floorFieldModeProperty(),
                NormalFloorFieldController.FloorFieldMode.DRAWING
        );
    }

    public static void initializeNormalFloorField(
            Text promptText,
            Label modeLabel,
            ChoiceBox<NormalFloorFieldController.FloorFieldMode> modeChoiceBox,
            Label directionLabel,
            ChoiceBox<QueueingFloorField.FloorFieldState> floorFieldStateChoiceBox,
            Label intensityLabel,
            Slider intensitySlider,
            TextField intensityTextField,
            Button validateButton,
            Button deleteAllButton
    ) {
        modeLabel.setLabelFor(modeChoiceBox);

        modeChoiceBox.setItems(FXCollections.observableArrayList(
                NormalFloorFieldController.FloorFieldMode.DRAWING,
                NormalFloorFieldController.FloorFieldMode.DELETING
        ));
        modeChoiceBox.getSelectionModel().select(0);

        directionLabel.setLabelFor(floorFieldStateChoiceBox);

        NormalFloorFieldController.FloorFieldMode initialFloorFieldMode
                = modeChoiceBox.getSelectionModel().getSelectedItem();
        MainScreenController.normalFloorFieldController.setFloorFieldMode(initialFloorFieldMode);

        NormalFloorFieldController.updatePromptText(promptText, initialFloorFieldMode);

        modeChoiceBox.setOnAction(event -> {
            // Set the floor field mode as given in the choice box
            NormalFloorFieldController.FloorFieldMode updatedFloorFieldMode
                    = modeChoiceBox.getSelectionModel().getSelectedItem();

            MainScreenController.normalFloorFieldController.setFloorFieldMode(updatedFloorFieldMode);

            // Also update the prompt text
            NormalFloorFieldController.updatePromptText(promptText, updatedFloorFieldMode);
        });

        floorFieldStateChoiceBox.setOnAction(event -> {
            // Set the floor field state as given in the choice box
            QueueingFloorField.FloorFieldState updatedFloorFieldState
                    = floorFieldStateChoiceBox.getSelectionModel().getSelectedItem();

            MainScreenController.normalFloorFieldController.setFloorFieldState(updatedFloorFieldState);

            // Also update the floor field state
            Main.mainScreenController.updateFloorFieldState(updatedFloorFieldState);
        });

        validateButton.disableProperty().bind(DRAWING_FLOOR_FIELD_MODE);
        deleteAllButton.disableProperty().bind(Bindings.not(DRAWING_FLOOR_FIELD_MODE));

        intensityLabel.setLabelFor(intensitySlider);
        intensitySlider.disableProperty().bind(DRAWING_FLOOR_FIELD_MODE);
        intensityTextField.disableProperty().bind(DRAWING_FLOOR_FIELD_MODE);

        intensitySlider.getParent().setOnScroll(event -> {
            // Use the scroll wheel to set the intensity value
            final double zoomFactor = 0.00075;
            double newValue = intensitySlider.getValue() + event.getDeltaY() * zoomFactor;

            if (newValue < 0.1) {
                newValue = 0.1;
            } else if (newValue > 1.0) {
                newValue = 1.0;
            }

            intensitySlider.setValue(newValue);
        });
    }
}
