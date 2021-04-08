package com.crowdsimulation.model.core.environment.station.patch.patchobject.passable.goal.blockable;

import com.crowdsimulation.model.core.agent.passenger.PassengerMovement;
import com.crowdsimulation.model.core.environment.station.patch.Patch;
import com.crowdsimulation.model.core.environment.station.patch.floorfield.FloorField;
import com.crowdsimulation.model.core.environment.station.patch.floorfield.QueueObject;
import com.crowdsimulation.model.core.environment.station.patch.floorfield.headful.QueueingFloorField;

import java.util.ArrayList;
import java.util.List;

public class Turnstile extends BlockableAmenity {
    // Denotes the current mode of this turnstile
    private TurnstileMode turnstileMode;

    // Denotes the floor field states needed to access the floor fields of this security gate
    private final QueueingFloorField.FloorFieldState turnstileFloorFieldStateBoarding;
    private final QueueingFloorField.FloorFieldState turnstileFloorFieldStateAlighting;

    public Turnstile(
            Patch patch,
            boolean enabled,
            int waitingTime,
            boolean blockEntry,
            TurnstileMode turnstileMode
    ) {
        super(
                patch,
                enabled,
                waitingTime,
                new QueueObject(),
                blockEntry
        );

        // Initialize this turnstile's floor field states
        this.turnstileFloorFieldStateBoarding = new QueueingFloorField.FloorFieldState(
                PassengerMovement.Direction.BOARDING,
                PassengerMovement.State.IN_QUEUE,
                this
        );

        this.turnstileFloorFieldStateAlighting = new QueueingFloorField.FloorFieldState(
                PassengerMovement.Direction.ALIGHTING,
                PassengerMovement.State.IN_QUEUE,
                this
        );

        this.turnstileMode = turnstileMode;

        // Add a blank floor field
        QueueingFloorField floorField = new QueueingFloorField(this);

        // Using the floor field states defined earlier, create the floor fields
        this.getQueueObject().getFloorFields().put(this.turnstileFloorFieldStateBoarding, floorField);
        this.getQueueObject().getFloorFields().put(this.turnstileFloorFieldStateAlighting, floorField);
    }

    public TurnstileMode getTurnstileMode() {
        return turnstileMode;
    }

    public void setTurnstileMode(TurnstileMode turnstileMode) {
        this.turnstileMode = turnstileMode;
    }

    public QueueingFloorField.FloorFieldState getTurnstileFloorFieldStateBoarding() {
        return turnstileFloorFieldStateBoarding;
    }

    public QueueingFloorField.FloorFieldState getTurnstileFloorFieldStateAlighting() {
        return turnstileFloorFieldStateAlighting;
    }

    @Override
    public String toString() {
        return "Turnstile";
    }

    @Override
    public List<QueueingFloorField.FloorFieldState> retrieveFloorFieldState() {
        List<QueueingFloorField.FloorFieldState> floorFieldStates = new ArrayList<>();

        floorFieldStates.add(this.turnstileFloorFieldStateBoarding);
        floorFieldStates.add(this.turnstileFloorFieldStateAlighting);

        return floorFieldStates;
    }

    @Override
    public QueueingFloorField retrieveFloorField(QueueingFloorField.FloorFieldState floorFieldState) {
        return this.getQueueObject().getFloorFields().get(
                floorFieldState
        );
    }

    @Override
    // Denotes whether the floor field for this turnstile is complete
    public boolean isFloorFieldsComplete() {
        QueueingFloorField boardingFloorField;
        QueueingFloorField alightingFloorField;

        boolean boardingCheck;
        boolean alightingCheck;

        boardingFloorField = retrieveFloorField(turnstileFloorFieldStateBoarding);
        alightingFloorField = retrieveFloorField(turnstileFloorFieldStateAlighting);

        boardingCheck = boardingFloorField.getApex() != null && !boardingFloorField.getAssociatedPatches().isEmpty();
        alightingCheck = alightingFloorField.getApex() != null && !alightingFloorField.getAssociatedPatches().isEmpty();

        // The floor field of this queueable is complete when, for both floor fields, there are floor fields present and
        // apex patches are present
        return boardingCheck && alightingCheck;
    }

    // Clear all floor fields of this turnstile
    @Override
    public void clearFloorFields() {
        QueueingFloorField boardingFloorField = retrieveFloorField(this.turnstileFloorFieldStateBoarding);
        QueueingFloorField alightingFloorField = retrieveFloorField(this.turnstileFloorFieldStateAlighting);

        QueueingFloorField.clearFloorField(
                boardingFloorField,
                this.turnstileFloorFieldStateBoarding
        );

        QueueingFloorField.clearFloorField(
                alightingFloorField,
                this.turnstileFloorFieldStateAlighting
        );
    }

    // Turnstile factory
    public static class TurnstileFactory extends AmenityFactory {
        @Override
        public Turnstile create(Object... objects) {
            return new Turnstile(
                    (Patch) objects[0],
                    (boolean) objects[1],
                    (int) objects[2],
                    (boolean) objects[3],
                    (TurnstileMode) objects[4]
            );
        }
    }

    // Lists the possible modes of this turnstile
    public enum TurnstileMode {
        BOARDING,
        ALIGHTING,
        BIDIRECTIONAL
    }
}
