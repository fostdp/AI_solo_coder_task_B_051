package com.saltdamage.transport.algorithm.util;

import lombok.Data;

@Data
public class PorosityGrid {

    private final int rows;
    private final int cols;
    private final double deltaX;
    private final double deltaY;
    private final double[][] porosity;

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

    public double getPorosity(int x, int y) {
        validateIndex(x, y);
        return porosity[y][x];
    }

    public void setPorosity(int x, int y, double value) {
        validateIndex(x, y);
        if (value <= 0 || value >= 1) {
            throw new IllegalArgumentException("孔隙度必须在(0, 1)范围内: " + value);
        }
        porosity[y][x] = value;
    }

    public double getPermeability(int x, int y, double kozenyConstant, double particleDiameter) {
        double n = getPorosity(x, y);
        double numerator = Math.pow(n, 3) * particleDiameter * particleDiameter;
        double denominator = kozenyConstant * 36.0 * Math.pow(1 - n, 2);
        return numerator / denominator;
    }

    public double getTortuosity(int x, int y) {
        return 1.0 / getPorosity(x, y);
    }

    public double getEffectiveDiffusion(int x, int y, double freeDiffusionCoeff) {
        double n = getPorosity(x, y);
        double tau = getTortuosity(x, y);
        return freeDiffusionCoeff * n / tau;
    }

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

    @Data
    public static class LayerConfig {
        private final double startRatio;
        private final double endRatio;
        private final double porosity;

        public LayerConfig(double startRatio, double endRatio, double porosity) {
            this.startRatio = startRatio;
            this.endRatio = endRatio;
            this.porosity = porosity;
        }
    }
}
