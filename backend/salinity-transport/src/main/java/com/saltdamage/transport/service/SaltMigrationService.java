package com.saltdamage.transport.service;

import com.saltdamage.common.message.AnalysisMessage;
import com.saltdamage.common.message.SensorDataMessage;
import com.saltdamage.transport.algorithm.SaltMigrationModel;
import com.saltdamage.transport.algorithm.util.PorosityGrid;
import com.saltdamage.transport.algorithm.util.Vector3D;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaltMigrationService {

    private final SaltMigrationModel saltMigrationModel;

    @Value("${algorithm.salt-migration.grid-rows:10}")
    private int gridRows;

    @Value("${algorithm.salt-migration.grid-cols:10}")
    private int gridCols;

    @Value("${algorithm.salt-migration.grid-delta-x:0.01}")
    private double gridDeltaX;

    @Value("${algorithm.salt-migration.grid-delta-y:0.01}")
    private double gridDeltaY;

    @Value("${algorithm.salt-migration.bottom-porosity:0.40}")
    private double bottomPorosity;

    @Value("${algorithm.salt-migration.top-porosity:0.15}")
    private double topPorosity;

    public AnalysisMessage analyzeSensorData(SensorDataMessage sensorData) {
        log.info("开始盐分运移分析: deviceId={}, tombId={}, sensorType={}",
                sensorData.getDeviceId(), sensorData.getTombId(), sensorData.getSensorType());

        double[][] concentrationGrid = buildConcentrationGrid(sensorData);
        double[][] pressureGrid = buildPressureGrid(sensorData);
        PorosityGrid porosityGrid = buildPorosityGrid();

        int centerX = gridCols / 2;
        int centerY = gridRows / 2;

        Vector3D velocity = saltMigrationModel.calculateMigrationVelocityHeterogeneous(
                concentrationGrid, pressureGrid, porosityGrid,
                0, 0, centerX, centerY);

        double totalSalt = calculateTotalSalt(concentrationGrid);
        double velocityMagnitude = velocity.magnitude();

        String riskLevel = determineRiskLevel(velocityMagnitude, totalSalt);

        AnalysisMessage message = new AnalysisMessage();
        message.setMessageId(sensorData.getMessageId() + "-migration");
        message.setTimestamp(System.currentTimeMillis());
        message.setDeviceId(sensorData.getDeviceId());
        message.setTombId(sensorData.getTombId());
        message.setChamberId(sensorData.getChamberId());
        message.setMigrationVelocityX(velocity.getX());
        message.setMigrationVelocityY(velocity.getY());
        message.setMigrationVelocityZ(velocity.getZ());
        message.setRiskLevel(riskLevel);
        message.setPredictionHours(24);
        message.setPredictedTotalSalt(totalSalt * (1 + velocityMagnitude * 86400));
        message.setPredictedCrystallizationPressure(0.0);

        log.info("盐分运移分析完成: deviceId={}, 运移速度=({}), 风险等级={}, 总盐量={}",
                sensorData.getDeviceId(), velocity, riskLevel, totalSalt);

        return message;
    }

    private double[][] buildConcentrationGrid(SensorDataMessage sensorData) {
        double[][] grid = new double[gridRows][gridCols];
        double totalSalt = sensorData.getTotalSalt() != null ? sensorData.getTotalSalt() : 0.0;

        double naPlus = sensorData.getNaPlus() != null ? sensorData.getNaPlus() : 0.0;
        double ca2Plus = sensorData.getCa2Plus() != null ? sensorData.getCa2Plus() : 0.0;
        double so42Minus = sensorData.getSo42Minus() != null ? sensorData.getSo42Minus() : 0.0;
        double clMinus = sensorData.getClMinus() != null ? sensorData.getClMinus() : 0.0;

        for (int y = 0; y < gridRows; y++) {
            for (int x = 0; x < gridCols; x++) {
                double distance = Math.sqrt(
                        Math.pow(x - gridCols / 2.0, 2) +
                        Math.pow(y - gridRows / 2.0, 2));
                double decay = Math.exp(-distance * 0.2);
                grid[y][x] = totalSalt * decay;
            }
        }
        return grid;
    }

    private double[][] buildPressureGrid(SensorDataMessage sensorData) {
        double[][] grid = new double[gridRows][gridCols];

        double basePressure = 101325.0;
        double humidity = sensorData.getHumidity() != null ? sensorData.getHumidity() : 50.0;
        double capillaryPressure = humidity * 10.0;

        for (int y = 0; y < gridRows; y++) {
            double heightFactor = 1.0 - (double) y / gridRows;
            for (int x = 0; x < gridCols; x++) {
                grid[y][x] = basePressure + capillaryPressure * heightFactor;
            }
        }
        return grid;
    }

    private PorosityGrid buildPorosityGrid() {
        return PorosityGrid.generateCapillaryZone(
                gridRows, gridCols, gridDeltaX, gridDeltaY,
                bottomPorosity, topPorosity, 0.05);
    }

    private double calculateTotalSalt(double[][] concentrationGrid) {
        double total = 0;
        for (int y = 0; y < concentrationGrid.length; y++) {
            for (int x = 0; x < concentrationGrid[0].length; x++) {
                total += concentrationGrid[y][x];
            }
        }
        return total;
    }

    private String determineRiskLevel(double velocityMagnitude, double totalSalt) {
        double velocityThreshold = 1.0e-8;
        double saltThreshold = 100.0;

        if (velocityMagnitude > velocityThreshold * 10 || totalSalt > saltThreshold * 3) {
            return "HIGH";
        } else if (velocityMagnitude > velocityThreshold * 3 || totalSalt > saltThreshold * 1.5) {
            return "MEDIUM";
        } else if (velocityMagnitude > velocityThreshold || totalSalt > saltThreshold) {
            return "LOW";
        }
        return "NORMAL";
    }
}
