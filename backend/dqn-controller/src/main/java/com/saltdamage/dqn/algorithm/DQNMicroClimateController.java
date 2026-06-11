package com.saltdamage.dqn.algorithm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
public class DQNMicroClimateController {

    private static final int INPUT_DIM = 5;

    @Value("${algorithm.dqn.hidden1-dim:32}")
    private int hidden1Dim = 32;

    @Value("${algorithm.dqn.hidden2-dim:16}")
    private int hidden2Dim = 16;

    private static final int OUTPUT_DIM = 4;

    @Value("${algorithm.dqn.learning-rate:0.001}")
    private double learningRate = 0.001;

    @Value("${algorithm.dqn.gamma:0.95}")
    private double gamma = 0.95;

    @Value("${algorithm.dqn.replay-buffer-size:10000}")
    private int replayBufferSize = 10000;

    @Value("${algorithm.dqn.batch-size:32}")
    private int batchSize = 32;

    @Value("${algorithm.dqn.target-update-freq:100}")
    private int targetUpdateFreq = 100;

    @Value("${algorithm.dqn.epsilon-start:1.0}")
    private double epsilonStart = 1.0;

    @Value("${algorithm.dqn.epsilon-end:0.05}")
    private double epsilonEnd = 0.05;

    @Value("${algorithm.dqn.epsilon-decay:0.995}")
    private double epsilonDecay = 0.995;

    @Value("${algorithm.dqn.base-rh:0.70}")
    private double baseRh = 0.70;

    @Value("${algorithm.dqn.rh-amplitude:0.15}")
    private double rhAmplitude = 0.15;

    @Value("${algorithm.dqn.dehumidifier-effect:0.02}")
    private double dehumidifierEffect = 0.02;

    @Value("${algorithm.dqn.humidifier-effect:0.03}")
    private double humidifierEffect = 0.03;

    @Value("${algorithm.dqn.leakage-rate:0.001}")
    private double leakageRate = 0.001;

    private static final double TIME_STEP = 1.0;

    @Value("${algorithm.dqn.reward-safe:1.0}")
    private double rewardSafe = 1.0;

    @Value("${algorithm.dqn.penalty-deliquescence:-5.0}")
    private double penaltyDeliquescence = -5.0;

    @Value("${algorithm.dqn.penalty-extreme:-10.0}")
    private double penaltyExtreme = -10.0;

    @Value("${algorithm.dqn.penalty-dehumidifier:-0.5}")
    private double penaltyDehumidifier = -0.5;

    @Value("${algorithm.dqn.penalty-humidifier:-0.5}")
    private double penaltyHumidifier = -0.5;

    @Value("${algorithm.dqn.penalty-both:-1.5}")
    private double penaltyBoth = -1.5;

    @Value("${algorithm.dqn.penalty-switch:-1.0}")
    private double penaltySwitch;

    @Value("${algorithm.dqn.action-persistence-bonus:0.3}")
    private double actionPersistenceBonus;

    private int actionPersistenceCount;

    @Value("${algorithm.dqn.reward-goal:100.0}")
    private double rewardGoal = 100.0;

    private static final int GOAL_HOURS = 24;

    private double epsilon;

    private int totalSteps;

    private State currentState;

    private int lastAction;

    private int consecutiveSafeHours;

    private double currentTime;

    private List<Double> rhHistory;

    private DQNNetwork qNetwork;

    private DQNNetwork targetNetwork;

    private ReplayBuffer replayBuffer;

    private Random random;

    public DQNMicroClimateController() {
        this.random = new Random(42);
        this.epsilon = epsilonStart;
        this.totalSteps = 0;
        this.consecutiveSafeHours = 0;
        this.currentTime = 0;
        this.lastAction = 0;
        this.rhHistory = new ArrayList<>();
    }

