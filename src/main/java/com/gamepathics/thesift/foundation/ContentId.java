package com.gamepathics.thesift.foundation;

public enum ContentId {
    SIFTSLATE("siftslate"),
    SOUL_BLOCK("soul_block"),
    CHAROITE("charoite"),
    SIFTITE("siftite"),
    ICHOR("ichor"),
    ICHOR_BOTTLE("ichor_bottle"),
    ICHOR_GLASS("ichor_glass"),
    ICHOR_GLASS_PANE("ichor_glass_pane"),
    CHAROITE_ITEM("charoite_item"),
    CHAROITE_ORE("charoite_ore"),
    SIFTITE_INGOT("siftite_ingot"),
    SIFTITE_ORE("siftite_ore"),
    SIFTITE_NUGGET("siftite_nugget"),
    SIFTITE_TEMPLATE("siftite_upgrade_smithing_template"),
    SIFTITE_SWORD("siftite_sword"),
    SIFTITE_AXE("siftite_axe"),
    SIFTITE_PICKAXE("siftite_pickaxe"),
    SIFTITE_SHOVEL("siftite_shovel"),
    SIFTITE_HOE("siftite_hoe"),
    SIFTITE_SPEAR("siftite_spear"),
    SIFTITE_HELMET("siftite_helmet"),
    SIFTITE_CHESTPLATE("siftite_chestplate"),
    SIFTITE_LEGGINGS("siftite_leggings"),
    SIFTITE_BOOTS("siftite_boots"),
    HEALTHY_SCULK("healthy_sculk"),
    OVERGROWN_WILLOW("overgrown_willow"),
    RESONANCE("resonance"),
    RIFT_EVENTS("rift-events"),
    BLUB("blub"),
    SINGER("singer"),
    ECHO_GOLEM("echo_golem"),
    RIFT("rift"),
    SIFT_DIMENSION("sift_dimension"),
    SIFTER("sifter");

    private final String key;

    ContentId(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
