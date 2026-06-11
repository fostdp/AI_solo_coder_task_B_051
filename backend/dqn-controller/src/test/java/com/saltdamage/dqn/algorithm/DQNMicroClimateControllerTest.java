package com.saltdamage.dqn.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("DQN微环境调控算法单元测试")
class DQNMicroClimateControllerTest {

    private DQNMicroClimateController controller;

    @BeforeEach
    @DisplayName("初始化测试控制器（轻量参数加速测试）")
    void setUp() throws Exception {
        controller = new DQNMicroClimateController();

        setPrivateField(controller, "hidden1Dim", 16);
        setPrivateField(controller, "hidden2Dim", 8);
        setPrivateField(controller, "replayBufferSize", 500);
        setPrivateField(controller, "batchSize", 16);
        setPrivateField(controller, "targetUpdateFreq", 50);
        setPrivateField(controller, "learningRate", 0.01);
        setPrivateField(controller, "epsilonStart", 1.0);
        setPrivateField(controller, "epsilonEnd", 0.05);
        setPrivateField(controller, "epsilonDecay", 0.995);

        controller.resetEnvironment(0.55, 12);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private double invokePrivateCalculateReward(double rh, int action,
                                                boolean dehumidifierOn,
                                                boolean humidifierOn) throws Exception {
        setPrivateField(controller, "lastAction", 0);
        Method method = DQNMicroClimateController.class.getDeclaredMethod(
                "calculateReward", double.class, int.class, boolean.class, boolean.class);
        method.setAccessible(true);
        return (double) method.invoke(controller, rh, action, dehumidifierOn, humidifierOn);
    }

    private boolean invokePrivateIsInSafeZone(double rh) throws Exception {
        Method method = DQNMicroClimateController.class.getDeclaredMethod("isInSafeZone", double.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, rh);
    }

    private double invokePrivateCalculateAmbientRh(double time) throws Exception {
        Method method = DQNMicroClimateController.class.getDeclaredMethod("calculateAmbientRh", double.class);
        method.setAccessible(true);
        return (double) method.invoke(controller, time);
    }

    @Nested
    @DisplayName("一、状态空间测试")
    class StateSpaceTests {

        @Test
        @DisplayName("1. 创建合法状态，各字段正确赋值")
        void testStateCreation_ValidValues() {
            DQNMicroClimateController.State state = DQNMicroClimateController.State.builder()
                    .currentRh(0.55)
                    .rhTrend(0.01)
                    .hourOfDay(12.0 / 23.0)
                    .dehumidifierOn(0)
                    .humidifierOn(1)
                    .build();

            assertEquals(0.55, state.getCurrentRh(), 1e-9, "currentRh应正确赋值");
            assertEquals(0.01, state.getRhTrend(), 1e-9, "rhTrend应正确赋值");
            assertEquals(12.0 / 23.0, state.getHourOfDay(), 1e-9, "hourOfDay应正确赋值");
            assertEquals(0, state.getDehumidifierOn(), "dehumidifierOn应正确赋值");
            assertEquals(1, state.getHumidifierOn(), "humidifierOn应正确赋值");

            double[] arr = state.toArray();
            assertEquals(5, arr.length, "toArray应返回5维数组");
            assertEquals(0.55, arr[0], 1e-9);
            assertEquals(0.01, arr[1], 1e-9);
        }

        @Test
        @DisplayName("2. RH值步进出范围时被裁剪到[0,1]（边界验证）")
        void testStateNormalization_RhInRange() {
            controller.resetEnvironment(0.99, 12);
            for (int i = 0; i < 50; i++) {
                DQNMicroClimateController.StepResult result = controller.step(2);
                assertTrue(result.getNextState().getCurrentRh() >= 0.0,
                        "RH不应小于0，当前值=" + result.getNextState().getCurrentRh());
                assertTrue(result.getNextState().getCurrentRh() <= 1.0,
                        "RH不应大于1，当前值=" + result.getNextState().getCurrentRh());
            }

            controller.resetEnvironment(0.01, 12);
            for (int i = 0; i < 50; i++) {
                DQNMicroClimateController.StepResult result = controller.step(1);
                assertTrue(result.getNextState().getCurrentRh() >= 0.0,
                        "RH不应小于0，当前值=" + result.getNextState().getCurrentRh());
                assertTrue(result.getNextState().getCurrentRh() <= 1.0,
                        "RH不应大于1，当前值=" + result.getNextState().getCurrentRh());
            }
        }

        @Test
        @DisplayName("3. 小时23→0的循环处理")
        void testStateHourNormalization_24HourWrap() {
            controller.resetEnvironment(0.5, 22);
            controller.step(0);
            assertEquals(0, (int) (controller.getCurrentState().getHourOfDay() * 23 + 0.5),
                    "23点后下一小时应为0点");

            controller.resetEnvironment(0.5, 20);
            for (int i = 0; i < 10; i++) {
                controller.step(0);
            }
            double hourNorm = controller.getCurrentState().getHourOfDay();
            assertTrue(hourNorm >= 0.0 && hourNorm <= 1.0,
                    "归一化小时应始终在[0,1]范围内，当前=" + hourNorm);
        }
    }

    @Nested
    @DisplayName("二、动作空间测试")
    class ActionSpaceTests {

        @Test
        @DisplayName("4. 动作空间维度为4")
        void testActionCount_FourActions() throws Exception {
            DQNMicroClimateController.DQNNetwork net = (DQNMicroClimateController.DQNNetwork)
                    getPrivateField(controller, "qNetwork");
            double[] output = net.forward(new double[]{0.5, 0.0, 0.5, 0, 0});
            assertEquals(4, output.length, "输出层维度应为4（动作数）");
        }

        @Test
        @DisplayName("5. 传入非法动作编号的处理（边界/异常）")
        void testInvalidAction_HandledGracefully() {
            controller.resetEnvironment(0.5, 12);

            DQNMicroClimateController.StepResult r1 = controller.step(-1);
            assertNotNull(r1, "动作-1不应抛异常，应返回结果");
            assertFalse(r1.getNextState().getDehumidifierOn() == 1
                            && r1.getNextState().getHumidifierOn() == 0,
                    "非法动作-1不应映射到除湿");

            DQNMicroClimateController.StepResult r2 = controller.step(4);
            assertNotNull(r2, "动作4不应抛异常，应返回结果");

            double rh = controller.getCurrentState().getCurrentRh();
            assertTrue(rh >= 0.0 && rh <= 1.0, "非法动作后RH仍应在合理范围");
        }

        @Test
        @DisplayName("6. 各动作对应的能耗惩罚计算正确")
        void testActionEnergyConsumption_CorrectValues() throws Exception {
            setPrivateField(controller, "penaltyDehumidifier", -0.5);
            setPrivateField(controller, "penaltyHumidifier", -0.5);
            setPrivateField(controller, "penaltyBoth", -1.5);
            setPrivateField(controller, "rewardSafe", 1.0);
            setPrivateField(controller, "penaltySwitch", 0.0);

            double rIdle = invokePrivateCalculateReward(0.5, 0, false, false);
            assertEquals(1.0, rIdle, 0.01, "待机(动作0)能耗应为0，奖励=安全奖励");

            double rDehum = invokePrivateCalculateReward(0.5, 1, true, false);
            assertEquals(1.0 - 0.5, rDehum, 0.01, "除湿(动作1)能耗惩罚-0.5");

            double rHumid = invokePrivateCalculateReward(0.5, 2, false, true);
            assertEquals(1.0 - 0.5, rHumid, 0.01, "加湿(动作2)能耗惩罚-0.5");

            double rBoth = invokePrivateCalculateReward(0.5, 3, true, true);
            assertEquals(1.0 - 1.5, rBoth, 0.01, "同时开(动作3)能耗惩罚-1.5");
        }
    }

    @Nested
    @DisplayName("三、环境模拟测试")
    class EnvironmentSimulationTests {

        @BeforeEach
        void setUpEnv() throws Exception {
            setPrivateField(controller, "dehumidifierEffect", 0.05);
            setPrivateField(controller, "humidifierEffect", 0.05);
            setPrivateField(controller, "leakageRate", 0.0);
        }

        @Test
        @DisplayName("7. 执行除湿动作后RH应下降")
        void testStep_DehumidifierReducesRh() {
            controller.resetEnvironment(0.70, 12);
            double before = controller.getCurrentState().getCurrentRh();

            double sumChange = 0;
            for (int i = 0; i < 5; i++) {
                DQNMicroClimateController.StepResult r = controller.step(1);
                sumChange += (r.getNextState().getCurrentRh() - before);
                before = r.getNextState().getCurrentRh();
            }
            assertTrue(sumChange < 0, "连续除湿5步RH应总体下降，变化量=" + sumChange);
        }

        @Test
        @DisplayName("8. 执行加湿动作后RH应上升")
        void testStep_HumidifierIncreasesRh() {
            controller.resetEnvironment(0.30, 12);
            double before = controller.getCurrentState().getCurrentRh();

            double sumChange = 0;
            for (int i = 0; i < 5; i++) {
                DQNMicroClimateController.StepResult r = controller.step(2);
                sumChange += (r.getNextState().getCurrentRh() - before);
                before = r.getNextState().getCurrentRh();
            }
            assertTrue(sumChange > 0, "连续加湿5步RH应总体上升，变化量=" + sumChange);
        }

        @Test
        @DisplayName("9. 待机动作RH变化小（自然渗漏）")
        void testStep_Idle_NoImmediateChange() throws Exception {
            setPrivateField(controller, "leakageRate", 0.0);
            controller.resetEnvironment(0.55, 12);
            double before = controller.getCurrentState().getCurrentRh();
            double ambient = invokePrivateCalculateAmbientRh(12);

            double totalChange = 0;
            for (int i = 0; i < 5; i++) {
                DQNMicroClimateController.StepResult r = controller.step(0);
                double diff = Math.abs(r.getNextState().getCurrentRh() - before);
                totalChange += diff;
                before = r.getNextState().getCurrentRh();
            }
            assertTrue(totalChange < 0.01,
                    "渗漏率为0时待机RH变化应极小，总变化=" + totalChange
                            + "，ambient=" + ambient);
        }

        @Test
        @DisplayName("10. 多次步进后RH应始终保持在[0,1]范围（边界验证）")
        void testRhRange_BoundedBetween0And100() {
            double[] initialRhs = {0.0, 0.05, 0.5, 0.95, 1.0};
            int[] actions = {0, 1, 2, 3};

            for (double initRh : initialRhs) {
                for (int action : actions) {
                    controller.resetEnvironment(initRh, 12);
                    for (int step = 0; step < 200; step++) {
                        DQNMicroClimateController.StepResult r = controller.step(action);
                        double rh = r.getNextState().getCurrentRh();
                        assertTrue(rh >= 0.0,
                                String.format("RH<0: initRh=%.2f, action=%d, step=%d, rh=%.4f",
                                        initRh, action, step, rh));
                        assertTrue(rh <= 1.0,
                                String.format("RH>1: initRh=%.2f, action=%d, step=%d, rh=%.4f",
                                        initRh, action, step, rh));
                    }
                }
            }
            log.info("RH边界验证通过：5个初始RH × 4个动作 × 200步全部在[0,1]内");
        }
    }

    @Nested
    @DisplayName("四、奖励函数测试")
    class RewardFunctionTests {

        @BeforeEach
        void setUpReward() throws Exception {
            setPrivateField(controller, "rewardSafe", 1.0);
            setPrivateField(controller, "penaltyDeliquescence", -5.0);
            setPrivateField(controller, "penaltyExtreme", -10.0);
            setPrivateField(controller, "penaltySwitch", 0.0);
        }

        @Test
        @DisplayName("11. RH在安全区[40-65%或80-90%]获得正奖励")
        void testReward_SafeZone_PositiveReward() throws Exception {
            double[] safeRhs = {0.40, 0.50, 0.60, 0.65, 0.80, 0.85, 0.90};
            for (double rh : safeRhs) {
                double reward = invokePrivateCalculateReward(rh, 0, false, false);
                assertTrue(reward > 0,
                        String.format("RH=%.0f%%应获得正奖励，实际=%.2f", rh * 100, reward));
                assertTrue(invokePrivateIsInSafeZone(rh),
                        "RH=" + (rh * 100) + "%应判定为安全区");
            }
        }

        @Test
        @DisplayName("12. RH在潮解带[65-80%]获得负奖励")
        void testReward_DeliquescenceZone_NegativeReward() throws Exception {
            double[] deliqRhs = {0.66, 0.70, 0.75, 0.79};
            for (double rh : deliqRhs) {
                double reward = invokePrivateCalculateReward(rh, 0, false, false);
                assertTrue(reward < 0,
                        String.format("RH=%.0f%%潮解带应获得负奖励，实际=%.2f", rh * 100, reward));
                assertFalse(invokePrivateIsInSafeZone(rh),
                        "RH=" + (rh * 100) + "%不应判定为安全区");
            }
        }

        @Test
        @DisplayName("13. RH<40%或>90%强负奖励")
        void testReward_ExtremeZone_StrongNegative() throws Exception {
            double[] extremeRhs = {0.0, 0.20, 0.39, 0.91, 0.95, 1.0};
            for (double rh : extremeRhs) {
                double reward = invokePrivateCalculateReward(rh, 0, false, false);
                assertTrue(reward <= -9,
                        String.format("RH=%.0f%%极端区应获得强负奖励(≈-10)，实际=%.2f",
                                rh * 100, reward));
            }
        }

        @Test
        @DisplayName("14. 能耗惩罚正确扣除")
        void testReward_EnergyConsumptionPenaltyApplied() throws Exception {
            setPrivateField(controller, "penaltyDehumidifier", -0.5);
            setPrivateField(controller, "penaltyHumidifier", -0.5);
            setPrivateField(controller, "penaltyBoth", -1.5);

            double baseSafe = invokePrivateCalculateReward(0.5, 0, false, false);

            double rDehum = invokePrivateCalculateReward(0.5, 1, true, false);
            assertEquals(baseSafe - 0.5, rDehum, 0.01,
                    "除湿应额外扣除0.5能耗惩罚");

            double rHumid = invokePrivateCalculateReward(0.5, 2, false, true);
            assertEquals(baseSafe - 0.5, rHumid, 0.01,
                    "加湿应额外扣除0.5能耗惩罚");

            double rBoth = invokePrivateCalculateReward(0.5, 3, true, true);
            assertEquals(baseSafe - 1.5, rBoth, 0.01,
                    "同时开应额外扣除1.5能耗惩罚");
        }
    }

    @Nested
    @DisplayName("五、神经网络测试")
    class NetworkTests {

        @Test
        @DisplayName("15. 前向传播输出维度=4（动作数）")
        void testNetworkForward_OutputDimensionCorrect() throws Exception {
            DQNMicroClimateController.DQNNetwork net = (DQNMicroClimateController.DQNNetwork)
                    getPrivateField(controller, "qNetwork");

            double[][] testInputs = {
                    {0.5, 0.0, 0.5, 0, 0},
                    {0.0, -1.0, 0.0, 1, 0},
                    {1.0, 1.0, 1.0, 0, 1},
                    {0.7, 0.1, 0.9, 1, 1}
            };

            for (double[] input : testInputs) {
                double[] output = net.forward(input);
                assertEquals(4, output.length, "输出维度必须为4");
            }
        }

        @Test
        @DisplayName("16. 反向传播几步后损失应下降（梯度下降验证）")
        void testNetworkBackward_LossDecreases() throws Exception {
            DQNMicroClimateController.DQNNetwork net = new DQNMicroClimateController.DQNNetwork(
                    5, 16, 8, 4, new Random(123));

            double[] input = {0.5, 0.0, 0.5, 0, 0};
            double[] target = {1.0, 2.0, 3.0, 4.0};
            double lr = 0.05;

            List<Double> losses = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                double[] current = net.forward(input);
                double loss = mse(current, target);
                losses.add(loss);
                net.backward(input, current, target, lr);
            }

            double firstLoss = losses.get(0);
            double lastLoss = losses.get(losses.size() - 1);
            log.info("损失下降验证: 初始Loss={:.4f}, 最终Loss={:.4f}", firstLoss, lastLoss);
            assertTrue(lastLoss < firstLoss * 0.8,
                    "反向传播50步后损失应显著下降，初始=" + firstLoss
                            + "，最终=" + lastLoss);
        }

        @Test
        @DisplayName("17. 调用copyWeightsFrom后目标网络权重与主网络一致")
        void testWeightSync_TargetNetworkMatches() throws Exception {
            DQNMicroClimateController.DQNNetwork net1 = new DQNMicroClimateController.DQNNetwork(
                    5, 16, 8, 4, new Random(999));
            DQNMicroClimateController.DQNNetwork net2 = new DQNMicroClimateController.DQNNetwork(
                    5, 16, 8, 4, new Random(777));

            double[] testInput = {0.3, 0.1, 0.7, 1, 0};
            double[] outBefore = net2.forward(testInput);

            net2.copyWeightsFrom(net1);
            double[] outAfter = net2.forward(testInput);
            double[] outSource = net1.forward(testInput);

            assertNotEquals(Arrays.toString(outBefore), Arrays.toString(outAfter),
                    "复制前两个网络输出应不同");
            assertArrayEquals(outSource, outAfter, 1e-9,
                    "复制后目标网络输出应与源网络完全一致");

            assertArrayEquals(net1.getWeights1(), net2.getWeights1(),
                    "第一层权重应完全一致");
            assertArrayEquals(net1.getBiases3(), net2.getBiases3(), 1e-9,
                    "第三层偏置应完全一致");
        }

        private double mse(double[] a, double[] b) {
            double sum = 0;
            for (int i = 0; i < a.length; i++) {
                sum += (a[i] - b[i]) * (a[i] - b[i]);
            }
            return sum / a.length;
        }
    }

