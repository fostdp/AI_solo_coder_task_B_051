package com.saltdamage.algorithm;

import com.saltdamage.algorithm.util.PorosityGrid;
import com.saltdamage.algorithm.util.Vector3D;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 盐分运移模型
 *
 * 本模型基于Darcy定律和离子扩散方程，模拟多孔介质中盐分的运移过程。
 * 盐分运移由两部分组成：
 * 1. 对流运移：由压力梯度驱动，遵循Darcy定律
 * 2. 扩散运移：由浓度梯度驱动，遵循Fick扩散定律
 *
 * 总速度公式: v = -k/μ * ∇P + D * ∇C
 *
 * 其中：
 * - v: 运移速度向量 (m/s)
 * - k: 渗透率 (m²)
 * - μ: 流体粘度 (Pa·s)
 * - ∇P: 压力梯度 (Pa/m)
 * - D: 扩散系数 (m²/s)
 * - ∇C: 浓度梯度 (mol/m⁴)
 *
 * 模型特点：
 * - 采用Kozeny-Carman方程进行孔隙度修正，考虑孔隙结构对渗透率的影响
 * - 使用中心差分法计算梯度，提高计算精度
 * - 支持二维网格的盐分运移速度计算
 */
@Slf4j
@Component
public class SaltMigrationModel {

    /**
     * 默认孔隙度（从配置文件读取）
     * 孔隙度是多孔介质中孔隙体积与总体积之比，无量纲
     * 典型岩石孔隙度范围: 0.05~0.40
     */
    @Value("${algorithm.salt-migration.porosity:0.35}")
    private double defaultPorosity;

    /**
     * 默认渗透率（从配置文件读取）
     * 渗透率表示多孔介质允许流体通过的能力，单位: m²
     * 典型岩石渗透率范围: 1e-15 ~ 1e-10 m²
     */
    @Value("${algorithm.salt-migration.permeability:1.0e-12}")
    private double defaultPermeability;

    /**
     * 默认流体粘度（从配置文件读取）
     * 水在20℃时的粘度约为 0.001002 Pa·s
     */
    @Value("${algorithm.salt-migration.viscosity:0.001002}")
    private double defaultViscosity;

    /**
     * 默认扩散系数（从配置文件读取）
     * Na+和SO4^2-在水中的典型扩散系数约为 1e-9 ~ 2e-9 m²/s
     */
    @Value("${algorithm.salt-migration.diffusion-coeff:1.33e-9}")
    private double defaultDiffusionCoeff;

    /**
     * Kozeny-Carman方程的形状因子
     * 对于球形颗粒，典型值为5.0
     */
    private static final double KOZENY_CONSTANT = 5.0;

