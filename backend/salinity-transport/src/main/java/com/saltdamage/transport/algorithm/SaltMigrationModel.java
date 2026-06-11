package com.saltdamage.transport.algorithm;

import com.saltdamage.transport.algorithm.util.PorosityGrid;
import com.saltdamage.transport.algorithm.util.Vector3D;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SaltMigrationModel {

    @Value("${algorithm.salt-migration.porosity:0.35}")
    private double defaultPorosity;

    @Value("${algorithm.salt-migration.permeability:1.0e-12}")
    private double defaultPermeability;

    @Value("${algorithm.salt-migration.viscosity:0.001002}")
    private double defaultViscosity;

    @Value("${algorithm.salt-migration.diffusion-coeff:1.33e-9}")
    private double defaultDiffusionCoeff;

    private static final double KOZENY_CONSTANT = 5.0;

    public Vector3D calculateMigrationVelocity(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            double porosity,
            double permeability,
            double viscosity,
            double diffusionCoeff,
            int x, int y,
            double deltaX, double deltaY) {

        validateInputParameters(concentrationGrid, pressureGrid, x, y, deltaX, deltaY);

        double actualPorosity = (porosity > 0 && porosity < 1) ? porosity : defaultPorosity;
        double actualViscosity = viscosity > 0 ? viscosity : defaultViscosity;
        double actualDiffusionCoeff = diffusionCoeff > 0 ? diffusionCoeff : defaultDiffusionCoeff;

        double effectivePermeability;
        if (permeability > 0) {
            effectivePermeability = permeability;
        } else {
            effectivePermeability = calculateKozenyCarmanPermeability(actualPorosity);
            log.debug("使用Kozeny-Carman方程计算有效渗透率: {} m²", effectivePermeability);
        }

        double effectiveDiffusionCoeff = actualDiffusionCoeff * actualPorosity * actualPorosity;

        Vector3D pressureGradient = calculateGradient(pressureGrid, x, y, deltaX, deltaY);
        Vector3D concentrationGradient = calculateGradient(concentrationGrid, x, y, deltaX, deltaY);

        double convectionCoeff = -effectivePermeability / actualViscosity;
        Vector3D convectionVelocity = pressureGradient.multiply(convectionCoeff);

        Vector3D diffusionVelocity = concentrationGradient.multiply(effectiveDiffusionCoeff);

        Vector3D totalVelocity = convectionVelocity.add(diffusionVelocity);

        log.debug("位置({}, {}): 对流速度={}, 扩散速度={}, 总速度={}",
                x, y, convectionVelocity, diffusionVelocity, totalVelocity);

        return totalVelocity;
    }

    public double calculateKozenyCarmanPermeability(double porosity) {
        if (porosity <= 0 || porosity >= 1) {
            throw new IllegalArgumentException("孔隙度必须在(0, 1)范围内，当前值: " + porosity);
        }
        double d_p = 1.0e-4;
        double numerator = Math.pow(porosity, 3) * d_p * d_p;
        double denominator = KOZENY_CONSTANT * 36 * Math.pow(1 - porosity, 2);
        return numerator / denominator;
    }

    public double calculateHydraulicConductivity(double permeability, double viscosity) {
        double density = 1000.0;
        double gravity = 9.81;
        return permeability * density * gravity / viscosity;
    }

    private Vector3D calculateGradient(double[][] grid, int x, int y, double deltaX, double deltaY) {
        int rows = grid.length;
        int cols = grid[0].length;

        double dFdX;
        double dFdY;

        if (x == 0) {
            dFdX = (grid[y][x + 1] - grid[y][x]) / deltaX;
        } else if (x == cols - 1) {
            dFdX = (grid[y][x] - grid[y][x - 1]) / deltaX;
        } else {
            dFdX = (grid[y][x + 1] - grid[y][x - 1]) / (2 * deltaX);
        }

        if (y == 0) {
            dFdY = (grid[y + 1][x] - grid[y][x]) / deltaY;
        } else if (y == rows - 1) {
            dFdY = (grid[y][x] - grid[y - 1][x]) / deltaY;
        } else {
            dFdY = (grid[y + 1][x] - grid[y - 1][x]) / (2 * deltaY);
        }

        return Vector3D.of(dFdX, dFdY, 0);
    }

    private void validateInputParameters(double[][] concentrationGrid, double[][] pressureGrid,
                                         int x, int y, double deltaX, double deltaY) {
        if (concentrationGrid == null || pressureGrid == null) {
            throw new IllegalArgumentException("浓度网格和压力网格不能为null");
        }

        int rows = concentrationGrid.length;
        if (rows == 0 || concentrationGrid[0].length == 0) {
            throw new IllegalArgumentException("浓度网格不能为空");
        }

        int cols = concentrationGrid[0].length;
        if (pressureGrid.length != rows || pressureGrid[0].length != cols) {
            throw new IllegalArgumentException("浓度网格和压力网格尺寸必须相同");
        }

        if (x < 0 || x >= cols) {
            throw new IllegalArgumentException(
                    String.format("x坐标索引 %d 超出范围 [0, %d)", x, cols));
        }
        if (y < 0 || y >= rows) {
            throw new IllegalArgumentException(
                    String.format("y坐标索引 %d 超出范围 [0, %d)", y, rows));
        }

        if (deltaX <= 0 || deltaY <= 0) {
            throw new IllegalArgumentException("网格间距必须为正数");
        }
    }

    public double calculateTortuosity(double porosity) {
        if (porosity <= 0 || porosity >= 1) {
            throw new IllegalArgumentException("孔隙度必须在(0, 1)范围内");
        }
        return 1.0 / porosity;
    }

    public double calculateReynoldsNumber(double velocity, double viscosity) {
        double density = 1000.0;
        double d_p = 1.0e-4;
        return density * Math.abs(velocity) * d_p / viscosity;
    }

    public Vector3D calculateMigrationVelocityHeterogeneous(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            PorosityGrid porosityGrid,
            double viscosity,
            double diffusionCoeff,
            int x, int y) {

        validateInputParameters(concentrationGrid, pressureGrid, x, y,
                porosityGrid.getDeltaX(), porosityGrid.getDeltaY());

        double actualViscosity = viscosity > 0 ? viscosity : defaultViscosity;
        double actualDiffusionCoeff = diffusionCoeff > 0 ? diffusionCoeff : defaultDiffusionCoeff;

        double localPorosity = porosityGrid.getPorosity(x, y);
        double localPermeability = porosityGrid.getPermeability(x, y, KOZENY_CONSTANT, 1.0e-4);
        double localDiffusion = porosityGrid.getEffectiveDiffusion(x, y, actualDiffusionCoeff);

        log.debug("位置({}, {}): 孔隙度={}, 渗透率={} m², 有效扩散={} m²/s",
                x, y, localPorosity, localPermeability, localDiffusion);

        Vector3D pressureGradient = calculateGradient(pressureGrid, x, y,
                porosityGrid.getDeltaX(), porosityGrid.getDeltaY());

        Vector3D concentrationGradient = calculateGradient(concentrationGrid, x, y,
                porosityGrid.getDeltaX(), porosityGrid.getDeltaY());

        double convectionCoeff = -localPermeability / actualViscosity;
        Vector3D convectionVelocity = pressureGradient.multiply(convectionCoeff);

        Vector3D diffusionVelocity = concentrationGradient.multiply(localDiffusion);

        Vector3D totalVelocity = convectionVelocity.add(diffusionVelocity);

        log.debug("非均质模型 - 位置({}, {}): 对流速度={}, 扩散速度={}, 总速度={}",
                x, y, convectionVelocity, diffusionVelocity, totalVelocity);

        return totalVelocity;
    }

    public Vector3D[][] calculateVelocityFieldHeterogeneous(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            PorosityGrid porosityGrid,
            double viscosity,
            double diffusionCoeff) {

        int rows = porosityGrid.getRows();
        int cols = porosityGrid.getCols();
        Vector3D[][] velocityField = new Vector3D[rows][cols];

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                velocityField[y][x] = calculateMigrationVelocityHeterogeneous(
                        concentrationGrid, pressureGrid, porosityGrid,
                        viscosity, diffusionCoeff, x, y);
            }
        }

        return velocityField;
    }

    public List<double[]> traceMigrationPath(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            PorosityGrid porosityGrid,
            int startX, int startY,
            int numSteps, double timeStep,
            double viscosity, double diffusionCoeff) {

        List<double[]> path = new ArrayList<>();

        double posX = startX * porosityGrid.getDeltaX();
        double posY = startY * porosityGrid.getDeltaY();

        path.add(new double[]{posX, posY});

        double deltaX = porosityGrid.getDeltaX();
        double deltaY = porosityGrid.getDeltaY();
        int rows = porosityGrid.getRows();
        int cols = porosityGrid.getCols();

        for (int step = 0; step < numSteps; step++) {
            Vector3D velocity = interpolateVelocity(
                    concentrationGrid, pressureGrid, porosityGrid,
                    posX, posY, viscosity, diffusionCoeff);

            posX += velocity.getX() * timeStep;
            posY += velocity.getY() * timeStep;

            if (posX < 0 || posX >= (cols - 1) * deltaX ||
                    posY < 0 || posY >= (rows - 1) * deltaY) {
                break;
            }

            path.add(new double[]{posX, posY});
        }

        return path;
    }

    private Vector3D interpolateVelocity(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            PorosityGrid porosityGrid,
            double posX, double posY,
            double viscosity, double diffusionCoeff) {

        double deltaX = porosityGrid.getDeltaX();
        double deltaY = porosityGrid.getDeltaY();

        int x0 = (int) Math.floor(posX / deltaX);
        int y0 = (int) Math.floor(posY / deltaY);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        x0 = Math.max(0, Math.min(x0, porosityGrid.getCols() - 2));
        y0 = Math.max(0, Math.min(y0, porosityGrid.getRows() - 2));
        x1 = x0 + 1;
        y1 = y0 + 1;

        double wx = (posX - x0 * deltaX) / deltaX;
        double wy = (posY - y0 * deltaY) / deltaY;

        Vector3D v00 = calculateMigrationVelocityHeterogeneous(
                concentrationGrid, pressureGrid, porosityGrid,
                viscosity, diffusionCoeff, x0, y0);
        Vector3D v10 = calculateMigrationVelocityHeterogeneous(
                concentrationGrid, pressureGrid, porosityGrid,
                viscosity, diffusionCoeff, x1, y0);
        Vector3D v01 = calculateMigrationVelocityHeterogeneous(
                concentrationGrid, pressureGrid, porosityGrid,
                viscosity, diffusionCoeff, x0, y1);
        Vector3D v11 = calculateMigrationVelocityHeterogeneous(
                concentrationGrid, pressureGrid, porosityGrid,
                viscosity, diffusionCoeff, x1, y1);

        double vx = v00.getX() * (1 - wx) * (1 - wy) +
                v10.getX() * wx * (1 - wy) +
                v01.getX() * (1 - wx) * wy +
                v11.getX() * wx * wy;

        double vy = v00.getY() * (1 - wx) * (1 - wy) +
                v10.getY() * wx * (1 - wy) +
                v01.getY() * (1 - wx) * wy +
                v11.getY() * wx * wy;

        return Vector3D.of(vx, vy, 0);
    }

    public double[] calculateHeterogeneityBias(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            PorosityGrid porosityGrid,
            double viscosity,
            double diffusionCoeff) {

        int rows = porosityGrid.getRows();
        int cols = porosityGrid.getCols();

        double avgPorosity = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                avgPorosity += porosityGrid.getPorosity(x, y);
            }
        }
        avgPorosity /= (rows * cols);

        double totalBias = 0;
        double maxBias = 0;
        double minBias = Double.MAX_VALUE;
        int count = 0;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                Vector3D vHomogeneous = calculateMigrationVelocity(
                        concentrationGrid, pressureGrid,
                        avgPorosity, 0, viscosity, diffusionCoeff,
                        x, y, porosityGrid.getDeltaX(), porosityGrid.getDeltaY());

                Vector3D vHeterogeneous = calculateMigrationVelocityHeterogeneous(
                        concentrationGrid, pressureGrid, porosityGrid,
                        viscosity, diffusionCoeff, x, y);

                double speedHomo = vHomogeneous.magnitude();
                double speedHetero = vHeterogeneous.magnitude();

                if (speedHomo > 1e-15) {
                    double bias = Math.abs(speedHetero - speedHomo) / speedHomo * 100;
                    totalBias += bias;
                    maxBias = Math.max(maxBias, bias);
                    minBias = Math.min(minBias, bias);
                    count++;
                }
            }
        }

        double avgBias = count > 0 ? totalBias / count : 0;
        if (minBias == Double.MAX_VALUE) minBias = 0;

        log.info("孔隙非均质性偏差统计: 平均={:.2f}%, 最大={:.2f}%, 最小={:.2f}%",
                avgBias, maxBias, minBias);

        return new double[]{avgBias, maxBias, minBias};
    }
}