    private void initializeNetworks() {
        if (qNetwork == null) {
            this.qNetwork = new DQNNetwork(INPUT_DIM, hidden1Dim, hidden2Dim, OUTPUT_DIM, random);
            this.targetNetwork = new DQNNetwork(INPUT_DIM, hidden1Dim, hidden2Dim, OUTPUT_DIM, random);
            this.targetNetwork.copyWeightsFrom(qNetwork);
            this.replayBuffer = new ReplayBuffer(replayBufferSize);
            log.info("DQN网络初始化完成 - 输入: {}, 隐藏1: {}, 隐藏2: {}, 输出: {}",
                    INPUT_DIM, hidden1Dim, hidden2Dim, OUTPUT_DIM);
        }
    }

    public void resetEnvironment(double initialRh, int initialHour) {
        initializeNetworks();

        this.currentTime = initialHour;
        this.lastAction = 0;
        this.actionPersistenceCount = 0;
        this.consecutiveSafeHours = 0;
        this.rhHistory = new ArrayList<>();
        this.rhHistory.add(initialRh);

        double rhTrend = 0.0;

        this.currentState = State.builder()
                .currentRh(initialRh)
                .rhTrend(rhTrend)
                .hourOfDay(initialHour / 23.0)
                .dehumidifierOn(0)
                .humidifierOn(0)
                .build();

        log.debug("环境重置 - RH: {}, 小时: {}", String.format("%.2f", initialRh), initialHour);
    }

    public List<Double> train(int episodes) {
        initializeNetworks();

        log.info("开始DQN训练 - 回合数: {}, ε: {:.3f}, 学习率: {}",
                episodes, epsilon, learningRate);

        List<Double> episodeRewards = new ArrayList<>();
        int stepsPerEpisode = 24 * 7;

        for (int episode = 0; episode < episodes; episode++) {
            double initialRh = 0.4 + random.nextDouble() * 0.4;
            int initialHour = random.nextInt(24);
            resetEnvironment(initialRh, initialHour);

            double totalReward = 0.0;

            for (int step = 0; step < stepsPerEpisode; step++) {
                int action = selectAction(currentState);

                StepResult result = step(action);

                Transition transition = Transition.builder()
                        .state(currentState)
                        .action(action)
                        .reward(result.getReward())
                        .nextState(result.getNextState())
                        .done(result.isDone())
                        .build();
                replayBuffer.add(transition);

                currentState = result.getNextState();
                totalReward += result.getReward();

                if (replayBuffer.size() >= batchSize) {
                    trainBatch();
                }

                totalSteps++;
                if (totalSteps % targetUpdateFreq == 0) {
                    targetNetwork.copyWeightsFrom(qNetwork);
                    log.debug("目标网络更新 - 步数: {}", totalSteps);
                }

                epsilon = Math.max(epsilonEnd, epsilon * epsilonDecay);
            }

            episodeRewards.add(totalReward);

            if ((episode + 1) % 10 == 0) {
                log.info("训练进度 - 回合: {}/{}, 奖励: {:.2f}, ε: {:.4f}",
                        episode + 1, episodes, totalReward, epsilon);
            }
        }

        log.info("训练完成 - 总回合数: {}, 最终ε: {:.4f}, 总步数: {}",
                episodes, epsilon, totalSteps);

        return episodeRewards;
    }

    public int selectAction(State state) {
        initializeNetworks();

        if (random.nextDouble() < epsilon) {
            return random.nextInt(OUTPUT_DIM);
        } else {
            double[] qValues = qNetwork.forward(state.toArray());
            return argmax(qValues);
        }
    }

    public int selectBestAction(State state) {
        initializeNetworks();
        double[] qValues = qNetwork.forward(state.toArray());
        return argmax(qValues);
    }