    /**
     * 计算盐分运移速度
     *
     * 本方法结合Darcy定律和Fick扩散定律，计算二维网格中指定点的盐分运移速度。
     * 运移速度由对流项和扩散项两部分组成：
     *
     * 对流项（Darcy定律）: v_convection = -k_eff/μ * ∇P
     * 扩散项（Fick定律）: v_diffusion = D_eff * ∇C
     *
     * 其中k_eff是经过Kozeny-Carman方程修正后的有效渗透率，
     * D_eff是考虑孔隙度影响的有效扩散系数。
     *
     * @param concentrationGrid 浓度网格，单位: mol/m³
     * @param pressureGrid 压力网格，单位: Pa
     * @param porosity 孔隙度（0~1），为null时使用默认值
     * @param permeability 固有渗透率，单位: m²，为0时使用Kozeny-Carman公式计算
     * @param viscosity 流体粘度，单位: Pa·s，为0时使用默认值
     * @param diffusionCoeff 自由溶液扩散系数，单位: m²/s，为0时使用默认值
     * @param x 计算点的x坐标索引
     * @param y 计算点的y坐标索引
     * @param deltaX x方向网格间距，单位: m
     * @param deltaY y方向网格间距，单位: m
     * @return 盐分运移速度向量，单位: m/s，x分量为水平速度，y分量为垂直速度，z分量为0
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public Vector3D calculateMigrationVelocity(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            double porosity,
            double permeability,
            double viscosity,
            double diffusionCoeff,
            int x, int y,
            double deltaX, double deltaY) {

        // ==================== 参数校验 ====================
        validateInputParameters(concentrationGrid, pressureGrid, x, y, deltaX, deltaY);

        // 使用默认值填充0或负数参数
        double actualPorosity = (porosity > 0 && porosity < 1) ? porosity : defaultPorosity;
        double actualViscosity = viscosity > 0 ? viscosity : defaultViscosity;
        double actualDiffusionCoeff = diffusionCoeff > 0 ? diffusionCoeff : defaultDiffusionCoeff;

        // ==================== Kozeny-Carman孔隙度修正 ====================
        // 计算有效渗透率
        // 如果用户未提供渗透率（permeability <= 0），则使用Kozeny-Carman方程计算
        //
        // Kozeny-Carman方程: k = (n³ * d_p²) / (180 * (1-n)²)
        // 其中:
        //   n  - 孔隙度
        //   d_p - 平均粒径（此处使用经验值1e-4 m，即细砂级）
        //
        // 该方程基于以下假设：
        // 1. 多孔介质由均匀球形颗粒组成
        // 2. 孔隙是连通的、迂曲的毛细管
        // 3. 流动为层流状态
        double effectivePermeability;
        if (permeability > 0) {
            effectivePermeability = permeability;
        } else {
            effectivePermeability = calculateKozenyCarmanPermeability(actualPorosity);
            log.debug("使用Kozeny-Carman方程计算有效渗透率: {} m²", effectivePermeability);
        }

        // 有效扩散系数修正（考虑孔隙度和迂曲度）
        // D_eff = D * n / τ
        // 其中τ为迂曲度，通常取 τ ≈ 1/n（经验关系）
        // 因此 D_eff ≈ D * n²
        double effectiveDiffusionCoeff = actualDiffusionCoeff * actualPorosity * actualPorosity;

        // ==================== 梯度计算（中心差分法） ====================
        // 计算压力梯度 ∇P = (∂P/∂x, ∂P/∂y)
        Vector3D pressureGradient = calculateGradient(pressureGrid, x, y, deltaX, deltaY);

        // 计算浓度梯度 ∇C = (∂C/∂x, ∂C/∂y)
        Vector3D concentrationGradient = calculateGradient(concentrationGrid, x, y, deltaX, deltaY);

        // ==================== 运移速度计算 ====================
        // 对流速度（Darcy定律）: v_convection = -k/μ * ∇P
        // 负号表示流体从高压区流向低压区
        double convectionCoeff = -effectivePermeability / actualViscosity;
        Vector3D convectionVelocity = pressureGradient.multiply(convectionCoeff);

        // 扩散速度（Fick定律）: v_diffusion = D_eff * ∇C
        // 盐分从高浓度区向低浓度区扩散，这里直接使用浓度梯度
        Vector3D diffusionVelocity = concentrationGradient.multiply(effectiveDiffusionCoeff);

        // 总速度 = 对流速度 + 扩散速度
        Vector3D totalVelocity = convectionVelocity.add(diffusionVelocity);

        log.debug("位置({}, {}): 对流速度={}, 扩散速度={}, 总速度={}",
                x, y, convectionVelocity, diffusionVelocity, totalVelocity);

        return totalVelocity;
    }

    /**
     * 使用Kozeny-Carman方程计算渗透率
     *
     * 公式: k = (n³ * d_p²) / (180 * (1 - n)²)
     *
     * 其中:
     *   k  - 渗透率 (m²)
     *   n  - 孔隙度
     *   d_p - 平均粒径 (m)，此处取经验值1e-4 m（细砂）
     *
     * @param porosity 孔隙度
     * @return 渗透率，单位: m²
     */
    public double calculateKozenyCarmanPermeability(double porosity) {
        // 边界检查
        if (porosity <= 0 || porosity >= 1) {
            throw new IllegalArgumentException("孔隙度必须在(0, 1)范围内，当前值: " + porosity);
        }

        // 平均粒径（细砂，经验值），单位: m
        double d_p = 1.0e-4;

        // Kozeny-Carman方程
        double numerator = Math.pow(porosity, 3) * d_p * d_p;
        double denominator = KOZENY_CONSTANT * 36 * Math.pow(1 - porosity, 2);
        // 注: 180 = 5 * 36，其中36是球形颗粒的表面积系数

        return numerator / denominator;
    }

    /**
     * 计算有效渗透系数（水力传导系数）
     * K = k * ρ * g / μ
     *
     * @param permeability 渗透率 (m²)
     * @param viscosity 粘度 (Pa·s)
     * @return 渗透系数 (m/s)
     */
    public double calculateHydraulicConductivity(double permeability, double viscosity) {
        // 水的密度，单位: kg/m³
        double density = 1000.0;
        // 重力加速度，单位: m/s²
        double gravity = 9.81;
        return permeability * density * gravity / viscosity;
    }