    @Nested
    @DisplayName("六、经验回放测试")
    class ReplayBufferTests {

        @Test
        @DisplayName("18. 添加经验后能随机采样，样本维度正确")
        void testReplayBuffer_AddAndSample() {
            DQNMicroClimateController.ReplayBuffer buffer =
                    new DQNMicroClimateController.ReplayBuffer(100);
            Random rand = new Random(42);

            for (int i = 0; i < 30; i++) {
                DQNMicroClimateController.Transition t = DQNMicroClimateController.Transition.builder()
                        .state(DQNMicroClimateController.State.builder()
                                .currentRh(0.4 + i * 0.01)
                                .rhTrend(0.0)
                                .hourOfDay(0.5)
                                .dehumidifierOn(0)
                                .humidifierOn(0)
                                .build())
                        .action(i % 4)
                        .reward(i * 0.1)
                        .nextState(DQNMicroClimateController.State.builder()
                                .currentRh(0.41 + i * 0.01)
                                .rhTrend(0.01)
                                .hourOfDay(0.52)
                                .dehumidifierOn(1)
                                .humidifierOn(0)
                                .build())
                        .done(i == 29)
                        .build();
                buffer.add(t);
            }

            assertEquals(30, buffer.size(), "缓冲区大小应为30");

            List<DQNMicroClimateController.Transition> batch = buffer.sample(10, rand);
            assertEquals(10, batch.size(), "采样大小应为10");

            Set<Integer> actionsInBatch = new HashSet<>();
            for (DQNMicroClimateController.Transition t : batch) {
                assertEquals(5, t.getState().toArray().length, "状态应为5维");
                assertEquals(5, t.getNextState().toArray().length, "下一状态应为5维");
                assertTrue(t.getAction() >= 0 && t.getAction() < 4,
                        "动作索引应在[0,3]内");
                actionsInBatch.add(t.getAction());
            }
            assertTrue(actionsInBatch.size() >= 2,
                    "随机采样应包含多样性动作，实际动作种类=" + actionsInBatch.size());
        }