    public int selectBestActionWithOnnx(ai.onnxruntime.OrtEnvironment env, ai.onnxruntime.OrtSession session, State state) {
        if (session == null) return selectBestAction(state);
        try {
            float[] input = new float[]{
                    (float) state.getCurrentRh(),
                    (float) state.getRhTrend(),
                    (float) state.getHourOfDay() / 24.0f,
                    (float) state.getDehumidifierOn() ? 1.0f : 0.0f,
                    (float) state.getHumidifierOn() ? 1.0f : 0.0f
            };
            java.nio.FloatBuffer buffer = java.nio.FloatBuffer.wrap(input);
            long[] shape = new long[]{1, 5};
            ai.onnxruntime.OnnxTensor tensor = ai.onnxruntime.OnnxTensor.createTensor(env, buffer, shape);
            java.util.Map<String, ai.onnxruntime.OnnxValue> result = session.run(java.util.Collections.singletonMap("input", tensor));
            float[][] output = (float[][]) result.values().iterator().next().getValue();
            float[] qValues = output[0];
            int bestAction = 0;
            for (int i = 1; i < qValues.length; i++) {
                if (qValues[i] > qValues[bestAction]) bestAction = i;
            }
            result.values().forEach(v -> { try { v.close(); } catch (Exception e) {} });
            tensor.close();
            return bestAction;
        } catch (Exception e) {
            log.warn("ONNX inference failed, falling back to Java: {}", e.getMessage());
            return selectBestAction(state);
        }
    }

    public double[] getQValues(State state) {
        initializeNetworks();
        return qNetwork.forward(state.toArray());
    }

    public StepResult step(int action) {
        initializeNetworks();

        boolean dehumidifierOn = (action == 1 || action == 3);
        boolean humidifierOn = (action == 2 || action == 3);

        double ambientRh = calculateAmbientRh(currentTime);

        double currentRh = currentState.getCurrentRh();
        double newRh = currentRh;

        if (dehumidifierOn) {
            newRh -= dehumidifierEffect * TIME_STEP;
        }

        if (humidifierOn) {
            newRh += humidifierEffect * TIME_STEP;
        }

        newRh += (ambientRh - currentRh) * leakageRate * TIME_STEP;

        newRh = Math.max(0.0, Math.min(1.0, newRh));

        currentTime += TIME_STEP;
        int hourOfDay = ((int) currentTime) % 24;

        rhHistory.add(newRh);
        if (rhHistory.size() > 12) {
            rhHistory.remove(0);
        }

        double rhTrend = calculateRhTrend();

        double reward = calculateReward(newRh, action, dehumidifierOn, humidifierOn);

        if (isInSafeZone(newRh)) {
            consecutiveSafeHours++;
        } else {
            consecutiveSafeHours = 0;
        }

        boolean done = false;
        if (consecutiveSafeHours >= GOAL_HOURS) {
            reward += rewardGoal;
            done = true;
            log.debug("目标达成！连续{}小时避开潮解带", GOAL_HOURS);
        }

        State nextState = State.builder()
                .currentRh(newRh)
                .rhTrend(rhTrend)
                .hourOfDay(hourOfDay / 23.0)
                .dehumidifierOn(dehumidifierOn ? 1 : 0)
                .humidifierOn(humidifierOn ? 1 : 0)
                .build();

        lastAction = action;

        return new StepResult(nextState, reward, done);
    }

    private double calculateAmbientRh(double time) {
        double phase = 2 * Math.PI * time / 24.0;
        double rh = baseRh + rhAmplitude * Math.sin(phase - Math.PI / 2);
        return Math.max(0.0, Math.min(1.0, rh));
    }

    private double calculateRhTrend() {
        if (rhHistory.size() < 2) {
            return 0.0;
        }

        int n = rhHistory.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = rhHistory.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

        double maxTrend = 0.1;
        return Math.max(-1.0, Math.min(1.0, slope / maxTrend));
    }

