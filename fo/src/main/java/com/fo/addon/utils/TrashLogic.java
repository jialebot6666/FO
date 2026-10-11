package com.fo.addon.utils;

/**
 * AutoTrash 黑白名单纯决策逻辑（不依赖 Minecraft 运行时，可单元测试）。
 *
 * <p>参考 meteor-miku (GPL) 自动扔垃圾的设计思路自研，并增强黑白名单模式。
 */
public final class TrashLogic {

    public enum Mode {
        /** 黑名单：列表中的物品丢弃，其余保留 */
        BLACKLIST("黑名单"),
        /** 白名单：仅保留列表中的物品，其余全部丢弃 */
        WHITELIST("白名单");

        private final String display;

        Mode(String display) {
            this.display = display;
        }

        /** 前端 UI/下拉框/持久化均显示中文（Meteor EnumSetting 走 toString） */
        @Override
        public String toString() {
            return display;
        }
    }

    /** 附魔书过滤模式。 */
    public enum BookMode {
        /** 跟随名单：附魔书按普通物品走黑白名单逻辑 */
        FOLLOW_LIST("跟随名单"),
        /** 保等级：附魔等级 ≥ 阈值的书保留，低于阈值的当垃圾丢弃 */
        KEEP_LEVEL("保留等级达标的书"),
        /** 全部丢弃：附魔书一律当垃圾 */
        DROP_ALL("全部丢弃"),
        /** 全部保留：附魔书一律不丢 */
        KEEP_ALL("全部保留");

        private final String display;

        BookMode(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private TrashLogic() {}

    /**
     * 附魔书判定（参考 xiaohe66-meteor-lotus 自动分类「按附魔等级分价值」的思路自研）。
     *
     * @param mode            附魔书过滤模式
     * @param isEnchantedBook 该堆是否为附魔书
     * @param maxEnchantLevel 该堆附魔书里的最高附魔等级（非附魔书/无附魔传 0）
     * @param threshold       等级阈值（KEEP_LEVEL 模式用）
     * @return true=丢弃，false=保留，null=无附魔书特判，回落到普通名单逻辑
     */
    public static Boolean decideBook(BookMode mode, boolean isEnchantedBook, int maxEnchantLevel, int threshold) {
        if (!isEnchantedBook || mode == BookMode.FOLLOW_LIST) return null;
        return switch (mode) {
            case DROP_ALL -> true;
            case KEEP_ALL -> false;
            case KEEP_LEVEL -> maxEnchantLevel < threshold;
            default -> null;
        };
    }

    /**
     * 判断某物品是否应当被丢弃。
     *
     * @param mode          名单模式
     * @param listConfigured 物品列表是否非空（安全保护：列表为空时一律不丢，防止白名单误清空背包）
     * @param itemInList    物品是否在名单中
     */
    public static boolean shouldDrop(Mode mode, boolean listConfigured, boolean itemInList) {
        if (!listConfigured) return false;
        return mode == Mode.BLACKLIST ? itemInList : !itemInList;
    }
}
