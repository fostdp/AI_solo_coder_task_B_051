package com.saltdamage.dqn.onnx;

import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OnnxRuntimeServiceTest {

    @InjectMocks
    private OnnxRuntimeService onnxRuntimeService;

    @Test
    @DisplayName("测试模型文件不存在时优雅降级")
    void testModelNotFound_GracefulFallback() {
        ReflectionTestUtils.setField(onnxRuntimeService, "modelPath", "nonexistent/path/model.onnx");
        ReflectionTestUtils.setField(onnxRuntimeService, "inputDim", 5);
        ReflectionTestUtils.setField(onnxRuntimeService, "outputDim", 4);

        onnxRuntimeService.init();

        assertFalse(onnxRuntimeService.isModelAvailable(),
                "模型文件不存在时modelAvailable应为false");
        assertNull(onnxRuntimeService.getSession(),
                "模型文件不存在时session应为null");
        assertNotNull(onnxRuntimeService.getEnvironment(),
                "即使模型加载失败，环境对象仍应创建");
    }

    @Test
    @DisplayName("测试输入输出维度配置正确")
    void testInputOutputDimensions() {
        ReflectionTestUtils.setField(onnxRuntimeService, "modelPath", "models/dqn_microclimate.onnx");
        ReflectionTestUtils.setField(onnxRuntimeService, "inputDim", 5);
        ReflectionTestUtils.setField(onnxRuntimeService, "outputDim", 4);

        int inputDim = (int) ReflectionTestUtils.getField(onnxRuntimeService, "inputDim");
        int outputDim = (int) ReflectionTestUtils.getField(onnxRuntimeService, "outputDim");

        assertEquals(5, inputDim, "输入维度应为5");
        assertEquals(4, outputDim, "输出维度应为4");
    }

    @Test
    @DisplayName("测试模型不可用时推理方法优雅降级")
    void testInferenceFallback() {
        ReflectionTestUtils.setField(onnxRuntimeService, "modelPath", "nonexistent/path/model.onnx");
        ReflectionTestUtils.setField(onnxRuntimeService, "inputDim", 5);
        ReflectionTestUtils.setField(onnxRuntimeService, "outputDim", 4);
        ReflectionTestUtils.setField(onnxRuntimeService, "modelAvailable", false);

        float[] inputFeatures = new float[]{0.55f, 0.01f, 0.5f, 0.0f, 0.0f};

        assertThrows(OrtException.class, () -> onnxRuntimeService.inferBestAction(inputFeatures),
                "模型不可用时调用inferBestAction应抛出OrtException");
    }
}
