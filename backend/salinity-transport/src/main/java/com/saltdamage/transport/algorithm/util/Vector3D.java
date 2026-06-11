package com.saltdamage.transport.algorithm.util;

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

    public static Vector3D of(double x, double y, double z) {
        return new Vector3D(x, y, z);
    }

    public static Vector3D zero() {
        return new Vector3D(0, 0, 0);
    }

    public Vector3D add(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return new Vector3D(this.x + v.x, this.y + v.y, this.z + v.z);
    }

    public Vector3D subtract(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return new Vector3D(this.x - v.x, this.y - v.y, this.z - v.z);
    }

    public Vector3D multiply(double scalar) {
        return new Vector3D(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public double dot(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return this.x * v.x + this.y * v.y + this.z * v.z;
    }

    public Vector3D cross(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        double newX = this.y * v.z - this.z * v.y;
        double newY = this.z * v.x - this.x * v.z;
        double newZ = this.x * v.y - this.y * v.x;
        return new Vector3D(newX, newY, newZ);
    }

    public double magnitude() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public double magnitudeSquared() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public Vector3D normalize() {
        double mag = magnitude();
        if (mag == 0) {
            throw new ArithmeticException("不能归一化零向量");
        }
        return new Vector3D(this.x / mag, this.y / mag, this.z / mag);
    }

    public double distanceTo(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return this.subtract(v).magnitude();
    }

    public double angleBetween(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        double mag1 = this.magnitude();
        double mag2 = v.magnitude();
        if (mag1 == 0 || mag2 == 0) {
            throw new ArithmeticException("不能计算零向量的夹角");
        }
        double dotProduct = this.dot(v);
        double cosAngle = dotProduct / (mag1 * mag2);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
        return Math.acos(cosAngle);
    }

    public Vector3D negate() {
        return new Vector3D(-this.x, -this.y, -this.z);
    }

    public Vector3D elementwiseMultiply(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return new Vector3D(this.x * v.x, this.y * v.y, this.z * v.z);
    }

    public boolean isZero() {
        return this.x == 0 && this.y == 0 && this.z == 0;
    }

    public boolean epsilonEquals(Vector3D v, double epsilon) {
        if (v == null) {
            return false;
        }
        return Math.abs(this.x - v.x) < epsilon
                && Math.abs(this.y - v.y) < epsilon
                && Math.abs(this.z - v.z) < epsilon;
    }

    @Override
    public String toString() {
        return String.format("(%.6f, %.6f, %.6f)", x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vector3D vector3D = (Vector3D) o;
        return Double.compare(vector3D.x, x) == 0
                && Double.compare(vector3D.y, y) == 0
                && Double.compare(vector3D.z, z) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(x);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(z);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
