package com.saltdamage.algorithm;

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

/**
 * DQN强化学习微环境调控算法
 *
 * 使用深度Q学习(DQN)控制除湿机和加湿器的启停，使相对湿度(RH)避开盐分潮解带(65%-80%)，
 * 同时最小化能耗。算法采用纯Java实现，不依赖外部深度学习框架，通过矩阵运算模拟神经网络。
 *
 * 核心特性：
 * - 状态空间：5维（当前RH、RH变化趋势、当前小时、除湿机状态、加湿器状态）
 * - 动作空间：4个离散动作（待机、除湿、加湿、同时开）
 * - 神经网络：输入层5→隐藏层32→隐藏层16→输出层4
 * - 经验回放：容量10000，batch_size=32
 * - 目标网络：每100步同步权重
 * - ε-greedy探索：ε从1.0衰减到0.05，衰减率0.995
 *
 * @author SaltDamage Algorithm Team
 */
@Slf4j
@Service
public class DQNMicroClimateController {

    // ==================== 网络结构参数 ====================

    /**
     * 输入层维度（状态空间维度）
     */
    private static final int INPUT_DIM = 5;

    /**
     * 第一隐藏层维度
     */
    @Value("${algorithm.dqn.hidden1-dim:32}")
    private int hidden1Dim = 32;

    /**
     * 第二隐藏层维度
     */
    @Value("${algorithm.dqn.hidden2-dim:16}")
    private int hidden2Dim = 16;

    /**
     * 输出层维度（动作空间维度）
     */
    private static final int OUTPUT_DIM = 4;

    // ==================== 训练超参数 ====================

    /**
     * 学习率
     */
    @Value("${algorithm.dqn.learning-rate:0.001}")
    private double learningRate = 0.001;

    /**
     * 折扣因子γ
     */
    @Value("${algorithm.dqn.gamma:0.95}")
    private double gamma = 0.95;

    /**
     * 经验回放池容量
     */
    @Value("${algorithm.dqn.replay-buffer-size:10000}")
    private int replayBufferSize = 10000;

    /**
     * 批处理大小
     */
    @Value("${algorithm.dqn.batch-size:32}")
    private int batchSize = 32;

    /**
     * 目标网络同步步数
     */
    @Value("${algorithm.dqn.target-update-freq:100}")
    private int targetUpdateFreq = 100;

    /**
     * 初始探索率ε
     */
    @Value("${algorithm.dqn.epsilon-start:1.0}")
    private double epsilonStart = 1.0;

    /**
     * 最终探索率ε
     */
    @Value("${algorithm.dqn.epsilon-end:0.05}")
    private double epsilonEnd = 0.05;

    /**
     * ε衰减率
     */
    @Value("${algorithm.dqn.epsilon-decay:0.995}")
    private double epsilonDecay = 0.995;

    // ==================== 环境模拟参数 ====================

    /**
     * 外界环境基础RH值
     */
    @Value("${algorithm.dqn.base-rh:0.70}")
    private double baseRh = 0.70;

    /**
     * 昼夜节律RH振幅
     */
    @Value("${algorithm.dqn.rh-amplitude:0.15}")
    private double rhAmplitude = 0.15;

    /**
     * 除湿机效果（每小时降低RH比例）
     */
    @Value("${algorithm.dqn.dehumidifier-effect:0.02}")
    private double dehumidifierEffect = 0.02;

    /**
     * 加湿器效果（每小时升高RH比例）
     */
    @Value("${algorithm.dqn.humidifier-effect:0.03}")
    private double humidifierEffect = 0.03;

    /**
     * 自然渗漏速率（每小时向外界RH靠拢的比例）
     */
    @Value("${algorithm.dqn.leakage-rate:0.001}")
    private double leakageRate = 0.001;

    /**
     * 时间步长（小时）
     */
    private static final double TIME_STEP = 1.0;

    // ==================== 奖励函数参数 ====================

    /**
     * 安全区奖励
     */
    @Value("${algorithm.dqn.reward-safe:1.0}")
    private double rewardSafe = 1.0;

    /**
     * 潮解带惩罚
     */
    @Value("${algorithm.dqn.penalty-deliquescence:-5.0}")
    private double penaltyDeliquescence = -5.0;

    /**
     * 极端危险区惩罚
     */
    @Value("${algorithm.dqn.penalty-extreme:-10.0}")
    private double penaltyExtreme = -10.0;