    /**
     * 计算二维网格的梯度（使用中心差分法）
     *
     * 对于内部点，使用二阶精度的中心差分：
     * ∂f/∂x ≈ (f(x+1,y) - f(x-1,y)) / (2*Δx)
     * ∂f/∂y ≈ (f(x,y+1) - f(x,y-1)) / (2*Δy)
     *
     * 对于边界点，使用一阶精度的单边差分：
     * ∂f/∂x ≈ (f(x+1,y) - f(x,y)) / Δx (左边界)
     * ∂f/∂x ≈ (f(x,y) - f(x-1,y)) / Δx (右边界)
     *
     * @param grid 二维网格数据
     * @param x x方向索引
     * @param y y方向索引
     * @param deltaX x方向网格间距
     * @param deltaY y方向网格间距
     * @return 梯度向量，x分量为∂f/∂x，y分量为∂f/∂y，z分量为0
     */
    private Vector3D calculateGradient(double[][] grid, int x, int y, double deltaX, double deltaY) {
        int rows = grid.length;
        int cols = grid[0].length;

        double dFdX;
        double dFdY;

        // x方向偏导数计算
        if (x == 0) {
            // 左边界，使用前向差分
            dFdX = (grid[y][x + 1] - grid[y][x]) / deltaX;
        } else if (x == cols - 1) {
            // 右边界，使用后向差分
            dFdX = (grid[y][x] - grid[y][x - 1]) / deltaX;
        } else {
            // 内部点，使用中心差分
            dFdX = (grid[y][x + 1] - grid[y][x - 1]) / (2 * deltaX);
        }

        // y方向偏导数计算
        if (y == 0) {
            // 下边界，使用前向差分
            dFdY = (grid[y + 1][x] - grid[y][x]) / deltaY;
        } else if (y == rows - 1) {
            // 上边界，使用后向差分
            dFdY = (grid[y][x] - grid[y - 1][x]) / deltaY;
        } else {
            // 内部点，使用中心差分
            dFdY = (grid[y + 1][x] - grid[y - 1][x]) / (2 * deltaY);
        }

        return Vector3D.of(dFdX, dFdY, 0);
    }

    /**
     * 验证输入参数有效性
     *
     * @param concentrationGrid 浓度网格
     * @param pressureGrid 压力网格
     * @param x x坐标索引
     * @param y y坐标索引
     * @param deltaX x方向网格间距
     * @param deltaY y方向网格间距
     * @throws IllegalArgumentException 参数无效时抛出
     */
    private void validateInputParameters(double[][] concentrationGrid, double[][] pressureGrid,
                                         int x, int y, double deltaX, double deltaY) {
        // 检查网格是否为null
        if (concentrationGrid == null || pressureGrid == null) {
            throw new IllegalArgumentException("浓度网格和压力网格不能为null");
        }

        // 检查网格是否为空
        int rows = concentrationGrid.length;
        if (rows == 0 || concentrationGrid[0].length == 0) {
            throw new IllegalArgumentException("浓度网格不能为空");
        }

        int cols = concentrationGrid[0].length;
        if (pressureGrid.length != rows || pressureGrid[0].length != cols) {
            throw new IllegalArgumentException("浓度网格和压力网格尺寸必须相同");
        }

        // 检查坐标索引范围
        if (x < 0 || x >= cols) {
            throw new IllegalArgumentException(
                    String.format("x坐标索引 %d 超出范围 [0, %d)", x, cols));
        }
        if (y < 0 || y >= rows) {
            throw new IllegalArgumentException(
                    String.format("y坐标索引 %d 超出范围 [0, %d)", y, rows));
        }

        // 检查网格间距
        if (deltaX <= 0 || deltaY <= 0) {
            throw new IllegalArgumentException("网格间距必须为正数");
        }
    }

    /**
     * 计算孔隙迂曲度
     * 迂曲度描述孔隙通道的弯曲程度，τ ≥ 1
     * 经验公式: τ = 1 / n
     *
     * @param porosity 孔隙度
     * @return 迂曲度
     */
    public double calculateTortuosity(double porosity) {
        if (porosity <= 0 || porosity >= 1) {
            throw new IllegalArgumentException("孔隙度必须在(0, 1)范围内");
        }
        return 1.0 / porosity;
    }