        @Test
        @DisplayName("19. 缓冲区满后新样本覆盖最旧样本（环形队列验证）")
        void testReplayBuffer_FullCapacity_CircularOverride() {
            int capacity = 10;
            DQNMicroClimateController.ReplayBuffer buffer =
                    new DQNMicroClimateController.ReplayBuffer(capacity);

            for (int i = 0; i < capacity; i++) {
                buffer.add(createTransitionWithReward(i));
            }
            assertEquals(capacity, buffer.size(), "填充后容量应为10");

            for (int i = capacity; i < capacity + 5; i++) {
                buffer.add(createTransitionWithReward(i * 100));
            }
            assertEquals(capacity, buffer.size(), "超容后大小仍为capacity");

            Random rand = new Random(1);
            List<Double> rewards = new ArrayList<>();
            for (int s = 0; s < 200; s++) {
                List<DQNMicroClimateController.Transition> sample = buffer.sample(5, rand);
                for (DQNMicroClimateController.Transition t : sample) {
                    rewards.add(t.getReward());
                }
            }

            Set<Double> uniqueRewards = new HashSet<>(rewards);
            assertTrue(uniqueRewards.contains(1000.0), "应包含新样本(1000)");
            assertTrue(uniqueRewards.contains(1400.0), "应包含最新样本(1400)");
            log.info("环形缓冲验证通过，出现的奖励值集合: {}", uniqueRewards);
        }