    /**
     * 除湿机能耗惩罚
     */
    @Value("${algorithm.dqn.penalty-dehumidifier:-0.5}")
    private double penaltyDehumidifier = -0.5;

    /**
     * 加湿器能耗惩罚
     */
    @Value("${algorithm.dqn.penalty-humidifier:-0.5}")
    private double penaltyHumidifier = -0.5;

    /**
     * 双设备能耗惩罚
     */
    @Value("${algorithm.dqn.penalty-both:-1.5}")
    private double penaltyBoth = -1.5;

    /**
     * 频繁切换惩罚
     */
    @Value("${algorithm.dqn.penalty-switch:-0.2}")
    private double penaltySwitch;

    /**
     * 动作持续性奖励（鼓励保持同一动作）
     * 修复缺陷：原奖励函数频繁切换惩罚(-0.2)过小，无法有效抑制频繁启停
     * 根因：仅有一个微弱的penaltySwitch=-0.2，而无对持续性的正向激励，
     *       DQN学到"快速切换动作以微调RH"的策略，导致除湿机/加湿器频繁启停，
     *       实际设备中这不仅浪费能源，还会缩短设备寿命。
     * 修复：新增 actionPersistenceBonus=0.3，当连续执行同一动作时给予正向奖励，
     *       同时将切换惩罚提升到 penaltySwitch=-1.0，使切换成本显著高于持续性收益。
     *       这迫使DQN学到"选择一个动作后持续执行"的策略。
     */
    @Value("${algorithm.dqn.action-persistence-bonus:0.3}")
    private double actionPersistenceBonus;

    /**
     * 连续执行同一动作的步数
     */
    private int actionPersistenceCount;

    /**
     * 连续安全目标奖励
     */
    @Value("${algorithm.dqn.reward-goal:100.0}")
    private double rewardGoal = 100.0;

    /**
     * 目标连续安全小时数
     */
    private static final int GOAL_HOURS = 24;

    // ==================== 运行时状态 ====================

    /**
     * 当前探索率
     */
    private double epsilon;

    /**
     * 当前训练步数
     */
    private int totalSteps;

    /**
     * 当前环境状态
     */
    private State currentState;

    /**
     * 上一动作
     */
    private int lastAction;

    /**
     * 连续安全小时计数
     */
    private int consecutiveSafeHours;

    /**
     * 当前模拟时间（小时）
     */
    private double currentTime;

    /**
     * 过去1小时RH历史记录
     */
    private List<Double> rhHistory;

    /**
     * 主Q网络
     */
    private DQNNetwork qNetwork;

    /**
     * 目标Q网络
     */
    private DQNNetwork targetNetwork;

    /**
     * 经验回放池
     */
    private ReplayBuffer replayBuffer;

    /**
     * 随机数生成器
     */
    private Random random;

    /**
     * 构造函数，初始化DQN控制器
     */
    public DQNMicroClimateController() {
        this.random = new Random(42);
        this.epsilon = epsilonStart;
        this.totalSteps = 0;
        this.consecutiveSafeHours = 0;
        this.currentTime = 0;
        this.lastAction = 0;
        this.rhHistory = new ArrayList<>();
    }

    /**
     * 初始化网络和回放池
     *
     * 必须在使用前调用此方法完成初始化。Spring Bean初始化后自动调用。
     */
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

