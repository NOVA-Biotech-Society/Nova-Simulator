package simulation.ml;

/**
 * High-level movement phase keyframes for the exoskeleton assistance cycle.
 */
public enum Keyframe {
    STANDING,
    DESCENDING,
    PROSTRATION,
    ASCENDING,
    RETURN_STANDING;

    public Keyframe next() {
        Keyframe[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Keyframe previous() {
        Keyframe[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}

