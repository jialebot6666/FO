package com.fo.addon.elytra.hell;

import java.util.EnumSet;
import java.util.Set;

public final class HellFlightStateMachine {
    public enum Phase {
        IDLE("空闲"),
        PREPARE("准备"),
        SUPPLY("补给"),
        TAKEOFF("起飞"),
        FLYING("飞行"),
        LANDING_SEARCH("寻找着陆点"),
        LANDING("着陆"),
        REPAIR_ELYTRA("修复鞘翅"),
        COMPLETE("完成"),
        STOPPING("正在停止"),
        FAILED("失败");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum RepairStep {
        LANDING("降落修复"),
        PREPARE("准备经验瓶"),
        THROWING("投掷经验瓶"),
        RETURN("整理经验瓶"),
        RESUME("恢复飞行");

        private final String label;

        RepairStep(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SupplyStep {
        CENTER("居中"),
        SORT("整理背包"),
        EXTINGUISH("扑灭火焰"),
        FIND_ENDER_SPOT("寻找末影箱位置"),
        PLACE_ENDER("放置末影箱"),
        OPEN_ENDER("打开末影箱"),
        SELECT_BOXES("选择补给盒"),
        WITHDRAW_BOX("取出补给盒"),
        PLACE_BOX("放置补给盒"),
        OPEN_BOX("打开补给盒"),
        TRANSFER("转移物资"),
        MINE_BOX("挖回补给盒"),
        PICKUP_BOX("拾取补给盒"),
        RETURN_BOX("归还补给盒"),
        CLOSE_ENDER("关闭末影箱"),
        MINE_ENDER("挖回末影箱"),
        PICKUP_ENDER("拾取末影箱"),
        DONE("补给完成");

        private final String label;

        SupplyStep(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Set<Phase> ACTIVE = EnumSet.of(
        Phase.PREPARE, Phase.SUPPLY, Phase.TAKEOFF, Phase.FLYING,
        Phase.LANDING_SEARCH, Phase.LANDING, Phase.REPAIR_ELYTRA
    );

    private Phase phase = Phase.IDLE;
    private SupplyStep supplyStep = SupplyStep.CENTER;
    private RepairStep repairStep = RepairStep.LANDING;
    private int segment;

    public Phase phase() {
        return phase;
    }

    public SupplyStep supplyStep() {
        return supplyStep;
    }

    public RepairStep repairStep() {
        return repairStep;
    }

    public int segment() {
        return segment;
    }

    public boolean active() {
        return ACTIVE.contains(phase);
    }

    public boolean start() {
        if (phase != Phase.IDLE && phase != Phase.COMPLETE && phase != Phase.FAILED) return false;
        segment = 0;
        supplyStep = SupplyStep.CENTER;
        phase = Phase.PREPARE;
        return true;
    }

    public boolean beginSupply() {
        if (phase != Phase.PREPARE && phase != Phase.LANDING && phase != Phase.TAKEOFF) return false;
        supplyStep = SupplyStep.CENTER;
        phase = Phase.SUPPLY;
        return true;
    }

    public boolean setSupplyStep(SupplyStep next) {
        if (phase != Phase.SUPPLY || next == null || next == supplyStep || !canAdvanceSupply(next)) return false;
        supplyStep = next;
        return true;
    }

    private boolean canAdvanceSupply(SupplyStep next) {
        return switch (supplyStep) {
            case CENTER -> next == SupplyStep.SORT;
            case SORT -> next == SupplyStep.DONE || next == SupplyStep.EXTINGUISH;
            case EXTINGUISH -> next == SupplyStep.FIND_ENDER_SPOT;
            case FIND_ENDER_SPOT -> next == SupplyStep.PLACE_ENDER;
            case PLACE_ENDER -> next == SupplyStep.OPEN_ENDER;
            case OPEN_ENDER -> next == SupplyStep.SELECT_BOXES;
            case SELECT_BOXES -> next == SupplyStep.WITHDRAW_BOX;
            case WITHDRAW_BOX -> next == SupplyStep.PLACE_BOX;
            case PLACE_BOX -> next == SupplyStep.OPEN_BOX;
            case OPEN_BOX -> next == SupplyStep.TRANSFER;
            case TRANSFER -> next == SupplyStep.MINE_BOX;
            case MINE_BOX -> next == SupplyStep.PICKUP_BOX;
            case PICKUP_BOX -> next == SupplyStep.RETURN_BOX;
            case RETURN_BOX -> next == SupplyStep.WITHDRAW_BOX || next == SupplyStep.CLOSE_ENDER;
            case CLOSE_ENDER -> next == SupplyStep.MINE_ENDER;
            case MINE_ENDER -> next == SupplyStep.PICKUP_ENDER;
            case PICKUP_ENDER -> next == SupplyStep.DONE;
            case DONE -> false;
        };
    }

    public boolean supplyDone() {
        if (phase != Phase.SUPPLY || supplyStep != SupplyStep.DONE) return false;
        phase = Phase.TAKEOFF;
        return true;
    }

    public boolean beginFlight() {
        if (phase != Phase.TAKEOFF) return false;
        phase = Phase.FLYING;
        return true;
    }

    public boolean requestLandingSearch() {
        if (phase != Phase.FLYING && phase != Phase.TAKEOFF) return false;
        phase = Phase.LANDING_SEARCH;
        return true;
    }

    public boolean beginLanding() {
        if (phase != Phase.LANDING_SEARCH) return false;
        phase = Phase.LANDING;
        return true;
    }

    public boolean landingFailed() {
        if (phase != Phase.LANDING) return false;
        phase = Phase.FLYING;
        return true;
    }

    public boolean requestRepair() {
        if (phase != Phase.FLYING) return false;
        repairStep = RepairStep.LANDING;
        phase = Phase.REPAIR_ELYTRA;
        return true;
    }

    public boolean setRepairStep(RepairStep next) {
        if (phase != Phase.REPAIR_ELYTRA || next == null || next == repairStep || !canAdvanceRepair(next)) return false;
        repairStep = next;
        return true;
    }

    private boolean canAdvanceRepair(RepairStep next) {
        return switch (repairStep) {
            case LANDING -> next == RepairStep.PREPARE;
            case PREPARE -> next == RepairStep.THROWING;
            case THROWING -> next == RepairStep.RETURN;
            case RETURN -> next == RepairStep.RESUME;
            case RESUME -> false;
        };
    }

    public boolean repairNeedsSupply() {
        if (phase != Phase.REPAIR_ELYTRA || repairStep != RepairStep.PREPARE) return false;
        phase = Phase.LANDING;
        return true;
    }

    public boolean resumeFlight() {
        if (phase != Phase.REPAIR_ELYTRA || repairStep != RepairStep.RESUME) return false;
        phase = Phase.FLYING;
        return true;
    }

    public boolean landed(boolean arrived) {
        if (phase != Phase.LANDING) return false;
        if (arrived) {
            phase = Phase.COMPLETE;
        } else {
            segment++;
            phase = Phase.PREPARE;
            supplyStep = SupplyStep.CENTER;
        }
        return true;
    }

    public boolean fail() {
        if (!active()) return false;
        phase = Phase.FAILED;
        return true;
    }

    public boolean stop() {
        if (phase == Phase.IDLE || phase == Phase.STOPPING) return false;
        phase = Phase.STOPPING;
        return true;
    }

    public boolean finishStop() {
        if (phase != Phase.STOPPING && phase != Phase.COMPLETE && phase != Phase.FAILED) return false;
        phase = Phase.IDLE;
        supplyStep = SupplyStep.CENTER;
        repairStep = RepairStep.LANDING;
        return true;
    }

    public String statusText() {
        if (phase == Phase.SUPPLY) return supplyStep.label();
        if (phase == Phase.REPAIR_ELYTRA) return repairStep.label();
        return phase.label();
    }
}
