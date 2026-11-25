package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;

/** Translates the outbound packets that keep the entity tracker and velocity queue in sync. */
final class OutboundTranslator {

    private OutboundTranslator() {
    }

    /** Returns null for packets Black Door does not track. */
    static ClientboundPacket translate(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity w = new WrapperPlayServerEntityVelocity(event);
            Vector3d v = w.getVelocity();
            return new ClientboundPacket.Velocity(w.getEntityId(), v.getX(), v.getY(), v.getZ());
        }
        if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(event);
            return spawn(w.getEntityId(), w.getPosition());
        }
        if (type == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer w = new WrapperPlayServerSpawnPlayer(event);
            return spawn(w.getEntityId(), w.getPosition());
        }
        if (type == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            WrapperPlayServerSpawnLivingEntity w = new WrapperPlayServerSpawnLivingEntity(event);
            return spawn(w.getEntityId(), w.getPosition());
        }
        if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove w = new WrapperPlayServerEntityRelativeMove(event);
            return new ClientboundPacket.EntityMove(w.getEntityId(), w.getDeltaX(), w.getDeltaY(), w.getDeltaZ());
        }
        if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation w = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            return new ClientboundPacket.EntityMove(w.getEntityId(), w.getDeltaX(), w.getDeltaY(), w.getDeltaZ());
        }
        if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport w = new WrapperPlayServerEntityTeleport(event);
            Vector3d p = w.getPosition();
            return new ClientboundPacket.EntityTeleport(w.getEntityId(), p.getX(), p.getY(), p.getZ());
        }
        if (type == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            WrapperPlayServerEntityPositionSync w = new WrapperPlayServerEntityPositionSync(event);
            Vector3d p = w.getValues().getPosition();
            return new ClientboundPacket.EntityTeleport(w.getId(), p.getX(), p.getY(), p.getZ());
        }
        if (type == PacketType.Play.Server.DESTROY_ENTITIES) {
            return new ClientboundPacket.EntityRemove(new WrapperPlayServerDestroyEntities(event).getEntityIds());
        }
        return null;
    }

    private static ClientboundPacket spawn(int entityId, Vector3d position) {
        return new ClientboundPacket.EntitySpawn(entityId, position.getX(), position.getY(), position.getZ());
    }
}
