package com.cyanrbt.walkbuddy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Explicit camera/detection stop controls retain priority when a system offers fewer slots. */
final class PipMenu {
    enum Item { STOP_PREVIEW, STOP_DETECTION, ENABLE_SOUND, DISABLE_SOUND }
    static List<Item> items(int capacity,SessionState.Phase phase,boolean detection,boolean sound) {
        if(capacity<2 || phase!=SessionState.Phase.LIVE || !detection)
            return Collections.singletonList(Item.STOP_PREVIEW);
        if(capacity<3)return Arrays.asList(Item.STOP_PREVIEW,Item.STOP_DETECTION);
        return Arrays.asList(Item.STOP_PREVIEW,Item.STOP_DETECTION,sound?Item.DISABLE_SOUND:Item.ENABLE_SOUND);
    }
}
