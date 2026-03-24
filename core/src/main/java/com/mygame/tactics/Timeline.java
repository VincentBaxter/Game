package com.mygame.tactics;

import com.badlogic.gdx.utils.Array;
import java.util.Comparator;

public class Timeline {
    public enum EventType { CHARACTER_TURN }

    public static class TimelineEvent {
        public EventType type;
        public Character actor;
        
        public TimelineEvent(EventType type, Character actor) {
            this.type = type;
            this.actor = actor;
        }
    }

    private Array<TimelineEvent> eventQueue = new Array<>();

    /**
     * Initializes the timeline with all living characters.
     * Updated to match the CombatScreen call signature.
     */
    public void projectFutureEvents(Array<Character> units) {
        eventQueue.clear();
        for (Character c : units) {
            if (!c.isDead()) {
                eventQueue.add(new TimelineEvent(EventType.CHARACTER_TURN, c));
            }
        }
        sortTimeline();
    }

    /**
     * Sorts the timeline so the character with the LOWEST wait (closest to turn) is first.
     */
    public void sortTimeline() {
        eventQueue.sort(new Comparator<TimelineEvent>() {
            @Override
            public int compare(TimelineEvent e1, TimelineEvent e2) {
                // Lower currentWait moves first in a countdown system
                return Float.compare(e1.actor.getCurrentWait(), e2.actor.getCurrentWait());
            }
        });
    }

    /**
     * Returns the projected list of events for the UI to draw.
     * This fixes the "getEvents() is undefined" error.
     */
    public Array<TimelineEvent> getEvents() {
        return eventQueue;
    }
}