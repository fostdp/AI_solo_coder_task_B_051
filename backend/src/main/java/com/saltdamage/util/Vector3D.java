package com.saltdamage.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vector3D {

    private double x;
    private double y;
    private double z;

    public Vector3D add(Vector3D other) {
        return new Vector3D(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vector3D subtract(Vector3D other) {
        return new Vector3D(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vector3D multiply(double scalar) {
        return new Vector3D(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public Vector3D divide(double scalar) {
        if (scalar == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return new Vector3D(this.x / scalar, this.y / scalar, this.z / scalar);
    }

    public double dot(Vector3D other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public Vector3D cross(Vector3D other) {
        return new Vector3D(
                this.y * other.z - this.z * other.y,
                this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x
        );
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3D normalize() {
        double mag = magnitude();
        if (mag == 0) {
            return new Vector3D(0, 0, 0);
        }
        return divide(mag);
    }

    public double distanceTo(Vector3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceSquaredTo(Vector3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Vector3D negate() {
        return new Vector3D(-x, -y, -z);
    }

    public static Vector3D zero() {
        return new Vector3D(0, 0, 0);
    }

    public static Vector3D unitX() {
        return new Vector3D(1, 0, 0);
    }

    public static Vector3D unitY() {
        return new Vector3D(0, 1, 0);
    }

    public static Vector3D unitZ() {
        return new Vector3D(0, 0, 1);
    }

    public double[] toArray() {
        return new double[]{x, y, z};
    }

    public static Vector3D fromArray(double[] array) {
        if (array == null || array.length != 3) {
            throw new IllegalArgumentException("Array must have exactly 3 elements");
        }
        return new Vector3D(array[0], array[1], array[2]);
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f, %.2f)", x, y, z);
    }
}