        private DQNMicroClimateController.Transition createTransitionWithReward(double reward) {
            DQNMicroClimateController.State s = DQNMicroClimateController.State.builder()
                    .currentRh(0.5).rhTrend(0.0).hourOfDay(0.5)
                    .dehumidifierOn(0).humidifierOn(0).build();
            return DQNMicroClimateController.Transition.builder()
                    .state(s).action(0).reward(reward)
                    .nextState(s).done(false).build();
        }
    }

    @Nested
    @DisplayName("七、训练收敛测试（核心）")
    class TrainingConvergenceTests {

        @Test
        @Timeout(value = 60)
        @DisplayName("20. 300回合训练收敛到节能策略（核心验证）")
        void testTraining_300Episodes_ConvergesToEnergySavingPolicy() throws Exception {
            log.info("===== 开始核心收敛测试：300回合 =====");

            setPrivateField(controller, "hidden1Dim", 16);
            setPrivateField(controller, "hidden2Dim", 8);
            setPrivateField(controller, "replayBufferSize", 1000);
            setPrivateField(controller, "batchSize", 32);
            setPrivateField(controller, "targetUpdateFreq", 100);
            setPrivateField(controller, "learningRate", 0.005);
            setPrivateField(controller, "epsilonStart", 1.0);
            setPrivateField(controller, "epsilonEnd", 0.05);
            setPrivateField(controller, "epsilonDecay", 0.997);
            setPrivateField(controller, "random", new Random(42));
            setPrivateField(controller, "epsilon", 1.0);
            setPrivateField(controller, "totalSteps", 0);
            setPrivateField(controller, "dehumidifierEffect", 0.03);
            setPrivateField(controller, "humidifierEffect", 0.03);
            setPrivateField(controller, "leakageRate", 0.005);

            int totalEpisodes = 300;
            int stepsPerEpisode = 24 * 5;

            double[] episodeRewards = new double[totalEpisodes];
            double[][] episodeStats = new double[totalEpisodes][3];

            List<Double> rewardHistory = new ArrayList<>();

            for (int ep = 0; ep < totalEpisodes; ep++) {
                Random trainRand = (Random) getPrivateField(controller, "random");
                double initRh = 0.4 + trainRand.nextDouble() * 0.4;
                int initHour = trainRand.nextInt(24);
                controller.resetEnvironment(initRh, initHour);

                double epReward = 0.0;
                int deliqSteps = 0;
                int deviceOnSteps = 0;
                int totalStepsEp = 0;

                for (int step = 0; step < stepsPerEpisode; step++) {
                    int action = controller.selectAction(controller.getCurrentState());
                    DQNMicroClimateController.StepResult result = controller.step(action);

                    DQNMicroClimateController.Transition trans = DQNMicroClimateController.Transition.builder()
                            .state(controller.getCurrentState())
                            .action(action)
                            .reward(result.getReward())
                            .nextState(result.getNextState())
                            .done(result.isDone())
                            .build();
                    DQNMicroClimateController.ReplayBuffer buf = (DQNMicroClimateController.ReplayBuffer)
                            getPrivateField(controller, "replayBuffer");
                    buf.add(trans);

                    double rh = result.getNextState().getCurrentRh();
                    if (rh > 0.65 && rh < 0.80) deliqSteps++;
                    if (action == 1 || action == 2 || action == 3) deviceOnSteps++;
                    totalStepsEp++;

                    epReward += result.getReward();

                    if (buf.size() >= (int) getPrivateField(controller, "batchSize")) {
                        trainOneBatch();
                    }

                    int totalStepCount = (int) getPrivateField(controller, "totalSteps");
                    setPrivateField(controller, "totalSteps", totalStepCount + 1);

                    int freq = (int) getPrivateField(controller, "targetUpdateFreq");
                    if ((totalStepCount + 1) % freq == 0) {
                        DQNMicroClimateController.DQNNetwork qNet = (DQNMicroClimateController.DQNNetwork)
                                getPrivateField(controller, "qNetwork");
                        DQNMicroClimateController.DQNNetwork tNet = (DQNMicroClimateController.DQNNetwork)
                                getPrivateField(controller, "targetNetwork");
                        tNet.copyWeightsFrom(qNet);
                    }

                    double eps = (double) getPrivateField(controller, "epsilon");
                    double decay = (double) getPrivateField(controller, "epsilonDecay");
                    double epsEnd = (double) getPrivateField(controller, "epsilonEnd");
                    setPrivateField(controller, "epsilon", Math.max(epsEnd, eps * decay));

                    if (result.isDone()) break;
                }

                episodeRewards[ep] = epReward;
                rewardHistory.add(epReward);
                episodeStats[ep][0] = (double) deliqSteps / totalStepsEp;
                episodeStats[ep][1] = (double) deviceOnSteps / totalStepsEp;
                episodeStats[ep][2] = totalStepsEp;

                if ((ep + 1) % 50 == 0) {
                    double avgRew = avg(episodeRewards, ep - 49, ep + 1);
                    double avgDeliq = avg(episodeStats, ep - 49, ep + 1, 0);
                    double avgDev = avg(episodeStats, ep - 49, ep + 1, 1);
                    log.info("回合 {}/{} | 近50回均奖励={:.1f} | 潮解停留={:.1f}% | 设备运行={:.1f}% | ε={:.3f}",
                            ep + 1, totalEpisodes, avgRew, avgDeliq * 100, avgDev * 100,
                            getPrivateField(controller, "epsilon"));
                }
            }

            double first50Avg = avg(episodeRewards, 0, 50);
            double last50Avg = avg(episodeRewards, 250, 300);
            double last10Deliq = avg(episodeStats, 290, 300, 0);
            double last10Device = avg(episodeStats, 290, 300, 1);

            log.info("===== 训练收敛测试结果 =====");
            log.info("前50回合均奖励: {:.2f}", first50Avg);
            log.info("后50回合均奖励: {:.2f}", last50Avg);
            log.info("奖励提升: {:.1f}%", (last50Avg - first50Avg) / Math.abs(first50Avg + 1e-9) * 100);
            log.info("最后10回合潮解带停留率: {:.1f}% (阈值<30%)", last10Deliq * 100);
            log.info("最后10回合设备运行率: {:.1f}% (阈值<40%)", last10Device * 100);

            assertTrue(last50Avg > first50Avg,
                    String.format("条件1失败: 后50回均奖励(%.1f)应>前50回(%.1f)",
                            last50Avg, first50Avg));

            assertTrue(last10Deliq < 0.30,
                    String.format("条件2失败: 潮解带停留率%.1f%%应<30%%", last10Deliq * 100));

            assertTrue(last10Device < 0.40,
                    String.format("条件3失败: 设备运行率%.1f%%应<40%%", last10Device * 100));

            log.info("✓ 全部3项核心收敛条件通过！");
        }

