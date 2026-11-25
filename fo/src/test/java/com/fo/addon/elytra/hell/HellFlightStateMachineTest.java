package com.fo.addon.elytra.hell;

import com.fo.addon.elytra.hell.HellFlightStateMachine.Phase;
import com.fo.addon.elytra.hell.HellFlightStateMachine.RepairStep;
import com.fo.addon.elytra.hell.HellFlightStateMachine.SupplyStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HellFlightStateMachineTest {
    @Test
    void completesFullSupplyFlightLandingLoop() {
        HellFlightStateMachine machine = new HellFlightStateMachine();
        assertTrue(machine.start());
        assertEquals(Phase.PREPARE, machine.phase());
        assertTrue(machine.beginSupply());

        for (SupplyStep step : SupplyStep.values()) {
            if (step == SupplyStep.CENTER) continue;
            assertTrue(machine.setSupplyStep(step), step.name());
        }
        assertTrue(machine.supplyDone());
        assertEquals(Phase.TAKEOFF, machine.phase());
        assertTrue(machine.beginFlight());
        assertTrue(machine.requestLandingSearch());
        assertTrue(machine.beginLanding());
        assertTrue(machine.landed(false));
        assertEquals(Phase.PREPARE, machine.phase());
        assertEquals(1, machine.segment());

        assertTrue(machine.beginSupply());
        assertTrue(machine.setSupplyStep(SupplyStep.SORT));
        assertTrue(machine.setSupplyStep(SupplyStep.DONE));
        assertTrue(machine.supplyDone());
        assertTrue(machine.beginFlight());
        assertTrue(machine.requestLandingSearch());
        assertTrue(machine.beginLanding());
        assertTrue(machine.landed(true));
        assertEquals(Phase.COMPLETE, machine.phase());
        assertTrue(machine.finishStop());
        assertEquals(Phase.IDLE, machine.phase());
    }

    @Test
    void rejectsInvalidTransitions() {
        HellFlightStateMachine machine = new HellFlightStateMachine();
        assertFalse(machine.beginFlight());
        assertFalse(machine.beginLanding());
        assertFalse(machine.landed(true));
        assertTrue(machine.start());
        assertFalse(machine.landed(true));
        assertTrue(machine.beginSupply());
        assertFalse(machine.setSupplyStep(SupplyStep.MINE_ENDER));
        assertTrue(machine.fail());
        assertEquals(Phase.FAILED, machine.phase());
        assertFalse(machine.fail());
        assertTrue(machine.finishStop());
    }

    @Test
    void xpRepairCycleReturnsToFlying() {
        HellFlightStateMachine machine = new HellFlightStateMachine();
        machine.start();
        machine.beginSupply();
        machine.setSupplyStep(SupplyStep.SORT);
        machine.setSupplyStep(SupplyStep.DONE);
        machine.supplyDone();
        machine.beginFlight();
        assertTrue(machine.requestRepair());
        assertEquals(Phase.REPAIR_ELYTRA, machine.phase());
        assertTrue(machine.setRepairStep(RepairStep.PREPARE));
        assertTrue(machine.setRepairStep(RepairStep.THROWING));
        assertTrue(machine.setRepairStep(RepairStep.RETURN));
        assertTrue(machine.setRepairStep(RepairStep.RESUME));
        assertTrue(machine.resumeFlight());
        assertEquals(Phase.FLYING, machine.phase());
    }

    @Test
    void repairCanFallBackToSupplyLanding() {
        HellFlightStateMachine machine = new HellFlightStateMachine();
        machine.start();
        machine.beginSupply();
        machine.setSupplyStep(SupplyStep.SORT);
        machine.setSupplyStep(SupplyStep.DONE);
        machine.supplyDone();
        machine.beginFlight();
        machine.requestRepair();
        machine.setRepairStep(RepairStep.PREPARE);
        assertTrue(machine.repairNeedsSupply());
        assertEquals(Phase.LANDING, machine.phase());
    }

    @Test
    void landingFailureReturnsToFlying() {
        HellFlightStateMachine machine = new HellFlightStateMachine();
        machine.start();
        machine.beginSupply();
        machine.setSupplyStep(SupplyStep.SORT);
        machine.setSupplyStep(SupplyStep.DONE);
        machine.supplyDone();
        machine.beginFlight();
        machine.requestLandingSearch();
        machine.beginLanding();
        assertTrue(machine.landingFailed());
        assertEquals(Phase.FLYING, machine.phase());
    }
}