    private double calculateReward(double rh, int action, boolean dehumidifierOn, boolean humidifierOn) {
        double reward = 0.0;

        if (rh < 0.4 || rh > 0.9) {
            reward += penaltyExtreme;
        } else if (rh >= 0.65 && rh <= 0.80) {
            reward += penaltyDeliquescence;
        } else {
            reward += rewardSafe;
        }

        if (dehumidifierOn && humidifierOn) {
            reward += penaltyBoth;
        } else if (dehumidifierOn) {
            reward += penaltyDehumidifier;
        } else if (humidifierOn) {
            reward += penaltyHumidifier;
        }

        if (action != lastAction) {
            reward += penaltySwitch;
            actionPersistenceCount = 0;
        } else {
            actionPersistenceCount++;
            if (actionPersistenceCount >= 1 && action != 0) {
                reward += actionPersistenceBonus;
            } else if (actionPersistenceCount >= 3 && action == 0) {
                reward += actionPersistenceBonus * 0.5;
            }
        }

        return reward;
    }

    private boolean isInSafeZone(double rh) {
        return (rh >= 0.4 && rh <= 0.65) || (rh >= 0.80 && rh <= 0.90);
    }

    private void trainBatch() {
        List<Transition> batch = replayBuffer.sample(batchSize, random);

        for (Transition transition : batch) {
            double[] stateArray = transition.getState().toArray();
            double[] nextStateArray = transition.getNextState().toArray();

            double[] currentQ = qNetwork.forward(stateArray);

            double[] nextQ = targetNetwork.forward(nextStateArray);
            double maxNextQ = max(nextQ);

            double target = transition.getReward();
            if (!transition.isDone()) {
                target += gamma * maxNextQ;
            }

            double[] targetQ = currentQ.clone();
            targetQ[transition.getAction()] = target;

            qNetwork.backward(stateArray, currentQ, targetQ, learningRate);
        }
    }