        @Test
        @DisplayName("21. ε随训练步长衰减，最终接近epsilonEnd")
        void testEpsilonDecay_DecreasesOverTime() throws Exception {
            setPrivateField(controller, "epsilonStart", 1.0);
            setPrivateField(controller, "epsilonEnd", 0.05);
            setPrivateField(controller, "epsilonDecay", 0.995);
            setPrivateField(controller, "epsilon", 1.0);

            double startEps = controller.getEpsilon();
            assertEquals(1.0, startEps, 1e-6, "初始ε应为1.0");

            controller.train(50);

            double afterTrainEps = controller.getEpsilon();
            log.info("ε衰减验证: 初始={:.3f}，训练50回合后={:.4f}", startEps, afterTrainEps);

            assertTrue(afterTrainEps < startEps * 0.9,
                    "训练后ε应显著衰减，当前=" + afterTrainEps);
            assertTrue(afterTrainEps >= 0.05 - 1e-6,
                    "ε不应低于epsilonEnd(0.05)，当前=" + afterTrainEps);
        }

        private void trainOneBatch() throws Exception {
            DQNMicroClimateController.ReplayBuffer buf = (DQNMicroClimateController.ReplayBuffer)
                    getPrivateField(controller, "replayBuffer");
            DQNMicroClimateController.DQNNetwork qNet = (DQNMicroClimateController.DQNNetwork)
                    getPrivateField(controller, "qNetwork");
            DQNMicroClimateController.DQNNetwork tNet = (DQNMicroClimateController.DQNNetwork)
                    getPrivateField(controller, "targetNetwork");
            int batchSizeVal = (int) getPrivateField(controller, "batchSize");
            double gammaVal = (double) getPrivateField(controller, "gamma");
            double lrVal = (double) getPrivateField(controller, "learningRate");
            Random rand = (Random) getPrivateField(controller, "random");

            List<DQNMicroClimateController.Transition> batch = buf.sample(batchSizeVal, rand);

            for (DQNMicroClimateController.Transition t : batch) {
                double[] sArr = t.getState().toArray();
                double[] nsArr = t.getNextState().toArray();

                double[] currentQ = qNet.forward(sArr);
                double[] nextQ = tNet.forward(nsArr);
                double maxNext = max(nextQ);

                double target = t.getReward();
                if (!t.isDone()) target += gammaVal * maxNext;

                double[] targetQ = currentQ.clone();
                targetQ[t.getAction()] = target;

                qNet.backward(sArr, currentQ, targetQ, lrVal);
            }
        }

