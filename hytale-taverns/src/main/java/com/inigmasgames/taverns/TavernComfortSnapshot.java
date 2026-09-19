package com.inigmasgames.taverns;

import java.util.List;

/** Event-invalidated snapshot of the registered Comfort objects in one Tavern volume. */
record TavernComfortSnapshot(
        ComfortScore score,
        List<RegisteredComfortObject> objects) {

    TavernComfortSnapshot {
        objects = List.copyOf(objects);
    }
}
