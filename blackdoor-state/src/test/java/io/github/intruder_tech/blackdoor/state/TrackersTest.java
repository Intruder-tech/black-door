package io.github.intruder_tech.blackdoor.state;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackersTest {

    @Test
    void pingIdsStayInReservedRangeAndAckInOrder() {
        TransactionTracker t = new TransactionTracker(false, 8);
        int a = t.next(0);
        int b = t.next(1_000_000);
        assertThat(TransactionTracker.isOurs(a, false)).isTrue();
        assertThat(TransactionTracker.isOurs(5, false)).isFalse();
        assertThat(t.acknowledge(a, 40_000_000)).isEqualTo(TransactionTracker.Result.IN_ORDER);
        assertThat(t.rttMillis()).isEqualTo(40.0);
        assertThat(t.acknowledge(b, 41_000_000)).isEqualTo(TransactionTracker.Result.IN_ORDER);
        assertThat(t.acknowledgedSequence()).isEqualTo(2);
        assertThat(t.acknowledge(b, 42_000_000)).as("replayed").isEqualTo(TransactionTracker.Result.UNKNOWN);
        assertThat(t.acknowledge(12345, 0)).isEqualTo(TransactionTracker.Result.NOT_OURS);
    }

    @Test
    void skippedRepliesAreOutOfOrder() {
        TransactionTracker t = new TransactionTracker(false, 8);
        t.next(0);
        int second = t.next(0);
        assertThat(t.acknowledge(second, 10)).isEqualTo(TransactionTracker.Result.OUT_OF_ORDER);
        assertThat(t.outstanding()).isZero();
    }

    @Test
    void legacyIdsFitWindowConfirmationShorts() {
        TransactionTracker t = new TransactionTracker(true, 4);
        for (int i = 0; i < 40_000; i++) {
            int id = t.next(i);
            assertThat(id).isBetween((int) Short.MIN_VALUE, -16385);
        }
        assertThat(t.outstanding()).isEqualTo(4);
        assertThat(t.overflowed()).isEqualTo(40_000 - 4);
    }

    @Test
    void entityTrackerFollowsMovesAndIsBounded() {
        EntityTracker e = new EntityTracker(2);
        e.spawn(1, 0, 64, 0);
        e.move(1, 1.5, 0, -0.5);
        assertThat(e.position(1)).containsExactly(1.5, 64, -0.5);
        e.teleport(1, 10, 70, 10);
        assertThat(e.position(1)).containsExactly(10, 70, 10);
        e.spawn(2, 0, 0, 0);
        e.spawn(3, 0, 0, 0);
        assertThat(e.size()).isEqualTo(2);
        assertThat(e.untracked()).isEqualTo(1);
        assertThat(e.position(3)).isNull();
        e.remove(1);
        assertThat(e.position(1)).isNull();
        e.move(99, 1, 1, 1);
    }

    @Test
    void velocitiesAreAppliedOnlyAfterAcknowledgement() {
        VelocityQueue q = new VelocityQueue(4);
        q.add(0.1, 0.4, 0, 3);
        q.add(0.2, 0.4, 0, 5);
        q.acknowledgedUpTo(2);
        assertThat(q.pending()).isEqualTo(2);
        q.acknowledgedUpTo(3);
        assertThat(q.pending()).isEqualTo(1);
        assertThat(q.lastApplied()).containsExactly(0.1, 0.4, 0);
        q.acknowledgedUpTo(100);
        assertThat(q.applied()).isEqualTo(2);
    }
}
