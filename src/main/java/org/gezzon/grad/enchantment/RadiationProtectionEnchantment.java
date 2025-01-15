//package org.gezzon.grad.enchantment;
//
////import io.papermc.paper.enchantments.EnchantmentRarity;
//import org.bukkit.enchantments.Enchantment;
//import org.bukkit.enchantments.EnchantmentTarget;
//import org.bukkit.entity.EntityCategory;
//import org.bukkit.inventory.EquipmentSlot;
//import org.bukkit.inventory.ItemStack;
//import org.bukkit.NamespacedKey;
//import net.kyori.adventure.text.Component;
//import org.jetbrains.annotations.NotNull;
//
//import java.util.Set;
//import java.util.EnumSet;
//
//public class RadiationProtectionEnchantment extends Enchantment {
//
//    public RadiationProtectionEnchantment(String key) {
//        super(new NamespacedKey("grad", key)); // Укажите ключ зачарования
//    }
//
//    @Override
//    public String getName() {
//        return "Radiation Protection"; // Название зачарования
//    }
//
//    @Override
//    public Component displayName(int level) {
//        return Component.text(getName() + " " + level); // Отображаемое название зачарования с уровнем
//    }
//
//    @Override
//    public @NotNull String translationKey() {
//        return "enchantment.grad.radiation_protection"; // Уникальный переводческий ключ
//    }
//
//    @Override
//    public boolean isTradeable() {
//        return false; // Зачарование можно обменивать через торговлю
//    }
//
//    @Override
//    public @NotNull Set<EquipmentSlot> getActiveSlots() {
//        return EnumSet.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET); // Работает на всех слотах брони
//    }
//
//    @Override
//    public float getDamageIncrease(int level, @NotNull EntityCategory entityCategory) {
//        return 0; // Это зачарование не увеличивает урон
//    }
//
//    @Override
//    public boolean isDiscoverable() {
//        return false; // Зачарование можно найти естественным образом
//    }
//
//    @Override
//    public @NotNull EnchantmentRarity getRarity() {
//        return EnchantmentRarity.VERY_RARE; // Редкость зачарования
//    }
//
//    @Override
//    public int getMaxLevel() {
//        return 5; // Максимальный уровень зачарования
//    }
//
//    @Override
//    public int getStartLevel() {
//        return 1; // Минимальный уровень зачарования
//    }
//
//    @Override
//    public EnchantmentTarget getItemTarget() {
//        return EnchantmentTarget.ARMOR; // Зачарование доступно только для брони
//    }
//
//    @Override
//    public boolean isTreasure() {
//        return false; // Зачарование можно получить обычным способом
//    }
//
//    @Override
//    public boolean isCursed() {
//        return false; // Зачарование не является проклятием
//    }
//
//    @Override
//    public boolean conflictsWith(@NotNull Enchantment other) {
//        return false;
//    }
//
//    @Override
//    public boolean canEnchantItem(ItemStack item) {
//        // Можно ли зачаровать этот предмет
//        return item.getType().toString().endsWith("_HELMET") ||
//                item.getType().toString().endsWith("_CHESTPLATE") ||
//                item.getType().toString().endsWith("_LEGGINGS") ||
//                item.getType().toString().endsWith("_BOOTS");
//    }
//}