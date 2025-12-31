package sh.joey.mc.pet.entity;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages a group of armor stands that together form a pet's visual appearance.
 * The pet consists of a hidden base entity (chicken) and multiple armor stands
 * positioned around it, each displaying a custom player head texture.
 */
public final class PetEntityGroup {

    private static final String PET_BASE_TAG = "pet_base";
    private static final String PET_PART_TAG = "pet_part";

    private final List<PetPart> parts = new ArrayList<>();
    private final Map<String, ArmorStand> armorStands = new HashMap<>();
    private Entity baseEntity;
    private float currentYaw = 0f;

    /**
     * Adds a part definition to this pet.
     * Must be called before spawn().
     */
    public void addPart(PetPart part) {
        parts.add(part);
    }

    /**
     * Spawns the pet at the given location.
     */
    public void spawn(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        currentYaw = location.getYaw();

        // Spawn invisible chicken as base entity
        baseEntity = world.spawnEntity(location, EntityType.CHICKEN);
        Chicken chicken = (Chicken) baseEntity;
        chicken.setInvisible(true);
        chicken.setInvulnerable(true);
        chicken.setSilent(true);
        chicken.setAI(false);
        chicken.setCollidable(false);
        chicken.setGravity(false);
        chicken.addScoreboardTag(PET_BASE_TAG);

        // Spawn armor stands for each part
        for (PetPart part : parts) {
            ArmorStand stand = spawnArmorStandPart(location, part);
            armorStands.put(part.name(), stand);
        }
    }

    private ArmorStand spawnArmorStandPart(Location baseLoc, PetPart part) {
        Location partLoc = calculatePartLocation(baseLoc, part);

        ArmorStand stand = (ArmorStand) baseLoc.getWorld().spawnEntity(partLoc, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setMarker(true); // No hitbox
        stand.setGravity(false);
        stand.setSmall(part.small());
        stand.setBasePlate(false);
        stand.addScoreboardTag(PET_PART_TAG);

        // Set equipment
        if (part.useRightArm()) {
            stand.setArms(true);
            stand.getEquipment().setItemInMainHand(createPlayerHead(part.texture()));
        } else {
            stand.getEquipment().setHelmet(createPlayerHead(part.texture()));
        }

        // Set default pose
        stand.setHeadPose(part.defaultPose());

        return stand;
    }

    private Location calculatePartLocation(Location baseLoc, PetPart part) {
        // Calculate position with rotation applied
        double yawRad = Math.toRadians(currentYaw);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        // Rotate offset around Y axis based on pet's facing direction
        double rotatedX = part.offsetX() * cos - part.offsetZ() * sin;
        double rotatedZ = part.offsetX() * sin + part.offsetZ() * cos;

        return baseLoc.clone().add(rotatedX, part.offsetY(), rotatedZ);
    }

    private ItemStack createPlayerHead(String base64Texture) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.getProperties().add(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);

        skull.setItemMeta(meta);
        return skull;
    }

    /**
     * Moves the pet to a new location.
     */
    public void moveTo(Location location) {
        if (baseEntity == null || baseEntity.isDead()) return;

        currentYaw = location.getYaw();
        baseEntity.teleport(location);

        // Update all part positions
        for (PetPart part : parts) {
            ArmorStand stand = armorStands.get(part.name());
            if (stand != null && !stand.isDead()) {
                Location partLoc = calculatePartLocation(location, part);
                partLoc.setYaw(currentYaw);
                stand.teleport(partLoc);
            }
        }
    }

    /**
     * Sets the head pose for a specific part.
     */
    public void setPartHeadPose(String partName, EulerAngle pose) {
        ArmorStand stand = armorStands.get(partName);
        if (stand != null && !stand.isDead()) {
            stand.setHeadPose(pose);
        }
    }

    /**
     * Sets the right arm pose for a specific part (for arm-held items).
     */
    public void setPartRightArmPose(String partName, EulerAngle pose) {
        ArmorStand stand = armorStands.get(partName);
        if (stand != null && !stand.isDead()) {
            stand.setRightArmPose(pose);
        }
    }

    /**
     * Gets the current location of the pet.
     */
    public Location getLocation() {
        if (baseEntity == null || baseEntity.isDead()) return null;
        return baseEntity.getLocation();
    }

    /**
     * Gets the current yaw (facing direction) of the pet.
     */
    public float getYaw() {
        return currentYaw;
    }

    /**
     * Sets the pet's facing direction without moving it.
     */
    public void setYaw(float yaw) {
        if (baseEntity == null || baseEntity.isDead()) return;
        currentYaw = yaw;
        Location baseLoc = baseEntity.getLocation();
        baseLoc.setYaw(yaw);
        baseEntity.teleport(baseLoc);

        // Update armor stand positions for new rotation
        for (PetPart part : parts) {
            ArmorStand stand = armorStands.get(part.name());
            if (stand != null && !stand.isDead()) {
                Location partLoc = calculatePartLocation(baseLoc, part);
                partLoc.setYaw(yaw);
                stand.teleport(partLoc);
            }
        }
    }

    /**
     * Removes all entities associated with this pet.
     */
    public void remove() {
        for (ArmorStand stand : armorStands.values()) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        armorStands.clear();

        if (baseEntity != null && !baseEntity.isDead()) {
            baseEntity.remove();
            baseEntity = null;
        }
    }

    /**
     * Checks if the pet is currently spawned and alive.
     */
    public boolean isSpawned() {
        return baseEntity != null && !baseEntity.isDead();
    }

    /**
     * Gets the UUID of the base entity for tracking purposes.
     */
    public UUID getBaseEntityId() {
        return baseEntity != null ? baseEntity.getUniqueId() : null;
    }
}
