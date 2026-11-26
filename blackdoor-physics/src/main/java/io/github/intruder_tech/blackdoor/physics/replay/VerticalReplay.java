package io.github.intruder_tech.blackdoor.physics.replay;

import io.github.intruder_tech.blackdoor.protocol.world.BlockShapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * Replays a trace tick by tick and predicts height only. Anything not modelled yet gives no
 * verdict with a reason; it is never counted as a match.
 */
public final class VerticalReplay {

    /** Accepted difference between predicted and reported vertical movement, in blocks. */
    static final double TOLERANCE = 1e-4;
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double JUMP = 0.42;
    private static final double JUMP_PER_LEVEL = 0.1;
    /** The client drops vertical speeds smaller than this. */
    private static final double NEGLIGIBLE = 0.003;
    private static final double STEP_HEIGHT = 0.6;

    public record Result(int matches, int misses, Map<String, Integer> noVerdict, int airborne, int airborneJudged,
                         List<String> firstMisses) {
    }

    private final BlockShapes shapes;
    private final TraceWorld world = new TraceWorld();
    private final Map<String, Integer> noVerdict = new TreeMap<>();
    private final List<String> firstMisses = new ArrayList<>();

    private boolean placed;
    private double x;
    private double y;
    private double z;
    private boolean ground;
    /** Vertical speed the client is expected to move with next; null until known. */
    private Double next;

    private boolean creative;
    private boolean flying;
    private boolean gliding;
    private boolean knockedBack;
    private boolean awaitingConfirm;
    private int settling;
    private boolean jumpHeld;
    private boolean sneaking;
    private int jumpBoost;
    private boolean levitation;
    private boolean slowFalling;

    private VerticalReplay(BlockShapes shapes) {
        this.shapes = shapes;
    }

    public static Result run(Trace trace, BlockShapes shapes) {
        VerticalReplay replay = new VerticalReplay(shapes);
        int matches = 0;
        int misses = 0;
        int airborne = 0;
        int judged = 0;
        int number = 0;
        for (Trace.Tick tick : trace.ticks()) {
            number++;
            tick.events().forEach(replay::apply);
            Boolean verdict = replay.tick(tick.move(), number);
            boolean inAir = !replay.creative && !replay.flying && tick.move() != null
                    && Boolean.FALSE.equals(tick.move().get("ground"));
            if (inAir) {
                airborne++;
                if (verdict != null) {
                    judged++;
                }
            }
            if (Boolean.TRUE.equals(verdict)) {
                matches++;
            } else if (Boolean.FALSE.equals(verdict)) {
                misses++;
            }
        }
        return new Result(matches, misses, replay.noVerdict, airborne, judged, replay.firstMisses);
    }

    private void apply(Map<String, Object> event) {
        world.apply(event);
        String name = String.valueOf(event.get("name"));
        switch (String.valueOf(event.get("k"))) {
            case "gamemode" -> creative = Boolean.TRUE.equals(event.get("creative"));
            case "flying", "abilities" -> flying = Boolean.TRUE.equals(event.get("flying"));
            case "velocity" -> knockedBack = true;
            case "teleport" -> awaitingConfirm = true;
            case "confirm" -> {
                awaitingConfirm = false;
                settling = 2;
            }
            case "input" -> {
                jumpHeld = Boolean.TRUE.equals(event.get("jump"));
                sneaking = Boolean.TRUE.equals(event.get("sneak"));
            }
            case "action" -> gliding |= name.contains("ELYTRA") || name.contains("GLIDING");
            case "effect" -> effect(name, true, event.get("amplifier"));
            case "effectRemoved" -> effect(name, false, null);
            default -> {
            }
        }
    }

    private void effect(String name, boolean on, Object amplifier) {
        if (name.endsWith("jump_boost")) {
            jumpBoost = on ? ((Double) amplifier).intValue() + 1 : 0;
        } else if (name.endsWith("levitation")) {
            levitation = on;
        } else if (name.endsWith("slow_falling")) {
            slowFalling = on;
        }
    }

