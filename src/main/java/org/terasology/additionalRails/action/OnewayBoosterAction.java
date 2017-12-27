/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.additionalRails.action;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.additionalRails.components.OnewayBoosterRailComponent;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.math.Rotation;
import org.terasology.math.geom.Vector3f;
import org.terasology.minecarts.blocks.RailComponent;
import org.terasology.minecarts.blocks.RailsUpdateFamily;
import org.terasology.minecarts.components.RailVehicleComponent;
import org.terasology.registry.In;
import org.terasology.segmentedpaths.components.PathFollowerComponent;
import org.terasology.segmentedpaths.events.OnExitSegment;
import org.terasology.segmentedpaths.events.OnVisitSegment;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.BlockManager;

import java.util.Objects;
import java.util.Set;

@RegisterSystem(RegisterMode.AUTHORITY)
public class OnewayBoosterAction extends BaseComponentSystem implements UpdateSubscriberSystem {
    private Set<RailCart> entities = Sets.newHashSet();
    private static final int PUSH_RATE = 20, VELOCITY_LENGTH_MAX = 25;
    @In
    private BlockManager blockManager;

    private static final Logger logger = LoggerFactory.getLogger(OnewayBoosterAction.class);

    @ReceiveEvent(components = {OnewayBoosterRailComponent.class, RailComponent.class})
    public void onEnterBoosterSegment(OnVisitSegment event, EntityRef entity) { //entity is the rail and event.getSegmentEntity() the cart
        entities.add(new RailCart(entity, event.getSegmentEntity()));
    }

    @ReceiveEvent(components = {OnewayBoosterRailComponent.class, RailComponent.class})
    public void onExitBoosterSegment(OnExitSegment event, EntityRef entity) {
        entities.remove(new RailCart(entity, event.getSegmentEntity()));
    }

    @Override
    public void update(float delta) {
        entities.removeIf(e -> !e.cart.exists() || !e.cart.hasComponent(RailVehicleComponent.class) || !e.cart.hasComponent(PathFollowerComponent.class)
                || !e.rail.exists() || !e.rail.hasComponent(OnewayBoosterRailComponent.class));
        for(RailCart rc : entities) {
            Block block = rc.rail.getComponent(BlockComponent.class).getBlock();
            RailsUpdateFamily family = (RailsUpdateFamily) block.getBlockFamily();
            Rotation rotation = family.getRotationFor(block.getURI());
            float yaw = rotation.getYaw().getRadians();
            Vector3f railDirection = new Vector3f((float) Math.cos(yaw), 0, (float) Math.sin(yaw)); //https://stackoverflow.com/a/1568687
            if(family == blockManager.getBlockFamily("AdditionalRails:OnewayBoosterRailInverted"))
                railDirection.invert();
            //Currently the direction the tiles point to is the opposite of the real direction they send carts to on Z axis.
            //TODO: fix this fundamentally
            railDirection.mulZ(-1);
            push(rc.cart, railDirection.mul(PUSH_RATE * delta));
        }
    }
    /**Adds {@code thisMuch} to the velocity of {@code ref}'s {@code RailVehicleComponent}
     * if it doesn't exceed {@link #VELOCITY_LENGTH_MAX}.*/
    private void push(EntityRef ref, Vector3f thisMuch) {
        RailVehicleComponent railVehicleComponent = ref.getComponent(RailVehicleComponent.class);
        Vector3f velocity = railVehicleComponent.velocity;
        if(velocity.lengthSquared() < VELOCITY_LENGTH_MAX) {
            velocity.add(thisMuch);
            ref.saveComponent(railVehicleComponent);
        }
    }
    private class RailCart {
        EntityRef rail, cart;
        RailCart(EntityRef rail, EntityRef cart) {
            this.rail = rail;
            this.cart = cart;
        }
        @Override
        public boolean equals(Object obj) {
            if(obj instanceof RailCart) {
                RailCart other = (RailCart) obj;
                return this.rail == other.rail && this.cart == other.cart;
            }
            return false;
        }
        @Override
        public int hashCode() {
            return Objects.hash(rail, cart);
        }
    }
}