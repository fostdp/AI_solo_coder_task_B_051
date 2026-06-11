package com.saltdamage.dqn.onnx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saltdamage.dqn.algorithm.DQNMicroClimateController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

@Slf4j
@Component
public class OnnxModelExporter {

    public void exportWeights(DQNMicroClimateController.DQNNetwork network, String outputPath) {
        try {
            WeightData weightData = new WeightData();

            Field w1Field = DQNMicroClimateController.DQNNetwork.class.getDeclaredField("weights1");
            w1Field.setAccessible(true);
            weightData.w1 = (double[][]) w1Field.get(network);

            Field b1Field = DQNMicroClimateController.DQNNetwork.class.getDeclaredField("biases1");
            b1Field.setAccessible(true);
            weightData.b1 = (double[]) b1Field.get(network);

            Field w2Field = DQNMicroClimateController.DQNNetwork.class.getDeclaredField("weights2");
            w2Field.setAccessible(true);
            weightData.w2 = (double[][]) w2Field.get(network);

            Field b2Field = DQNMicroClimateController.DQNNetwork.class.getDeclaredField("biases2");
            b2Field.setAccessible(true);
            weightData.b2 = (double[]) b2Field.get(network);

            Field w3Field = DQNMicroClimateController.DQNNetwork.class.getDeclaredField("weights3");
            w3Field.setAccessible(true);
            weightData.w3 = (double[][]) w3Field.get(network);

            Field b3Field = DQNMicroClimateController.DQNNetwork.class.getDeclaredField("biases3");
            b3Field.setAccessible(true);
            weightData.b3 = (double[]) b3Field.get(network);

            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(new File(outputPath), weightData);
            log.info("Model weights exported to JSON: {}", outputPath);
        } catch (Exception e) {
            log.error("Failed to export model weights: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to export model weights", e);
        }
    }

    static class WeightData {
        public double[][] w1;
        public double[][] w2;
        public double[][] w3;
        public double[] b1;
        public double[] b2;
        public double[] b3;
    }
}