    /** @return true for a match, false for a miss, null for no verdict */
    private Boolean tick(Map<String, Object> move, int number) {
        boolean positionSent = move != null && Boolean.TRUE.equals(move.get("pos"));
        double newX = positionSent ? (Double) move.get("x") : x;
        double newY = positionSent ? (Double) move.get("y") : y;
        double newZ = positionSent ? (Double) move.get("z") : z;
        boolean newGround = move == null ? ground : Boolean.TRUE.equals(move.get("ground"));
        if (!placed) {
            if (positionSent) {
                accept(newX, newY, newZ, newGround, newGround ? after(0, true) : null);
                placed = true;
            }
            return skip("first");
        }
        double dy = newY - y;
        double height = sneaking ? 1.5 : 1.8;
        String reason = reason(newGround);
        // Through a fluid or special block, or an unknown one: the next speed cannot be told either.
        boolean untold = world.clip(shapes, x, y, z, height, dy).isEmpty()
                || !world.plain(shapes, newX, newY, newZ, height);
        if (reason == null && untold) {
            reason = "block";
        }
        if (reason == null && next == null) {
            reason = "first";
        }
        if (reason != null) {
            accept(newX, newY, newZ, newGround, untold ? null : after(dy, newGround));
            return skip(reason);
        }

        List<Double> attempts = new ArrayList<>();
        attempts.add(next);
        if (ground && jumpHeld) {
            attempts.add(JUMP + JUMP_PER_LEVEL * jumpBoost);
        }
        double closest = Double.NaN;
        for (double attempt : attempts) {
            var clip = world.clip(shapes, x, y, z, height, attempt);
            if (clip.isEmpty()) {
                accept(newX, newY, newZ, newGround, null);
                return skip("block");
            }
            if (Math.abs(clip.get().dy() - dy) <= TOLERANCE) {
                accept(newX, newY, newZ, newGround, after(clip.get().collided() ? 0 : attempt, false));
                return true;
            }
            if (clip.get().collided() && attempt <= 0 && newGround && steppedUp(newX, newZ, y + clip.get().dy(), dy)) {
                accept(newX, newY, newZ, true, after(0, true));
                return true;
            }
            closest = clip.get().dy();
        }
        if (firstMisses.size() < 8) {
            firstMisses.add(String.format("tick %d at %.3f,%.3f,%.3f: moved %.5f, expected %.5f (ground %s->%s)",
                    number, newX, newY, newZ, dy, closest, ground, newGround));
        }
        accept(newX, newY, newZ, newGround, after(dy, newGround));
        return false;
    }

    private String reason(boolean newGround) {
        if (creative) {
            return "creative";
        }
        if (flying) {
            return "flying";
        }
        if (gliding) {
            gliding = !newGround;
            return "elytra";
        }
        if (knockedBack) {
            knockedBack = !newGround;
            return "velocity";
        }
        if (awaitingConfirm || settling > 0) {
            settling = Math.max(0, settling - 1);
            return "teleport";
        }
        if (levitation) {
            return "effect:levitation";
        }
        if (slowFalling) {
            return "effect:slow_falling";
        }
        return null;
    }

    /** The client moves onto a block up to 0.6 higher than where it would otherwise have stopped. */
    private boolean steppedUp(double newX, double newZ, double from, double dy) {
        OptionalDouble top = world.top(shapes, newX, newZ, from, from + STEP_HEIGHT);
        return top.isPresent() && Math.abs(top.getAsDouble() - y - dy) <= TOLERANCE;
    }

    private Boolean skip(String reason) {
        noVerdict.merge(reason, 1, Integer::sum);
        return null;
    }

    /** The speed for the next tick, after one that moved with {@code speed}. */
    private static double after(double speed, boolean onGround) {
        double following = ((onGround ? 0 : speed) - GRAVITY) * DRAG;
        return Math.abs(following) < NEGLIGIBLE ? 0 : following;
    }

    private void accept(double newX, double newY, double newZ, boolean newGround, Double following) {
        x = newX;
        y = newY;
        z = newZ;
        ground = newGround;
        next = following;
    }
}