    private int argmax(double[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private double max(double[] array) {
        double maxValue = array[0];
        for (double v : array) {
            if (v > maxValue) {
                maxValue = v;
            }
        }
        return maxValue;
    }

    public void saveModel(String filePath) throws IOException {
        initializeNetworks();

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(qNetwork.getWeights1());
            oos.writeObject(qNetwork.getBiases1());
            oos.writeObject(qNetwork.getWeights2());
            oos.writeObject(qNetwork.getBiases2());
            oos.writeObject(qNetwork.getWeights3());
            oos.writeObject(qNetwork.getBiases3());
            oos.writeDouble(epsilon);
            oos.writeInt(totalSteps);
        }

        log.info("模型已保存到: {}", filePath);
    }

    public void loadModel(String filePath) throws IOException, ClassNotFoundException {
        initializeNetworks();

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            double[][] w1 = (double[][]) ois.readObject();
            double[] b1 = (double[]) ois.readObject();
            double[][] w2 = (double[][]) ois.readObject();
            double[] b2 = (double[]) ois.readObject();
            double[][] w3 = (double[][]) ois.readObject();
            double[] b3 = (double[]) ois.readObject();
            epsilon = ois.readDouble();
            totalSteps = ois.readInt();

            qNetwork.setWeights(w1, b1, w2, b2, w3, b3);
            targetNetwork.copyWeightsFrom(qNetwork);
        }

        log.info("模型已从 {} 加载", filePath);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class State {
        private double currentRh;
        private double rhTrend;
        private double hourOfDay;
        private int dehumidifierOn;
        private int humidifierOn;

        public double[] toArray() {
            return new double[]{
                    currentRh,
                    rhTrend,
                    hourOfDay,
                    dehumidifierOn,
                    humidifierOn
            };
        }

        public static State fromArray(double[] array) {
            if (array.length != INPUT_DIM) {
                throw new IllegalArgumentException("状态数组长度必须为" + INPUT_DIM);
            }
            return State.builder()
                    .currentRh(array[0])
                    .rhTrend(array[1])
                    .hourOfDay(array[2])
                    .dehumidifierOn((int) array[3])
                    .humidifierOn((int) array[4])
                    .build();
        }

        public double getHumidity() {
            return currentRh;
        }

        public double getHumidityTrend() {
            return rhTrend;
        }

        public boolean isDehumidifierOn() {
            return dehumidifierOn == 1;
        }

        public boolean isHumidifierOn() {
            return humidifierOn == 1;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transition {
        private State state;
        private int action;
        private double reward;
        private State nextState;
        private boolean done;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResult {
        private State nextState;
        private double reward;
        private boolean done;
    }

    public static class DQNNetwork {
        private int inputDim;
        private int hidden1Dim;
        private int hidden2Dim;
        private int outputDim;
        private double[][] weights1;
        private double[] biases1;
        private double[][] weights2;
        private double[] biases2;
        private double[][] weights3;
        private double[] biases3;

        public DQNNetwork(int inputDim, int hidden1Dim, int hidden2Dim, int outputDim, Random random) {
            this.inputDim = inputDim;
            this.hidden1Dim = hidden1Dim;
            this.hidden2Dim = hidden2Dim;
            this.outputDim = outputDim;

            this.weights1 = initializeWeights(inputDim, hidden1Dim, random);
            this.biases1 = new double[hidden1Dim];
            this.weights2 = initializeWeights(hidden1Dim, hidden2Dim, random);
            this.biases2 = new double[hidden2Dim];
            this.weights3 = initializeWeights(hidden2Dim, outputDim, random);
            this.biases3 = new double[outputDim];
        }

        private double[][] initializeWeights(int fanIn, int fanOut, Random random) {
            double[][] weights = new double[fanOut][fanIn];
            double scale = Math.sqrt(2.0 / (fanIn + fanOut));

            for (int i = 0; i < fanOut; i++) {
                for (int j = 0; j < fanIn; j++) {
                    weights[i][j] = random.nextGaussian() * scale;
                }
            }

            return weights;
        }

        public double[] forward(double[] input) {
            double[] hidden1 = new double[hidden1Dim];
            for (int i = 0; i < hidden1Dim; i++) {
                double sum = biases1[i];
                for (int j = 0; j < inputDim; j++) {
                    sum += weights1[i][j] * input[j];
                }
                hidden1[i] = relu(sum);
            }

            double[] hidden2 = new double[hidden2Dim];
            for (int i = 0; i < hidden2Dim; i++) {
                double sum = biases2[i];
                for (int j = 0; j < hidden1Dim; j++) {
                    sum += weights2[i][j] * hidden1[j];
                }
                hidden2[i] = relu(sum);
            }

            double[] output = new double[outputDim];
            for (int i = 0; i < outputDim; i++) {
                double sum = biases3[i];
                for (int j = 0; j < hidden2Dim; j++) {
                    sum += weights3[i][j] * hidden2[j];
                }
                output[i] = sum;
            }

            return output;
        }

        public void backward(double[] input, double[] currentOutput, double[] targetOutput, double learningRate) {
            double[] hidden1 = new double[hidden1Dim];
            double[] z1 = new double[hidden1Dim];
            for (int i = 0; i < hidden1Dim; i++) {
                double sum = biases1[i];
                for (int j = 0; j < inputDim; j++) {
                    sum += weights1[i][j] * input[j];
                }
                z1[i] = sum;
                hidden1[i] = relu(sum);
            }

            double[] hidden2 = new double[hidden2Dim];
            double[] z2 = new double[hidden2Dim];
            for (int i = 0; i < hidden2Dim; i++) {
                double sum = biases2[i];
                for (int j = 0; j < hidden1Dim; j++) {
                    sum += weights2[i][j] * hidden1[j];
                }
                z2[i] = sum;
                hidden2[i] = relu(sum);
            }

            double[] delta3 = new double[outputDim];
            for (int i = 0; i < outputDim; i++) {
                delta3[i] = (currentOutput[i] - targetOutput[i]);
            }

            double[] delta2 = new double[hidden2Dim];
            for (int i = 0; i < hidden2Dim; i++) {
                double sum = 0;
                for (int j = 0; j < outputDim; j++) {
                    sum += weights3[j][i] * delta3[j];
                }
                delta2[i] = sum * reluDerivative(z2[i]);
            }

            double[] delta1 = new double[hidden1Dim];
            for (int i = 0; i < hidden1Dim; i++) {
                double sum = 0;
                for (int j = 0; j < hidden2Dim; j++) {
                    sum += weights2[j][i] * delta2[j];
                }
                delta1[i] = sum * reluDerivative(z1[i]);
            }

            for (int i = 0; i < outputDim; i++) {
                biases3[i] -= learningRate * delta3[i];
                for (int j = 0; j < hidden2Dim; j++) {
                    weights3[i][j] -= learningRate * delta3[i] * hidden2[j];
                }
            }

            for (int i = 0; i < hidden2Dim; i++) {
                biases2[i] -= learningRate * delta2[i];
                for (int j = 0; j < hidden1Dim; j++) {
                    weights2[i][j] -= learningRate * delta2[i] * hidden1[j];
                }
            }

            for (int i = 0; i < hidden1Dim; i++) {
                biases1[i] -= learningRate * delta1[i];
                for (int j = 0; j < inputDim; j++) {
                    weights1[i][j] -= learningRate * delta1[i] * input[j];
                }
            }
        }

        public void copyWeightsFrom(DQNNetwork other) {
            for (int i = 0; i < hidden1Dim; i++) {
                biases1[i] = other.biases1[i];
                System.arraycopy(other.weights1[i], 0, weights1[i], 0, inputDim);
            }

            for (int i = 0; i < hidden2Dim; i++) {
                biases2[i] = other.biases2[i];
                System.arraycopy(other.weights2[i], 0, weights2[i], 0, hidden1Dim);
            }

            for (int i = 0; i < outputDim; i++) {
                biases3[i] = other.biases3[i];
                System.arraycopy(other.weights3[i], 0, weights3[i], 0, hidden2Dim);
            }
        }

        public void setWeights(double[][] w1, double[] b1, double[][] w2, double[] b2, double[][] w3, double[] b3) {
            this.weights1 = w1;
            this.biases1 = b1;
            this.weights2 = w2;
            this.biases2 = b2;
            this.weights3 = w3;
            this.biases3 = b3;
        }

        public double[][] getWeights1() {
            return weights1;
        }

        public double[] getBiases1() {
            return biases1;
        }

        public double[][] getWeights2() {
            return weights2;
        }

        public double[] getBiases2() {
            return biases2;
        }

        public double[][] getWeights3() {
            return weights3;
        }

        public double[] getBiases3() {
            return biases3;
        }

        private double relu(double x) {
            return Math.max(0, x);
        }

        private double reluDerivative(double x) {
            return x > 0 ? 1.0 : 0.0;
        }
    }

    public static class ReplayBuffer {
        private int capacity;
        private List<Transition> buffer;
        private int position;

        public ReplayBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new ArrayList<>(capacity);
            this.position = 0;
        }

        public void add(Transition transition) {
            if (buffer.size() < capacity) {
                buffer.add(transition);
            } else {
                buffer.set(position, transition);
            }
            position = (position + 1) % capacity;
        }

        public List<Transition> sample(int batchSize, Random random) {
            List<Transition> batch = new ArrayList<>(batchSize);
            int size = buffer.size();

            for (int i = 0; i < batchSize; i++) {
                int index = random.nextInt(size);
                batch.add(buffer.get(index));
            }

            return batch;
        }

        public int size() {
            return buffer.size();
        }

        public int getCapacity() {
            return capacity;
        }

        public void clear() {
            buffer.clear();
            position = 0;
        }
    }

    public double getEpsilon() {
        return epsilon;
    }

    public State getCurrentState() {
        return currentState;
    }

    public double getCurrentTime() {
        return currentTime;
    }

    public int getConsecutiveSafeHours() {
        return consecutiveSafeHours;
    }
}
