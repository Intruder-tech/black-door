package io.github.intruder_tech.blackdoor.physics.replay;

import io.github.intruder_tech.blackdoor.protocol.world.BlockShapes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;

/**
 * Replays a trace tick by tick and predicts height only. Anything not modelled yet gives no
 * verdict with a reason; it is never counted as a match. Whether the player is on the ground is
 * worked out from the blocks, never taken from the client.
 *
 * <p>Not ready to judge a live player: server changes are applied when sent rather than when the
 * client acknowledges them, the client's own block placing is not seen, and a claimed elytra
 * flight cannot be checked.
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
    /** How long a server velocity may take to show up before it is forgotten. */
    private static final int VELOCITY_TICKS = 40;
    /** Attributes that change jumping or the player's box, with their normal values. */
    private static final Map<String, Double> NORMAL_ATTRIBUTES =
            Map.of("gravity", GRAVITY, "jump_strength", JUMP, "step_height", STEP_HEIGHT, "scale", 1.0);

    public record Result(int matches, int misses, Map<String, Integer> noVerdict, int airborne, int airborneJudged,
                         List<String> firstMisses) {
    }

    private final BlockShapes shapes;
    private final TraceWorld world = new TraceWorld();
    private final Map<String, Integer> noVerdict = new TreeMap<>();
    private final List<String> firstMisses = new ArrayList<>();
    private final Set<String> changedAttributes = new HashSet<>();

    private boolean placed;
    private double x;
    private double y;
    private double z;
    private boolean ground;
    /** Vertical speed the client is expected to move with next; null until known. */
    private Double next;
    private Double velocity;
    private int velocityTicks;

    private boolean creative;
    private boolean flying;
    private boolean flightAllowed;
    private boolean gliding;
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
            if (!replay.creative && !replay.flying && tick.move() != null && !replay.ground) {
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
            case "abilities" -> {
                flightAllowed = Boolean.TRUE.equals(event.get("allowed"));
                flying = flightAllowed && Boolean.TRUE.equals(event.get("flying"));
            }
            case "flying" -> flying = flightAllowed && Boolean.TRUE.equals(event.get("flying"));
            case "velocity" -> {
                velocity = (Double) event.get("y");
                velocityTicks = VELOCITY_TICKS;
            }
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
            case "attribute" -> attribute(name, event);
            case "effect" -> effect(name, true, event.get("amplifier"));
            case "effectRemoved" -> effect(name, false, null);
            default -> {
            }
        }
    }

    private void attribute(String name, Map<String, Object> event) {
        NORMAL_ATTRIBUTES.forEach((attribute, normal) -> {
            if (name.endsWith(attribute)) {
                boolean modified = event.get("modifiers") instanceof List<?> list && !list.isEmpty();
                if (modified || Math.abs((Double) event.get("base") - normal) > 1e-6) {
                    changedAttributes.add(attribute);
                } else {
                    changedAttributes.remove(attribute);
                }
            }
        });
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
        double height = sneaking ? 1.5 : 1.8;
        if (!placed) {
            if (positionSent) {
                boolean onGround = supported(newX, newY, newZ, height);
                accept(newX, newY, newZ, onGround, onGround ? after(0) : null);
                placed = true;
            }
            return skip("first");
        }
        Boolean claimed = move == null ? null : Boolean.TRUE.equals(move.get("ground"));
        double dy = newY - y;
        if (velocity != null && --velocityTicks < 0) {
            velocity = null;
        }
        // Through a fluid or special block, or an unknown one: the next speed cannot be told either.
        boolean untold = world.clip(shapes, x, y, z, height, dy).isEmpty()
                || !world.plain(shapes, newX, newY, newZ, height);
        String reason = reason();
        if (reason == null && untold) {
            reason = "block";
        }
        if (reason == null && !world.free(shapes, x, y, z, height)) {
            reason = "pose";
        }
        if (reason == null && next == null && velocity == null && !(ground && jumpHeld)) {
            // Even with an unknown speed, nothing lifts a player further than a jump does.
            if (dy > JUMP + JUMP_PER_LEVEL * jumpBoost + TOLERANCE) {
                return miss(number, newX, newY, newZ, height, dy, Double.NaN);
            }
            reason = "first";
        }
        if (reason != null) {
            boolean onGround = !untold && supported(newX, newY, newZ, height);
            accept(newX, newY, newZ, onGround, untold ? null : after(onGround ? 0 : dy));
            return skip(reason);
        }

        List<Double> attempts = new ArrayList<>();
        if (next != null) {
            attempts.add(next);
        }
        if (velocity != null) {
            attempts.add(velocity);
        }
        if (ground && jumpHeld) {
            attempts.add(JUMP + JUMP_PER_LEVEL * jumpBoost);
        }
        double closest = Double.NaN;
        for (double attempt : attempts) {
            var clip = world.clip(shapes, x, y, z, height, attempt);
            if (clip.isEmpty()) {
                accept(newX, newY, newZ, false, null);
                return skip("block");
            }
            boolean landed = clip.get().collided() && attempt <= 0;
            boolean stepped = landed && steppedUp(newX, newZ, y + clip.get().dy(), dy);
            if (stepped || Math.abs(clip.get().dy() - dy) <= TOLERANCE) {
                if (claimed != null && claimed != (landed || stepped)) {
                    closest = clip.get().dy();
                    continue;
                }
                if (velocity != null && velocity == attempt) {
                    velocity = null;
                }
                accept(newX, newY, newZ, landed || stepped, after(clip.get().collided() || stepped ? 0 : attempt));
                return true;
            }
            closest = clip.get().dy();
        }
        return miss(number, newX, newY, newZ, height, dy, closest);
    }

    private boolean miss(int number, double newX, double newY, double newZ, double height, double dy,
                         double expected) {
        boolean onGround = supported(newX, newY, newZ, height);
        if (firstMisses.size() < 8) {
            firstMisses.add(String.format("tick %d at %.3f,%.3f,%.3f: moved %.5f, expected %.5f (ground %s->%s)",
                    number, newX, newY, newZ, dy, expected, ground, onGround));
        }
        accept(newX, newY, newZ, onGround, after(onGround ? 0 : dy));
        return false;
    }

    private String reason() {
        if (creative) {
            return "creative";
        }
        if (flying) {
            return "flying";
        }
        if (gliding) {
            gliding = !ground;
            return "elytra";
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
        if (!changedAttributes.isEmpty()) {
            return "attribute";
        }
        return null;
    }

    /** Something solid directly under the player's box. */
    private boolean supported(double atX, double atY, double atZ, double height) {
        return world.clip(shapes, atX, atY, atZ, height, -TOLERANCE).map(TraceWorld.Clip::collided).orElse(false);
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
    private static double after(double speed) {
        double following = (speed - GRAVITY) * DRAG;
        return Math.abs(following) < NEGLIGIBLE ? 0 : following;
    }

    private void accept(double newX, double newY, double newZ, boolean onGround, Double following) {
        x = newX;
        y = newY;
        z = newZ;
        ground = onGround;
        next = following;
    }
}