    /**
     * 计算雷诺数，判断流动状态
     * Re = ρ * v * d_p / μ
     *
     * @param velocity 流速 (m/s)
     * @param viscosity 粘度 (Pa·s)
     * @return 雷诺数
     */
    public double calculateReynoldsNumber(double velocity, double viscosity) {
        // 水的密度
        double density = 1000.0;
        // 特征粒径（细砂）
        double d_p = 1.0e-4;
        return density * Math.abs(velocity) * d_p / viscosity;
    }

    // =========================================================================
    // 非均质孔隙度模型（基于CT扫描孔隙度分布）
    // =========================================================================

    /**
     * 基于非均质孔隙度网格计算单点盐分运移速度
     *
     * 与均质版本的核心区别：
     * 1. 渗透率 k 不是常数，而是由当地孔隙度通过 Kozeny-Carman 方程逐点计算
     * 2. 扩散系数 D 同样随当地孔隙度变化
     * 3. 考虑孔隙迂曲度的空间分布对运移路径的影响
     *
     * 适用场景：
     * - 墓葬壁画多层结构（地仗层/石灰层/颜料层）
     * - 毛细上升带孔隙度随高度递减
     * - CT扫描揭示的非均质孔隙分布
     *
     * @param concentrationGrid 浓度网格，单位: mol/m³
     * @param pressureGrid 压力网格，单位: Pa
     * @param porosityGrid 孔隙度网格（CT扫描分布）
     * @param viscosity 流体粘度，单位: Pa·s，为0时使用默认值
     * @param diffusionCoeff 自由溶液扩散系数，单位: m²/s，为0时使用默认值
     * @param x 计算点的x坐标索引
     * @param y 计算点的y坐标索引
     * @return 盐分运移速度向量，单位: m/s
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public Vector3D calculateMigrationVelocityHeterogeneous(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            PorosityGrid porosityGrid,
            double viscosity,
            double diffusionCoeff,
            int x, int y) {

        // ==================== 参数校验 ====================
        validateInputParameters(concentrationGrid, pressureGrid, x, y,
                porosityGrid.getDeltaX(), porosityGrid.getDeltaY());

        double actualViscosity = viscosity > 0 ? viscosity : defaultViscosity;
        double actualDiffusionCoeff = diffusionCoeff > 0 ? diffusionCoeff : defaultDiffusionCoeff;

        // ==================== 非均质参数计算（逐点） ====================

        // 当地孔隙度
        double localPorosity = porosityGrid.getPorosity(x, y);

        // 当地有效渗透率（Kozeny-Carman方程，使用当地孔隙度）
        double localPermeability = porosityGrid.getPermeability(x, y, KOZENY_CONSTANT, 1.0e-4);

        // 当地有效扩散系数（考虑当地孔隙度和迂曲度）
        double localDiffusion = porosityGrid.getEffectiveDiffusion(x, y, actualDiffusionCoeff);

        log.debug("位置({}, {}): 孔隙度={}, 渗透率={} m², 有效扩散={} m²/s",
                x, y, localPorosity, localPermeability, localDiffusion);

        // ==================== 梯度计算 ====================
        Vector3D pressureGradient = calculateGradient(pressureGrid, x, y,
                porosityGrid.getDeltaX(), porosityGrid.getDeltaY());

        Vector3D concentrationGradient = calculateGradient(concentrationGrid, x, y,
                porosityGrid.getDeltaX(), porosityGrid.getDeltaY());

        // ==================== 运移速度计算（非均质版本） ====================

        // 对流速度：使用当地渗透率
        double convectionCoeff = -localPermeability / actualViscosity;
        Vector3D convectionVelocity = pressureGradient.multiply(convectionCoeff);

        // 扩散速度：使用当地有效扩散系数
        Vector3D diffusionVelocity = concentrationGradient.multiply(localDiffusion);

        // 总速度
        Vector3D totalVelocity = convectionVelocity.add(diffusionVelocity);

        log.debug("非均质模型 - 位置({}, {}): 对流速度={}, 扩散速度={}, 总速度={}",
                x, y, convectionVelocity, diffusionVelocity, totalVelocity);

        return totalVelocity;
    }

    /**
     * 计算整个网格的盐分运移速度场（非均质模型）
     *
     * @param concentrationGrid 浓度网格
     * @param pressureGrid 压力网格
     * @param porosityGrid 孔隙度网格
     * @param viscosity 流体粘度
     * @param diffusionCoeff 扩散系数
     * @return 速度场二维数组，每个元素为速度向量
     */
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

