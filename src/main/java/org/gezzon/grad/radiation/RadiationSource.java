package org.gezzon.grad.radiation;

import org.bukkit.Location;

/**
 * Модель (POJO) одного сферического источника радиации.
 * Хранит ID, интенсивность (уровень), радиус, множитель (power) и центр (Location).
 */
public class RadiationSource {

    private final int id;           // Уникальный идентификатор источника
    private int intensity;    // Уровень радиации (1..5)
    private double radius;    // Радиус сферы
    private double power;     // Множитель радиации
    private Location center;  // Центр зоны (x,y,z + мир)

    public RadiationSource(int id, int intensity, double radius, double power, Location center) {
        this.id = id;
        this.intensity = intensity;
        this.radius = radius;
        this.power = power;
        this.center = center;
    }

    public int getId() {
        return id;
    }

    public int getIntensity() {
        return intensity;
    }

    public void setIntensity(int intensity) {
        this.intensity = intensity;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
        this.power = power;
    }

    public Location getCenter() {
        return center;
    }

    public void setCenter(Location center) {
        this.center = center;
    }
}
