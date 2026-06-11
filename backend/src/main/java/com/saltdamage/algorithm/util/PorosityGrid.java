package com.saltdamage.algorithm.util;

import lombok.Data;

/**
 * 孔隙度网格（CT扫描非均质分布）
 *
 * 描述多孔介质中孔隙度的空间分布，由CT扫描或岩心测试数据构建。
 * 每个网格单元存储当地孔隙度值，支持非均质介质的渗流计算。
 *
 * 典型应用场景：
 * - 墓葬壁画支撑体（砖体/石灰层/夯土）的孔隙分布不均
 * - 毛细上升区孔隙度随高度变化
 * - 盐析结壳导致的表层孔隙度降低
 */
@Data
public class PorosityGrid {

    /** 网格行数（y方向） */
    private final int rows;

    /** 网格列数（x方向） */
    private final int cols;

    /** x方向网格间距，单位: m */
    private final double deltaX;

    /** y方向网格间距，单位: m */
    private final double deltaY;

    /** 孔隙度二维数组，porosity[y][x] 表示 (x,y) 单元的孔隙度 */
    private final double[][] porosity;

    /**
     * 构造均匀孔隙度网格（均质模型，用于对比验证）
     *
     * @param rows 行数
     * @param cols 列数
     * @param deltaX x方向网格间距
     * @param deltaY y方向网格间距
     * @param uniformPorosity 均匀孔隙度值
     */
    public PorosityGrid(int rows, int cols, double deltaX, double deltaY, double uniformPorosity) {
        this.rows = rows;
        this.cols = cols;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.porosity = new double[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                this.porosity[y][x] = uniformPorosity;
            }
        }
    }

    /**
     * 构造非均质孔隙度网格
     *
     * @param rows 行数
     * @param cols 列数
     * @param deltaX x方向网格间距
     * @param deltaY y方向网格间距
     * @param porosityData 孔隙度数据数组（必须为 rows x cols）
     */
    public PorosityGrid(int rows, int cols, double deltaX, double deltaY, double[][] porosityData) {
        if (porosityData == null || porosityData.length != rows || porosityData[0].length != cols) {
            throw new IllegalArgumentException(
                    String.format("孔隙度数据尺寸不匹配: 期望 %d x %d，实际 %d x %d",
                            rows, cols,
                            porosityData == null ? 0 : porosityData.length,
                            porosityData == null || porosityData.length == 0 ? 0 : porosityData[0].length));
        }
        this.rows = rows;
        this.cols = cols;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.porosity = porosityData;
    }

    /**
     * 获取指定位置的孔隙度
     *
     * @param x x坐标索引
     * @param y y坐标索引
     * @return 孔隙度值
     */
    public double getPorosity(int x, int y) {
        validateIndex(x, y);
        return porosity[y][x];
    }

    /**
     * 设置指定位置的孔隙度
     *
     * @param x x坐标索引
     * @param y y坐标索引
     * @param value 孔隙度值
     */
    public void setPorosity(int x, int y, double value) {
        validateIndex(x, y);
        if (value <= 0 || value >= 1) {
            throw new IllegalArgumentException("孔隙度必须在(0, 1)范围内: " + value);
        }
        porosity[y][x] = value;
    }

    /**
     * 计算指定位置的有效渗透率（使用 Kozeny-Carman 方程，逐点计算）
     *
     * @param x x坐标索引
     * @param y y坐标索引
     * @param kozenyConstant Kozeny常数
     * @param particleDiameter 平均粒径，单位: m
     * @return 有效渗透率，单位: m²
     */
    public double getPermeability(int x, int y, double kozenyConstant, double particleDiameter) {
        double n = getPorosity(x, y);
        double numerator = Math.pow(n, 3) * particleDiameter * particleDiameter;
        double denominator = kozenyConstant * 36.0 * Math.pow(1 - n, 2);
        return numerator / denominator;
    }

    /**
     * 计算指定位置的迂曲度
     * 经验公式: τ = 1 / n（Bear, 1972）
     *
     * @param x x坐标索引
     * @param y y坐标索引
     * @return 迂曲度（≥1）
     */
    public double getTortuosity(int x, int y) {
        return 1.0 / getPorosity(x, y);
    }

    /**
     * 计算指定位置的有效扩散系数
     * D_eff = D₀ * n / τ ≈ D₀ * n²
     *
     * @param x x坐标索引
     * @param y y坐标索引
     * @param freeDiffusionCoeff 自由溶液扩散系数，单位: m²/s
     * @return 有效扩散系数，单位: m²/s
     */
    public double getEffectiveDiffusion(int x, int y, double freeDiffusionCoeff) {
        double n = getPorosity(x, y);
        double tau = getTortuosity(x, y);
        return freeDiffusionCoeff * n / tau;
    }

    /**
     * 生成毛细上升带孔隙度分布（随高度递减，模拟实际墓葬墙体）
     *
     * 模型假设：
     * - 底部（y=0）孔隙度最大（毛细上升水源区）
     * - 顶部孔隙度最小（盐分富集结壳区）
     * - 加入横向随机扰动模拟非均质性
     *
     * @param rows 行数
     * @param cols 列数
     * @param deltaX x方向网格间距
     * @param deltaY y方向网格间距
     * @param bottomPorosity 底部孔隙度
     * @param topPorosity 顶部孔隙度
     * @param randomFactor 随机扰动因子（0~0.3）
     * @return 孔隙度网格
     */
    public static PorosityGrid generateCapillaryZone(
            int rows, int cols,
            double deltaX, double deltaY,
            double bottomPorosity, double topPorosity,
            double randomFactor) {
        double[][] data = new double[rows][cols];
        for (int y = 0; y < rows; y++) {
            double heightRatio = (double) y / (rows - 1);
            double basePorosity = bottomPorosity - (bottomPorosity - topPorosity) * heightRatio;
            for (int x = 0; x < cols; x++) {
                double randomPerturbation = (Math.random() - 0.5) * 2 * randomFactor * basePorosity;
                double value = basePorosity + randomPerturbation;
                data[y][x] = Math.max(0.05, Math.min(0.6, value));
            }
        }
        return new PorosityGrid(rows, cols, deltaX, deltaY, data);
    }

    /**
     * 生成层状孔隙度分布（模拟壁画多层结构：地仗层+石灰层+颜料层）
     *
     * @param rows 行数
     * @param cols 列数
     * @param deltaX x方向网格间距
     * @param deltaY y方向网格间距
     * @param layers 层配置数组，每层 (起始y比例, 结束y比例, 孔隙度)
     * @return 孔隙度网格
     */
    public static PorosityGrid generateLayered(
            int rows, int cols,
            double deltaX, double deltaY,
            LayerConfig[] layers) {
        double[][] data = new double[rows][cols];
        for (int y = 0; y < rows; y++) {
            double yRatio = (double) y / rows;
            double layerPorosity = 0.35;
            for (LayerConfig layer : layers) {
                if (yRatio >= layer.startRatio && yRatio < layer.endRatio) {
                    layerPorosity = layer.porosity;
                    break;
                }
            }
            for (int x = 0; x < cols; x++) {
                data[y][x] = layerPorosity;
            }
        }
        return new PorosityGrid(rows, cols, deltaX, deltaY, data);
    }

    /**
     * 验证索引是否合法
     */
    private void validateIndex(int x, int y) {
        if (x < 0 || x >= cols) {
            throw new IndexOutOfBoundsException(
                    String.format("x索引 %d 超出范围 [0, %d)", x, cols));
        }
        if (y < 0 || y >= rows) {
            throw new IndexOutOfBoundsException(
                    String.format("y索引 %d 超出范围 [0, %d)", y, rows));
        }
    }

    /**
     * 层配置（用于生成层状孔隙度分布）
     */
    @Data
    public static class LayerConfig {
        /** 层起始位置（y方向比例，0~1） */
        private final double startRatio;
        /** 层结束位置（y方向比例，0~1） */
        private final double endRatio;
        /** 该层的孔隙度 */
        private final double porosity;

        public LayerConfig(double startRatio, double endRatio, double porosity) {
            this.startRatio = startRatio;
            this.endRatio = endRatio;
            this.porosity = porosity;
        }
    }
}
