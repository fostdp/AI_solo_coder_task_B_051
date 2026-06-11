package com.saltdamage.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
public class FEMSolver {

    private long meshHandle;

    @Value("${algorithm.fem.max-iterations:100}")
    private int maxIterations;

    @Value("${algorithm.fem.time-step:1.0}")
    private double defaultTimeStep;

    static {
        try {
            System.loadLibrary("fem_solver");
            log.info("FEM求解器JNI库加载成功");
        } catch (UnsatisfiedLinkError e) {
            log.warn("FEM求解器JNI库未找到，将使用Java降级求解器: {}", e.getMessage());
        }
    }

    public native long nativeInitMesh(double[] nodeX, double[] nodeY, double[] nodeZ,
                                       int[] elementNodes, int nodeCount, int elementCount);

    public native double[] nativeSolve(long meshHandle,
                                        double[] concentration, double[] porosity,
                                        double diffusionCoeff, double timeStep, int iterations);

    public native void nativeReleaseMesh(long meshHandle);

    public double[] solve(
            double[] nodeX, double[] nodeY, double[] nodeZ,
            int[] elementNodes, int nodeCount, int elementCount,
            double[] concentration, double[] porosity,
            double diffusionCoeff) {

        validateInputs(nodeX, nodeY, nodeZ, elementNodes, nodeCount, elementCount,
                concentration, porosity);

        if (meshHandle != 0) {
            nativeReleaseMesh(meshHandle);
            meshHandle = 0;
        }

        try {
            meshHandle = nativeInitMesh(nodeX, nodeY, nodeZ, elementNodes, nodeCount, elementCount);

            if (meshHandle == 0) {
                log.warn("JNI网格初始化失败，降级到Java求解器");
                return solveJavaFallback(concentration, porosity, diffusionCoeff, defaultTimeStep);
            }

            double[] result = nativeSolve(meshHandle, concentration, porosity,
                    diffusionCoeff, defaultTimeStep, maxIterations);

            if (result == null) {
                log.warn("JNI求解失败，降级到Java求解器");
                return solveJavaFallback(concentration, porosity, diffusionCoeff, defaultTimeStep);
            }

            return result;

        } catch (UnsatisfiedLinkError e) {
            log.warn("JNI调用失败，降级到Java求解器: {}", e.getMessage());
            return solveJavaFallback(concentration, porosity, diffusionCoeff, defaultTimeStep);
        }
    }

    private void validateInputs(
            double[] nodeX, double[] nodeY, double[] nodeZ,
            int[] elementNodes, int nodeCount, int elementCount,
            double[] concentration, double[] porosity) {

        if (nodeX == null || nodeY == null || nodeZ == null) {
            throw new IllegalArgumentException("节点坐标数组不能为null");
        }

        if (nodeX.length != nodeCount || nodeY.length != nodeCount || nodeZ.length != nodeCount) {
            throw new IllegalArgumentException(String.format(
                    "节点坐标数组长度不一致: X=%d, Y=%d, Z=%d, 期望=%d",
                    nodeX.length, nodeY.length, nodeZ.length, nodeCount));
        }

        if (elementNodes == null) {
            throw new IllegalArgumentException("单元节点数组不能为null");
        }

        if (elementNodes.length != elementCount * 4) {
            throw new IllegalArgumentException(String.format(
                    "单元节点数组长度 %d != elementCount*4=%d",
                    elementNodes.length, elementCount * 4));
        }

        for (int i = 0; i < elementNodes.length; i++) {
            if (elementNodes[i] < 0 || elementNodes[i] >= nodeCount) {
                throw new IllegalArgumentException(String.format(
                        "单元节点索引越界: elementNodes[%d]=%d, nodeCount=%d",
                        i, elementNodes[i], nodeCount));
            }
        }

        if (concentration != null && concentration.length != nodeCount) {
            throw new IllegalArgumentException(String.format(
                    "浓度数组长度 %d != nodeCount %d", concentration.length, nodeCount));
        }

        if (porosity != null && porosity.length != nodeCount) {
            throw new IllegalArgumentException(String.format(
                    "孔隙度数组长度 %d != nodeCount %d", porosity.length, nodeCount));
        }
    }

    private double[] solveJavaFallback(
            double[] concentration, double[] porosity,
            double diffusionCoeff, double timeStep) {

        int n = concentration.length;
        double[] result = Arrays.copyOf(concentration, n);

        for (int iter = 0; iter < maxIterations; iter++) {
            double[] next = new double[n];
            for (int i = 0; i < n; i++) {
                double d = diffusionCoeff * porosity[i] * porosity[i];
                double laplacian = 0;
                int count = 0;

                if (i > 0) { laplacian += result[i - 1] - result[i]; count++; }
                if (i < n - 1) { laplacian += result[i + 1] - result[i]; count++; }

                if (count > 0) {
                    laplacian /= count;
                }

                next[i] = result[i] + d * laplacian * timeStep;
            }
            result = next;
        }

        return result;
    }

    public void release() {
        if (meshHandle != 0) {
            try {
                nativeReleaseMesh(meshHandle);
            } catch (UnsatisfiedLinkError ignored) {
            }
            meshHandle = 0;
        }
    }
}