    /**
     * 重置环境到初始状态
     *
     * 将环境状态重置为初始条件，用于新的训练回合或新的控制周期。
     *
     * @param initialRh 初始相对湿度值（归一化到[0,1]）
     * @param initialHour 初始小时（0-23）
     */
    public void resetEnvironment(double initialRh, int initialHour) {
        initializeNetworks();

        this.currentTime = initialHour;
        this.lastAction = 0;
        this.actionPersistenceCount = 0;
        this.consecutiveSafeHours = 0;
        this.rhHistory = new ArrayList<>();
        this.rhHistory.add(initialRh);

        // 计算初始RH趋势（初始为0）
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

    /**
     * 训练DQN模型
     *
     * 执行指定回合数的训练，每回合从初始状态开始，直到达到终止条件。
     * 训练过程中使用经验回放和目标网络稳定学习过程。
     *
     * @param episodes 训练回合数
     * @return 训练过程中的奖励历史记录
     */
    public List<Double> train(int episodes) {
        initializeNetworks();

        log.info("开始DQN训练 - 回合数: {}, ε: {:.3f}, 学习率: {}",
                episodes, epsilon, learningRate);

        List<Double> episodeRewards = new ArrayList<>();
        int stepsPerEpisode = 24 * 7; // 每回合模拟7天

        for (int episode = 0; episode < episodes; episode++) {
            // 随机初始状态
            double initialRh = 0.4 + random.nextDouble() * 0.4; // 40%-80%
            int initialHour = random.nextInt(24);
            resetEnvironment(initialRh, initialHour);

            double totalReward = 0.0;

            for (int step = 0; step < stepsPerEpisode; step++) {
                // 选择动作
                int action = selectAction(currentState);

                // 执行动作
                StepResult result = step(action);

                // 存储经验
                Transition transition = Transition.builder()
                        .state(currentState)
                        .action(action)
                        .reward(result.getReward())
                        .nextState(result.getNextState())
                        .done(result.isDone())
                        .build();
                replayBuffer.add(transition);

                // 更新当前状态
                currentState = result.getNextState();
                totalReward += result.getReward();

                // 训练网络
                if (replayBuffer.size() >= batchSize) {
                    trainBatch();
                }

                // 更新目标网络
                totalSteps++;
                if (totalSteps % targetUpdateFreq == 0) {
                    targetNetwork.copyWeightsFrom(qNetwork);
                    log.debug("目标网络更新 - 步数: {}", totalSteps);
                }

                // 衰减ε
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

    /**
     * 选择动作（ε-greedy策略）
     *
     * 基于当前状态选择动作，以ε的概率随机探索，以1-ε的概率选择最优动作。
     *
     * @param state 当前状态
     * @return 选择的动作索引（0-3）
     */
    public int selectAction(State state) {
        initializeNetworks();

        if (random.nextDouble() < epsilon) {
            // 探索：随机选择动作
            return random.nextInt(OUTPUT_DIM);
        } else {
            // 利用：选择Q值最大的动作
            double[] qValues = qNetwork.forward(state.toArray());
            return argmax(qValues);
        }
    }

    /**
     * 选择最优动作（纯利用，无探索）
     *
     * 用于实际部署时选择最优控制动作。
     *
     * @param state 当前状态
     * @return 最优动作索引（0-3）
     */
    public int selectBestAction(State state) {
        initializeNetworks();
        double[] qValues = qNetwork.forward(state.toArray());
        return argmax(qValues);
    }

    /**
     * 获取所有动作的Q值
     *
     * @param state 当前状态
     * @return 所有动作的Q值数组
     */
    public double[] getQValues(State state) {
        initializeNetworks();
        return qNetwork.forward(state.toArray());
    }

    /**
     * 执行一步环境交互
     *
     * 根据当前动作更新环境状态，并返回奖励和下一状态。
     *
     * @param action 执行的动作索引
     * @return 一步交互结果，包含下一状态、奖励和终止标志
     */
    public StepResult step(int action) {
        initializeNetworks();

        // 解析动作
        boolean dehumidifierOn = (action == 1 || action == 3);
        boolean humidifierOn = (action == 2 || action == 3);

        // 计算当前外界环境RH（昼夜节律）
        double ambientRh = calculateAmbientRh(currentTime);

        // 更新RH值
        double currentRh = currentState.getCurrentRh();
        double newRh = currentRh;

        // 除湿机效果
        if (dehumidifierOn) {
            newRh -= dehumidifierEffect * TIME_STEP;
        }

        // 加湿器效果
        if (humidifierOn) {
            newRh += humidifierEffect * TIME_STEP;
        }

        // 自然渗漏
        newRh += (ambientRh - currentRh) * leakageRate * TIME_STEP;

        // 限制在[0, 1]范围内
        newRh = Math.max(0.0, Math.min(1.0, newRh));

        // 更新时间
        currentTime += TIME_STEP;
        int hourOfDay = ((int) currentTime) % 24;

        // 更新RH历史并计算趋势
        rhHistory.add(newRh);
        if (rhHistory.size() > 12) { // 保留最近12步用于趋势计算
            rhHistory.remove(0);
        }

        double rhTrend = calculateRhTrend();

        // 计算奖励
        double reward = calculateReward(newRh, action, dehumidifierOn, humidifierOn);

        // 更新连续安全计数
        if (isInSafeZone(newRh)) {
            consecutiveSafeHours++;
        } else {
            consecutiveSafeHours = 0;
        }

        // 目标达成奖励
        boolean done = false;
        if (consecutiveSafeHours >= GOAL_HOURS) {
            reward += rewardGoal;
            done = true;
            log.debug("目标达成！连续{}小时避开潮解带", GOAL_HOURS);
        }

        // 创建下一状态
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

    /**
     * 计算外界环境RH值（昼夜节律）
     *
     * 基于正弦函数模拟一天中RH的变化规律。
     *
     * @param time 当前时间（小时）
     * @return 外界环境RH值（归一化到[0,1]）
     */
    private double calculateAmbientRh(double time) {
        double phase = 2 * Math.PI * time / 24.0;
        double rh = baseRh + rhAmplitude * Math.sin(phase - Math.PI / 2);
        return Math.max(0.0, Math.min(1.0, rh));
    }

    /**
     * 计算RH变化趋势
     *
     * 基于最近几小时的RH数据计算变化趋势，归一化到[-1, 1]。
     *
     * @return RH变化趋势（归一化）
     */
    private double calculateRhTrend() {
        if (rhHistory.size() < 2) {
            return 0.0;
        }

        // 使用线性回归斜率作为趋势
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

        // 归一化到[-1, 1]（假设最大变化率为10%/小时）
        double maxTrend = 0.1;
        return Math.max(-1.0, Math.min(1.0, slope / maxTrend));
    }

    /**
     * 计算奖励值
     *
     * 根据当前RH值和动作计算综合奖励，包括：
     * - 基础奖励/惩罚（基于RH区间）
     * - 能耗惩罚
     * - 频繁切换惩罚
     *
     * @param rh 当前RH值
     * @param action 当前动作
     * @param dehumidifierOn 除湿机是否开启
     * @param humidifierOn 加湿器是否开启
     * @return 奖励值
     */
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

    /**
     * 判断RH是否在安全区域
     *
     * 安全区域定义为[40%, 65%]或[80%, 90%]。
     *
     * @param rh RH值（归一化到[0,1]）
     * @return 是否在安全区域
     */
    private boolean isInSafeZone(double rh) {
        return (rh >= 0.4 && rh <= 0.65) || (rh >= 0.80 && rh <= 0.90);
    }

    /**
     * 从经验回放池中采样并训练一批数据
     *
     * 使用随机梯度下降更新Q网络，目标Q值由目标网络计算。
     */
    private void trainBatch() {
        // 采样一批经验
        List<Transition> batch = replayBuffer.sample(batchSize, random);

        // 计算目标Q值
        for (Transition transition : batch) {
            double[] stateArray = transition.getState().toArray();
            double[] nextStateArray = transition.getNextState().toArray();

            // 当前Q值
            double[] currentQ = qNetwork.forward(stateArray);

            // 目标Q值
            double[] nextQ = targetNetwork.forward(nextStateArray);
            double maxNextQ = max(nextQ);

            double target = transition.getReward();
            if (!transition.isDone()) {
                target += gamma * maxNextQ;
            }

            // 更新Q值
            double[] targetQ = currentQ.clone();
            targetQ[transition.getAction()] = target;

            // 反向传播更新权重
            qNetwork.backward(stateArray, currentQ, targetQ, learningRate);
        }
    }

    /**
     * 求数组最大值索引
     *
     * @param array 输入数组
     * @return 最大值的索引
     */
    private int argmax(double[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * 求数组最大值
     *
     * @param array 输入数组
     * @return 最大值
     */
    private double max(double[] array) {
        double maxValue = array[0];
        for (double v : array) {
            if (v > maxValue) {
                maxValue = v;
            }
        }
        return maxValue;
    }

    /**
     * 保存模型到文件
     *
     * 将Q网络的权重保存到指定路径，便于后续加载使用。
     *
     * @param filePath 保存文件路径
     * @throws IOException 文件写入失败时抛出
     */
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

    /**
     * 从文件加载模型
     *
     * 从指定路径加载Q网络权重，并同步到目标网络。
     *
     * @param filePath 模型文件路径
     * @throws IOException 文件读取失败时抛出
     * @throws ClassNotFoundException 类版本不匹配时抛出
     */
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

    // ==================== 内部类 ====================

    /**
     * 状态表示类
     *
     * 包含5维状态向量：
     * - currentRh: 当前RH值（归一化到[0,1]）
     * - rhTrend: 过去1小时RH变化趋势（归一化到[-1,1]）
     * - hourOfDay: 当前小时（归一化到[0,1]，模拟昼夜节律）
     * - dehumidifierOn: 除湿机状态（0=关, 1=开）
     * - humidifierOn: 加湿器状态（0=关, 1=开）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class State {
        /**
         * 当前RH值（归一化到[0,1]，0=0%, 1=100%）
         */
        private double currentRh;

        /**
         * 过去1小时RH变化趋势（归一化到[-1,1]）
         */
        private double rhTrend;

        /**
         * 当前小时（归一化到[0,1]，0=0点, 1=23点）
         */
        private double hourOfDay;

        /**
         * 除湿机状态（0=关, 1=开）
         */
        private int dehumidifierOn;

        /**
         * 加湿器状态（0=关, 1=开）
         */
        private int humidifierOn;

        /**
         * 将状态转换为数组形式
         *
         * @return 状态数组（5维）
         */
        public double[] toArray() {
            return new double[]{
                    currentRh,
                    rhTrend,
                    hourOfDay,
                    dehumidifierOn,
                    humidifierOn
            };
        }

        /**
         * 从数组创建状态
         *
         * @param array 状态数组（5维）
         * @return State对象
         * @throws IllegalArgumentException 数组长度不为5时抛出
         */
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
    }

    /**
     * 转移样本类
     *
     * 表示经验回放中的一条样本，包含状态、动作、奖励、下一状态和终止标志。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transition {
        /**
         * 当前状态
         */
        private State state;

        /**
         * 执行的动作
         */
        private int action;

        /**
         * 获得的奖励
         */
        private double reward;

        /**
         * 下一状态
         */
        private State nextState;

        /**
         * 是否终止
         */
        private boolean done;
    }

    /**
     * 一步执行结果
     *
     * 包含环境step方法返回的所有信息。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResult {
        /**
         * 下一状态
         */
        private State nextState;

        /**
         * 获得的奖励
         */
        private double reward;

        /**
         * 是否终止
         */
        private boolean done;
    }

    /**
     * DQN神经网络类
     *
     * 三层全连接神经网络：输入层→隐藏层1→隐藏层2→输出层
     * 使用ReLU激活函数，MSE损失函数。
     */
    public static class DQNNetwork {
        /**
         * 输入层维度
         */
        private int inputDim;

        /**
         * 第一隐藏层维度
         */
        private int hidden1Dim;

        /**
         * 第二隐藏层维度
         */
        private int hidden2Dim;

        /**
         * 输出层维度
         */
        private int outputDim;

        /**
         * 第一层权重矩阵 [hidden1Dim][inputDim]
         */
        private double[][] weights1;

        /**
         * 第一层偏置向量 [hidden1Dim]
         */
        private double[] biases1;

        /**
         * 第二层权重矩阵 [hidden2Dim][hidden1Dim]
         */
        private double[][] weights2;

        /**
         * 第二层偏置向量 [hidden2Dim]
         */
        private double[] biases2;

        /**
         * 第三层权重矩阵 [outputDim][hidden2Dim]
         */
        private double[][] weights3;

        /**
         * 第三层偏置向量 [outputDim]
         */
        private double[] biases3;

        /**
         * 构造神经网络
         *
         * @param inputDim 输入层维度
         * @param hidden1Dim 第一隐藏层维度
         * @param hidden2Dim 第二隐藏层维度
         * @param outputDim 输出层维度
         * @param random 随机数生成器
         */
        public DQNNetwork(int inputDim, int hidden1Dim, int hidden2Dim, int outputDim, Random random) {
            this.inputDim = inputDim;
            this.hidden1Dim = hidden1Dim;
            this.hidden2Dim = hidden2Dim;
            this.outputDim = outputDim;

            // Xavier初始化
            this.weights1 = initializeWeights(inputDim, hidden1Dim, random);
            this.biases1 = new double[hidden1Dim];
            this.weights2 = initializeWeights(hidden1Dim, hidden2Dim, random);
            this.biases2 = new double[hidden2Dim];
            this.weights3 = initializeWeights(hidden2Dim, outputDim, random);
            this.biases3 = new double[outputDim];
        }

        /**
         * 使用Xavier方法初始化权重
         *
         * @param fanIn 输入神经元数
         * @param fanOut 输出神经元数
         * @param random 随机数生成器
         * @return 初始化的权重矩阵
         */
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

        /**
         * 前向传播
         *
         * 计算给定输入的网络输出（Q值）。
         *
         * @param input 输入向量
         * @return 输出向量（Q值）
         */
        public double[] forward(double[] input) {
            // 第一层：输入→隐藏层1，ReLU激活
            double[] hidden1 = new double[hidden1Dim];
            for (int i = 0; i < hidden1Dim; i++) {
                double sum = biases1[i];
                for (int j = 0; j < inputDim; j++) {
                    sum += weights1[i][j] * input[j];
                }
                hidden1[i] = relu(sum);
            }

            // 第二层：隐藏层1→隐藏层2，ReLU激活
            double[] hidden2 = new double[hidden2Dim];
            for (int i = 0; i < hidden2Dim; i++) {
                double sum = biases2[i];
                for (int j = 0; j < hidden1Dim; j++) {
                    sum += weights2[i][j] * hidden1[j];
                }
                hidden2[i] = relu(sum);
            }

            // 第三层：隐藏层2→输出，无激活
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

        /**
         * 反向传播更新权重
         *
         * 使用MSE损失和梯度下降更新网络参数。
         *
         * @param input 输入向量
         * @param currentOutput 当前输出
         * @param targetOutput 目标输出
         * @param learningRate 学习率
         */
        public void backward(double[] input, double[] currentOutput, double[] targetOutput, double learningRate) {
            // 前向传播，保存中间结果
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

            // 输出层误差（MSE损失导数）
            double[] delta3 = new double[outputDim];
            for (int i = 0; i < outputDim; i++) {
                delta3[i] = (currentOutput[i] - targetOutput[i]);
            }

            // 第二层误差
            double[] delta2 = new double[hidden2Dim];
            for (int i = 0; i < hidden2Dim; i++) {
                double sum = 0;
                for (int j = 0; j < outputDim; j++) {
                    sum += weights3[j][i] * delta3[j];
                }
                delta2[i] = sum * reluDerivative(z2[i]);
            }

            // 第一层误差
            double[] delta1 = new double[hidden1Dim];
            for (int i = 0; i < hidden1Dim; i++) {
                double sum = 0;
                for (int j = 0; j < hidden2Dim; j++) {
                    sum += weights2[j][i] * delta2[j];
                }
                delta1[i] = sum * reluDerivative(z1[i]);
            }

            // 更新第三层权重和偏置
            for (int i = 0; i < outputDim; i++) {
                biases3[i] -= learningRate * delta3[i];
                for (int j = 0; j < hidden2Dim; j++) {
                    weights3[i][j] -= learningRate * delta3[i] * hidden2[j];
                }
            }

            // 更新第二层权重和偏置
            for (int i = 0; i < hidden2Dim; i++) {
                biases2[i] -= learningRate * delta2[i];
                for (int j = 0; j < hidden1Dim; j++) {
                    weights2[i][j] -= learningRate * delta2[i] * hidden1[j];
                }
            }

            // 更新第一层权重和偏置
            for (int i = 0; i < hidden1Dim; i++) {
                biases1[i] -= learningRate * delta1[i];
                for (int j = 0; j < inputDim; j++) {
                    weights1[i][j] -= learningRate * delta1[i] * input[j];
                }
            }
        }

        /**
         * 从另一个网络复制权重
         *
         * 用于目标网络定期同步主网络权重。
         *
         * @param other 源网络
         */
        public void copyWeightsFrom(DQNNetwork other) {
            // 复制第一层
            for (int i = 0; i < hidden1Dim; i++) {
                biases1[i] = other.biases1[i];
                System.arraycopy(other.weights1[i], 0, weights1[i], 0, inputDim);
            }

            // 复制第二层
            for (int i = 0; i < hidden2Dim; i++) {
                biases2[i] = other.biases2[i];
                System.arraycopy(other.weights2[i], 0, weights2[i], 0, hidden1Dim);
            }

            // 复制第三层
            for (int i = 0; i < outputDim; i++) {
                biases3[i] = other.biases3[i];
                System.arraycopy(other.weights3[i], 0, weights3[i], 0, hidden2Dim);
            }
        }

        /**
         * 设置所有层的权重和偏置
         *
         * @param w1 第一层权重
         * @param b1 第一层偏置
         * @param w2 第二层权重
         * @param b2 第二层偏置
         * @param w3 第三层权重
         * @param b3 第三层偏置
         */
        public void setWeights(double[][] w1, double[] b1, double[][] w2, double[] b2, double[][] w3, double[] b3) {
            this.weights1 = w1;
            this.biases1 = b1;
            this.weights2 = w2;
            this.biases2 = b2;
            this.weights3 = w3;
            this.biases3 = b3;
        }

        /**
         * 获取第一层权重
         *
         * @return 权重矩阵
         */
        public double[][] getWeights1() {
            return weights1;
        }

        /**
         * 获取第一层偏置
         *
         * @return 偏置向量
         */
        public double[] getBiases1() {
            return biases1;
        }

        /**
         * 获取第二层权重
         *
         * @return 权重矩阵
         */
        public double[][] getWeights2() {
            return weights2;
        }

        /**
         * 获取第二层偏置
         *
         * @return 偏置向量
         */
        public double[] getBiases2() {
            return biases2;
        }

        /**
         * 获取第三层权重
         *
         * @return 权重矩阵
         */
        public double[][] getWeights3() {
            return weights3;
        }

        /**
         * 获取第三层偏置
         *
         * @return 偏置向量
         */
        public double[] getBiases3() {
            return biases3;
        }

        /**
         * ReLU激活函数
         *
         * @param x 输入值
         * @return 激活值
         */
        private double relu(double x) {
            return Math.max(0, x);
        }

        /**
         * ReLU导数
         *
         * @param x 输入值
         * @return 导数值
         */
        private double reluDerivative(double x) {
            return x > 0 ? 1.0 : 0.0;
        }
    }

    /**
     * 经验回放池
     *
     * 使用循环队列实现固定容量的经验回放缓冲区，支持随机采样。
     */
    public static class ReplayBuffer {
        /**
         * 缓冲区容量
         */
        private int capacity;

        /**
         * 存储的转移样本
         */
        private List<Transition> buffer;

        /**
         * 当前写入位置
         */
        private int position;

        /**
         * 构造经验回放池
         *
         * @param capacity 缓冲区容量
         */
        public ReplayBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new ArrayList<>(capacity);
            this.position = 0;
        }

        /**
         * 添加一条转移样本
         *
         * 当缓冲区满时，覆盖最旧的样本。
         *
         * @param transition 转移样本
         */
        public void add(Transition transition) {
            if (buffer.size() < capacity) {
                buffer.add(transition);
            } else {
                buffer.set(position, transition);
            }
            position = (position + 1) % capacity;
        }

        /**
         * 随机采样一批样本
         *
         * @param batchSize 批大小
         * @param random 随机数生成器
         * @return 采样的样本列表
         */
        public List<Transition> sample(int batchSize, Random random) {
            List<Transition> batch = new ArrayList<>(batchSize);
            int size = buffer.size();

            for (int i = 0; i < batchSize; i++) {
                int index = random.nextInt(size);
                batch.add(buffer.get(index));
            }

            return batch;
        }

        /**
         * 获取缓冲区当前大小
         *
         * @return 样本数量
         */
        public int size() {
            return buffer.size();
        }

        /**
         * 获取缓冲区容量
         *
         * @return 容量
         */
        public int getCapacity() {
            return capacity;
        }

        /**
         * 清空缓冲区
         */
        public void clear() {
            buffer.clear();
            position = 0;
        }
    }

    // ==================== Getter方法 ====================

    /**
     * 获取当前探索率ε
     *
     * @return 当前ε值
     */
    public double getEpsilon() {
        return epsilon;
    }

    /**
     * 获取当前状态
     *
     * @return 当前状态
     */
    public State getCurrentState() {
        return currentState;
    }

    /**
     * 获取当前模拟时间
     *
     * @return 当前时间（小时）
     */
    public double getCurrentTime() {
        return currentTime;
    }

    /**
     * 获取连续安全小时数
     *
     * @return 连续安全小时数
     */
    public int getConsecutiveSafeHours() {
        return consecutiveSafeHours;
    }
}