    /**
     * 计算盐分运移路径追踪（流线追踪，非均质模型）
     *
     * 使用四阶 Runge-Kutta 方法追踪盐粒从起点的运移路径。
     * 适用于可视化盐分从污染源的扩散路径。
     *
     * @param concentrationGrid 浓度网格
     * @param pressureGrid 压力网格
     * @param porosityGrid 孔隙度网格
     * @param startX 起点x索引
     * @param startY 起点y索引
     * @param numSteps 追踪步数
     * @param timeStep 时间步长，单位: s
     * @param viscosity 流体粘度
     * @param diffusionCoeff 扩散系数
     * @return 路径点列表（坐标索引）
     */
    public List<double[]> traceMigrationPath(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            PorosityGrid porosityGrid,
            int startX, int startY,
            int numSteps, double timeStep,
            double viscosity, double diffusionCoeff) {

        List<double[]> path = new ArrayList<>();

        // 初始位置（连续坐标，非网格索引）
        double posX = startX * porosityGrid.getDeltaX();
        double posY = startY * porosityGrid.getDeltaY();

        path.add(new double[]{posX, posY});

        double deltaX = porosityGrid.getDeltaX();
        double deltaY = porosityGrid.getDeltaY();
        int rows = porosityGrid.getRows();
        int cols = porosityGrid.getCols();

        for (int step = 0; step < numSteps; step++) {
            // 双线性插值获取当前位置的速度
            Vector3D velocity = interpolateVelocity(
                    concentrationGrid, pressureGrid, porosityGrid,
                    posX, posY, viscosity, diffusionCoeff);

            // 欧拉前进（简化版本，可扩展为 RK4）
            posX += velocity.getX() * timeStep;
            posY += velocity.getY() * timeStep;

            // 边界检查
            if (posX < 0 || posX >= (cols - 1) * deltaX ||
                    posY < 0 || posY >= (rows - 1) * deltaY) {
                break;
            }

            path.add(new double[]{posX, posY});
        }

        return path;
    }

    /**
     * 双线性插值获取任意位置的运移速度（非均质模型）
     */
    private Vector3D interpolateVelocity(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            PorosityGrid porosityGrid,
            double posX, double posY,
            double viscosity, double diffusionCoeff) {

        double deltaX = porosityGrid.getDeltaX();
        double deltaY = porosityGrid.getDeltaY();

        // 计算网格索引
        int x0 = (int) Math.floor(posX / deltaX);
        int y0 = (int) Math.floor(posY / deltaY);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        x0 = Math.max(0, Math.min(x0, porosityGrid.getCols() - 2));
        y0 = Math.max(0, Math.min(y0, porosityGrid.getRows() - 2));
        x1 = x0 + 1;
        y1 = y0 + 1;

        // 计算插值权重
        double wx = (posX - x0 * deltaX) / deltaX;
        double wy = (posY - y0 * deltaY) / deltaY;

        // 获取四个角点的速度
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

        // 双线性插值
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

    /**
     * 计算非均质模型与均质模型的速度偏差
     *
     * 用于量化孔隙非均质性对盐分运移预测的影响程度。
     * 偏差越大，说明均质模型的预测误差越大。
     *
     * @param concentrationGrid 浓度网格
     * @param pressureGrid 压力网格
     * @param porosityGrid 孔隙度网格
     * @param viscosity 流体粘度
     * @param diffusionCoeff 扩散系数
     * @return 偏差统计 [平均偏差%, 最大偏差%, 最小偏差%]
     */
    public double[] calculateHeterogeneityBias(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            PorosityGrid porosityGrid,
            double viscosity,
            double diffusionCoeff) {

        int rows = porosityGrid.getRows();
        int cols = porosityGrid.getCols();

        // 计算平均孔隙度（作为均质模型的等效孔隙度）
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
                // 均质模型速度
                Vector3D vHomogeneous = calculateMigrationVelocity(
                        concentrationGrid, pressureGrid,
                        avgPorosity, 0, viscosity, diffusionCoeff,
                        x, y, porosityGrid.getDeltaX(), porosityGrid.getDeltaY());

                // 非均质模型速度
                Vector3D vHeterogeneous = calculateMigrationVelocityHeterogeneous(
                        concentrationGrid, pressureGrid, porosityGrid,
                        viscosity, diffusionCoeff, x, y);

                double speedHomo = vHomogeneous.length();
                double speedHetero = vHeterogeneous.length();

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