        private double max(double[] arr) {
            double m = arr[0];
            for (double v : arr) if (v > m) m = v;
            return m;
        }

        private double avg(double[] arr, int from, int to) {
            double sum = 0;
            int cnt = 0;
            for (int i = from; i < to && i < arr.length; i++) {
                sum += arr[i];
                cnt++;
            }
            return cnt == 0 ? 0 : sum / cnt;
        }

        private double avg(double[][] arr, int from, int to, int col) {
            double sum = 0;
            int cnt = 0;
            for (int i = from; i < to && i < arr.length; i++) {
                sum += arr[i][col];
                cnt++;
            }
            return cnt == 0 ? 0 : sum / cnt;
        }
    }

    @Nested
    @DisplayName("八、动作选择测试")
    class ActionSelectionTests {

        @Test
        @DisplayName("22. ε高时探索动作多样性")
        void testEpsilonGreedy_ExplorationInEarlyTraining() throws Exception {
            setPrivateField(controller, "epsilon", 0.99);
            setPrivateField(controller, "random", new Random(7));

            controller.resetEnvironment(0.5, 12);
            DQNMicroClimateController.State s = controller.getCurrentState();

            Map<Integer, Integer> counts = new HashMap<>();
            int trials = 500;

            for (int i = 0; i < trials; i++) {
                int a = controller.selectAction(s);
                counts.merge(a, 1, Integer::sum);
            }

            log.info("高ε动作分布(500次): {}", counts);

            for (int a = 0; a < 4; a++) {
                assertTrue(counts.getOrDefault(a, 0) > trials * 0.10,
                        "高ε下每个动作都应被探索至少10%的次数，动作" + a
                                + "仅出现" + counts.getOrDefault(a, 0) + "次");
            }
        }

