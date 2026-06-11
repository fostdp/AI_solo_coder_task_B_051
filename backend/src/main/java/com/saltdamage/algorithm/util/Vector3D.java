package com.saltdamage.algorithm.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 三维向量类
 * 用于表示盐分运移速度、梯度等物理量的三维矢量
 *
 * 提供基本的向量运算功能，包括加减、数乘、点积、叉积、归一化等
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vector3D {

    /**
     * x分量
     */
    private double x;

    /**
     * y分量
     */
    private double y;

    /**
     * z分量
     */
    private double z;

    /**
     * 构造函数（从原点创建向量）
     *
     * @param x x分量
     * @param y y分量
     * @param z z分量
     * @return 新的三维向量
     */
    public static Vector3D of(double x, double y, double z) {
        return new Vector3D(x, y, z);
    }

    /**
     * 创建零向量
     *
     * @return 零向量 (0, 0, 0)
     */
    public static Vector3D zero() {
        return new Vector3D(0, 0, 0);
    }

    /**
     * 向量加法
     * 公式: this + v = (x1+x2, y1+y2, z1+z2)
     *
     * @param v 要加的向量
     * @return 相加后的新向量
     * @throws IllegalArgumentException 如果v为null
     */
    public Vector3D add(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return new Vector3D(this.x + v.x, this.y + v.y, this.z + v.z);
    }

    /**
     * 向量减法
     * 公式: this - v = (x1-x2, y1-y2, z1-z2)
     *
     * @param v 要减的向量
     * @return 相减后的新向量
     * @throws IllegalArgumentException 如果v为null
     */
    public Vector3D subtract(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return new Vector3D(this.x - v.x, this.y - v.y, this.z - v.z);
    }

    /**
     * 数乘（标量乘法）
     * 公式: k * this = (k*x, k*y, k*z)
     *
     * @param scalar 标量值
     * @return 数乘后的新向量
     */
    public Vector3D multiply(double scalar) {
        return new Vector3D(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    /**
     * 点积（内积）
     * 公式: this · v = x1*x2 + y1*y2 + z1*z2
     * 点积为0表示两向量垂直
     *
     * @param v 另一个向量
     * @return 点积结果（标量）
     * @throws IllegalArgumentException 如果v为null
     */
    public double dot(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return this.x * v.x + this.y * v.y + this.z * v.z;
    }

    /**
     * 叉积（外积）
     * 公式: this × v = (y1*z2 - z1*y2, z1*x2 - x1*z2, x1*y2 - y1*x2)
     * 叉积结果是一个与两个向量都垂直的向量
     *
     * @param v 另一个向量
     * @return 叉积结果（新向量）
     * @throws IllegalArgumentException 如果v为null
     */
    public Vector3D cross(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        double newX = this.y * v.z - this.z * v.y;
        double newY = this.z * v.x - this.x * v.z;
        double newZ = this.x * v.y - this.y * v.x;
        return new Vector3D(newX, newY, newZ);
    }

    /**
     * 计算向量模长（长度）
     * 公式: |this| = sqrt(x² + y² + z²)
     *
     * @return 模长
     */
    public double magnitude() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    /**
     * 计算向量模长的平方（避免开方运算，提高性能）
     * 公式: |this|² = x² + y² + z²
     *
     * @return 模长的平方
     */
    public double magnitudeSquared() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    /**
     * 归一化（单位向量）
     * 将向量转换为同方向的单位向量（模长为1）
     * 公式: this / |this|
     *
     * @return 归一化后的新向量
     * @throws ArithmeticException 如果是零向量
     */
    public Vector3D normalize() {
        double mag = magnitude();
        if (mag == 0) {
            throw new ArithmeticException("不能归一化零向量");
        }
        return new Vector3D(this.x / mag, this.y / mag, this.z / mag);
    }

    /**
     * 计算两向量之间的距离
     * 公式: |this - v|
     *
     * @param v 另一个向量
     * @return 距离
     * @throws IllegalArgumentException 如果v为null
     */
    public double distanceTo(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return this.subtract(v).magnitude();
    }

    /**
     * 计算两向量之间的夹角（弧度）
     * 公式: θ = arccos((this·v) / (|this|*|v|))
     *
     * @param v 另一个向量
     * @return 夹角（弧度，范围 [0, π]）
     * @throws IllegalArgumentException 如果v为null或任一向量为零向量
     */
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
        // 防止浮点误差导致超出[-1,1]范围
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
        return Math.acos(cosAngle);
    }

    /**
     * 向量取反
     * 公式: -this = (-x, -y, -z)
     *
     * @return 取反后的新向量
     */
    public Vector3D negate() {
        return new Vector3D(-this.x, -this.y, -this.z);
    }

    /**
     * 计算与另一个向量的逐元素乘积
     * 公式: this * v = (x1*x2, y1*y2, z1*z2)
     *
     * @param v 另一个向量
     * @return 逐元素乘积的新向量
     * @throws IllegalArgumentException 如果v为null
     */
    public Vector3D elementwiseMultiply(Vector3D v) {
        if (v == null) {
            throw new IllegalArgumentException("向量不能为null");
        }
        return new Vector3D(this.x * v.x, this.y * v.y, this.z * v.z);
    }

    /**
     * 判断是否为零向量
     *
     * @return 如果是零向量返回true
     */
    public boolean isZero() {
        return this.x == 0 && this.y == 0 && this.z == 0;
    }

    /**
     * 判断与另一个向量是否近似相等（考虑浮点误差）
     *
     * @param v 另一个向量
     * @param epsilon 容许误差
     * @return 如果近似相等返回true
     */
    public boolean epsilonEquals(Vector3D v, double epsilon) {
        if (v == null) {
            return false;
        }
        return Math.abs(this.x - v.x) < epsilon
                && Math.abs(this.y - v.y) < epsilon
                && Math.abs(this.z - v.z) < epsilon;
    }

    /**
     * 转换为字符串表示
     *
     * @return 向量字符串，格式: (x, y, z)
     */
    @Override
    public String toString() {
        return String.format("(%.6f, %.6f, %.6f)", x, y, z);
    }

    /**
     * equals方法
     *
     * @param o 比较对象
     * @return 如果相等返回true
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vector3D vector3D = (Vector3D) o;
        return Double.compare(vector3D.x, x) == 0
                && Double.compare(vector3D.y, y) == 0
                && Double.compare(vector3D.z, z) == 0;
    }

    /**
     * hashCode方法
     *
     * @return 哈希码
     */
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
