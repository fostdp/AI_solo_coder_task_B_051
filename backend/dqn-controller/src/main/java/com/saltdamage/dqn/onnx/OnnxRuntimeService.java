package com.saltdamage.dqn.onnx;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class OnnxRuntimeService {

    @Value("${dqn.onnx-model-path}")
    private String modelPath;

    @Value("${dqn.input-dim}")
    private int inputDim;

    @Value("${dqn.output-dim}")
    private int outputDim;

    private OrtEnvironment env;
    private OrtSession session;
    private boolean modelAvailable;

    @jakarta.annotation.PostConstruct
    public void init() {
        env = OrtEnvironment.getEnvironment();
        try {
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                log.warn("ONNX model file not found at: {}", modelPath);
                modelAvailable = false;
                return;
            }
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            session = env.createSession(modelFile.getAbsolutePath(), options);
            modelAvailable = true;
            log.info("ONNX model loaded successfully from: {}", modelPath);
        } catch (OrtException e) {
            modelAvailable = false;
            log.warn("Failed to load ONNX model, falling back to Java implementation: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
            log.info("ONNX runtime resources cleaned up");
        } catch (OrtException e) {
            log.error("Error cleaning up ONNX resources: {}", e.getMessage());
        }
    }

    public boolean isModelAvailable() {
        return modelAvailable;
    }

    public int inferBestAction(float[] inputFeatures) throws OrtException {
        if (!modelAvailable || session == null) {
            throw new OrtException("ONNX model is not available");
        }
        if (inputFeatures.length != inputDim) {
            throw new IllegalArgumentException("Input features must be of dimension " + inputDim);
        }
        FloatBuffer buffer = FloatBuffer.wrap(inputFeatures);
        long[] shape = new long[]{1, inputDim};
        try (ai.onnxruntime.OnnxTensor tensor = ai.onnxruntime.OnnxTensor.createTensor(env, buffer, shape)) {
            Map<String, ai.onnxruntime.OnnxValue> result = session.run(Collections.singletonMap("input", tensor));
            float[][] output = (float[][]) result.values().iterator().next().getValue();
            float[] qValues = output[0];
            int bestAction = 0;
            for (int i = 1; i < qValues.length; i++) {
                if (qValues[i] > qValues[bestAction]) {
                    bestAction = i;
                }
            }
            result.values().forEach(v -> {
                try {
                    v.close();
                } catch (Exception e) {
                }
            });
            return bestAction;
        }
    }

    public OrtEnvironment getEnvironment() {
        return env;
    }

    public OrtSession getSession() {
        return session;
    }
}