        @Test
        @DisplayName("23. 训练后选最优动作稳定（确定性）")
        void testSelectBestAction_DeterministicWhenTrained() throws Exception {
            controller.train(80);

            DQNMicroClimateController.State testState = DQNMicroClimateController.State.builder()
                    .currentRh(0.55).rhTrend(0.0).hourOfDay(0.5)
                    .dehumidifierOn(0).humidifierOn(0).build();

            Set<Integer> results = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                results.add(controller.selectBestAction(testState));
            }
            assertEquals(1, results.size(),
                    "selectBestAction应是确定性的，20次调用返回相同结果，实际=" + results);

            int chosen = results.iterator().next();
            double[] qVals = controller.getQValues(testState);
            int bestIdx = 0;
            for (int i = 1; i < qVals.length; i++) {
                if (qVals[i] > qVals[bestIdx]) bestIdx = i;
            }
            assertEquals(bestIdx, chosen,
                    "selectBestAction应选择Q值最大的动作，Q值=" + Arrays.toString(qVals));
        }
    }

    @Nested
    @DisplayName("九、边界/异常测试")
    class BoundaryExceptionTests {

        @Test
        @DisplayName("24. 相同种子重置后初始状态一致")
        void testResetEnvironment_DeterministicWithSeed() throws Exception {
            DQNMicroClimateController c1 = new DQNMicroClimateController();
            DQNMicroClimateController c2 = new DQNMicroClimateController();

            setPrivateField(c1, "hidden1Dim", 16);
            setPrivateField(c1, "hidden2Dim", 8);
            setPrivateField(c2, "hidden1Dim", 16);
            setPrivateField(c2, "hidden2Dim", 8);

            c1.resetEnvironment(0.55, 14);
            c2.resetEnvironment(0.55, 14);

            assertEquals(c1.getCurrentState().getCurrentRh(),
                    c2.getCurrentState().getCurrentRh(), 1e-9,
                    "相同参数重置后currentRh应一致");
            assertEquals(c1.getCurrentState().getHourOfDay(),
                    c2.getCurrentState().getHourOfDay(), 1e-9,
                    "相同参数重置后hourOfDay应一致");
            assertEquals(c1.getCurrentState().getDehumidifierOn(),
                    c2.getCurrentState().getDehumidifierOn(),
                    "相同参数重置后dehumidifierOn应一致");
            assertEquals(c1.getCurrentState().getHumidifierOn(),
                    c2.getCurrentState().getHumidifierOn(),
                    "相同参数重置后humidifierOn应一致");

            double[] q1 = c1.getQValues(c1.getCurrentState());
            double[] q2 = c2.getQValues(c2.getCurrentState());
            assertArrayEquals(q1, q2, 1e-9,
                    "相同种子下网络初始权重相同，Q值预测应一致");
        }

        @Test
        @DisplayName("25. 保存再加载模型后Q值预测一致")
        void testModelSaveLoad_PreservesWeights() throws Exception {
            setPrivateField(controller, "hidden1Dim", 16);
            setPrivateField(controller, "hidden2Dim", 8);

            controller.train(30);

            DQNMicroClimateController.State s = DQNMicroClimateController.State.builder()
                    .currentRh(0.6).rhTrend(-0.05).hourOfDay(0.3)
                    .dehumidifierOn(1).humidifierOn(0).build();
            double[] qBefore = controller.getQValues(s);

            File tempFile = File.createTempFile("dqn_model_test_", ".bin");
            tempFile.deleteOnExit();
            String path = tempFile.getAbsolutePath();

            controller.saveModel(path);

            DQNMicroClimateController loaded = new DQNMicroClimateController();
            setPrivateField(loaded, "hidden1Dim", 16);
            setPrivateField(loaded, "hidden2Dim", 8);
            loaded.resetEnvironment(0.5, 12);
            loaded.loadModel(path);

            double[] qAfter = loaded.getQValues(s);
            log.info("保存前Q值: {}", Arrays.toString(qBefore));
            log.info("加载后Q值: {}", Arrays.toString(qAfter));

            assertArrayEquals(qBefore, qAfter, 1e-6,
                    "保存再加载模型后Q值预测应完全一致");

            assertTrue(tempFile.length() > 0, "模型文件不应为空");
            log.info("模型文件大小: {} bytes", tempFile.length());
        }
    }

    @Nested
    @DisplayName("十、动作持续性奖励修复验证")
    class ActionPersistenceTests {

        @Test
        @DisplayName("26. 修复验证：连续相同动作获得持续性奖励")
        void testActionPersistence_ContinuousSameActionGetsBonus() throws Exception {
            setPrivateField(controller, "actionPersistenceBonus", 0.3);
            setPrivateField(controller, "penaltySwitch", -1.0);
            controller.resetEnvironment(0.55, 12);

            setPrivateField(controller, "lastAction", 1);
            setPrivateField(controller, "actionPersistenceCount", 0);

            double rewardSame = invokeCalculateReward(0.55, 1);
            log.info("连续执行动作1的奖励: {}", rewardSame);

            assertTrue(rewardSame > 0,
                    "安全区+持续动作应获得正奖励，实际: " + rewardSame);
        }

        @Test
        @DisplayName("27. 修复验证：切换动作受到显著惩罚")
        void testActionSwitching_SignificantPenalty() throws Exception {
            setPrivateField(controller, "actionPersistenceBonus", 0.3);
            setPrivateField(controller, "penaltySwitch", -1.0);
            controller.resetEnvironment(0.55, 12);

            setPrivateField(controller, "lastAction", 0);
            setPrivateField(controller, "actionPersistenceCount", 5);

            double rewardSwitch = invokeCalculateReward(0.55, 1);
            log.info("从动作0切换到动作1的奖励: {}", rewardSwitch);

            setPrivateField(controller, "lastAction", 1);
            setPrivateField(controller, "actionPersistenceCount", 2);
            double rewardPersist = invokeCalculateReward(0.55, 1);
            log.info("保持动作1的奖励: {}", rewardPersist);

            assertTrue(rewardPersist > rewardSwitch,
                    String.format("持续动作奖励(%.2f)应高于切换动作奖励(%.2f)",
                            rewardPersist, rewardSwitch));
        }

        @Test
        @DisplayName("28. 修复验证：待机持续3步以上获得较小持续性奖励")
        void testIdlePersistence_BonusAfter3Steps() throws Exception {
            setPrivateField(controller, "actionPersistenceBonus", 0.3);
            setPrivateField(controller, "penaltySwitch", -1.0);
            controller.resetEnvironment(0.55, 12);

            setPrivateField(controller, "lastAction", 0);
            setPrivateField(controller, "actionPersistenceCount", 2);
            double rewardBelow3 = invokeCalculateReward(0.55, 0);

            setPrivateField(controller, "lastAction", 0);
            setPrivateField(controller, "actionPersistenceCount", 3);
            double rewardAt3 = invokeCalculateReward(0.55, 0);

            log.info("待机2步奖励: {}, 待机3步奖励: {}", rewardBelow3, rewardAt3);
            assertTrue(rewardAt3 > rewardBelow3,
                    "待机3步以上应获得额外持续性奖励");
        }

        @Test
        @DisplayName("29. 修复验证：训练后动作切换率显著降低")
        void testTrainedPolicy_LowerSwitchRate() throws Exception {
            setPrivateField(controller, "actionPersistenceBonus", 0.3);
            setPrivateField(controller, "penaltySwitch", -1.0);

            controller.train(100);

            setPrivateField(controller, "epsilon", 0.05);
            controller.resetEnvironment(0.55, 12);

            int switches = 0;
            int lastAct = -1;
            int testSteps = 50;

            for (int i = 0; i < testSteps; i++) {
                int act = controller.selectAction(controller.getCurrentState());
                if (lastAct >= 0 && act != lastAct) switches++;
                lastAct = act;
                controller.step(act);
            }

            double switchRate = (double) switches / (testSteps - 1);
            log.info("训练后动作切换率: {:.1f}% ({}次切换/{}步)", switchRate * 100, switches, testSteps - 1);
            assertTrue(switchRate < 0.7,
                    String.format("训练后切换率应<70%%，实际%.1f%%", switchRate * 100));
        }

        private double invokeCalculateReward(double rh, int action) throws Exception {
            Method method = DQNMicroClimateController.class.getDeclaredMethod(
                    "calculateReward", double.class, int.class, boolean.class, boolean.class);
            method.setAccessible(true);
            boolean dehumidifierOn = (action == 1 || action == 3);
            boolean humidifierOn = (action == 2 || action == 3);
            return (double) method.invoke(controller, rh, action, dehumidifierOn, humidifierOn);
        }
    }
}
