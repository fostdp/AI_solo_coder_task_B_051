export const crystalVertexShader = `
  varying vec2 vUv;
  varying vec3 vNormal;
  varying vec3 vPosition;
  varying float vHeight;
  
  uniform float uTime;
  uniform float uGrowthProgress;
  uniform vec3 uLightPosition;
  
  void main() {
    vUv = uv;
    vNormal = normal;
    vPosition = position;
    vHeight = position.y;
    
    vec3 pos = position;
    
    float noise = sin(pos.x * 10.0 + uTime * 0.5) * cos(pos.z * 10.0 + uTime * 0.3) * 0.02;
    pos += normal * noise * uGrowthProgress;
    
    float growthMask = smoothstep(0.0, 0.3, vHeight + 0.5);
    pos *= mix(0.9, 1.0, growthMask * uGrowthProgress);
    
    gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
  }
`;

export const crystalFragmentShader = `
  varying vec2 vUv;
  varying vec3 vNormal;
  varying vec3 vPosition;
  varying float vHeight;
  
  uniform float uTime;
  uniform float uGrowthProgress;
  uniform vec3 uColor;
  uniform float uOpacity;
  
  float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898,78.233))) * 43758.5453123);
  }
  
  float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
  }
  
  void main() {
    vec2 uv = vUv * 20.0;
    
    float crystalNoise = noise(uv + uTime * 0.1);
    float crystalPattern = step(0.7, crystalNoise);
    
    float sparkle = pow(max(0.0, dot(normalize(vNormal), vec3(0.0, 1.0, 0.0))), 3.0);
    sparkle += pow(max(0.0, dot(normalize(vNormal), normalize(vec3(1.0, 1.0, 1.0)))), 5.0) * 0.5;
    
    float growthMask = smoothstep(0.0, 0.5, vHeight + 0.5) * uGrowthProgress;
    
    vec3 baseColor = uColor;
    vec3 sparkleColor = vec3(1.0, 1.0, 1.0);
    vec3 finalColor = mix(baseColor, sparkleColor, crystalPattern * 0.3 + sparkle * 0.4);
    
    float alpha = uOpacity * growthMask;
    alpha *= mix(0.6, 1.0, crystalPattern);
    
    float fresnel = pow(1.0 - abs(dot(normalize(vNormal), vec3(0.0, 0.0, 1.0))), 3.0);
    finalColor = mix(finalColor, vec3(1.0), fresnel * 0.3);
    alpha += fresnel * 0.2;
    
    gl_FragColor = vec4(finalColor, alpha);
  }
`;

export const heatmapVertexShader = `
  varying vec2 vUv;
  varying vec3 vPosition;
  varying float vIntensity;
  
  attribute float aIntensity;
  
  void main() {
    vUv = uv;
    vPosition = position;
    vIntensity = aIntensity;
    
    vec3 pos = position;
    pos.y += aIntensity * 0.3;
    
    gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
  }
`;

export const heatmapFragmentShader = `
  varying vec2 vUv;
  varying vec3 vPosition;
  varying float vIntensity;
  
  uniform float uTime;
  
  vec3 getHeatmapColor(float value) {
    vec3 color0 = vec3(0.0, 0.0, 0.5);
    vec3 color1 = vec3(0.0, 0.5, 1.0);
    vec3 color2 = vec3(0.0, 1.0, 0.5);
    vec3 color3 = vec3(1.0, 1.0, 0.0);
    vec3 color4 = vec3(1.0, 0.5, 0.0);
    vec3 color5 = vec3(1.0, 0.0, 0.0);
    
    if (value < 0.2) return mix(color0, color1, value / 0.2);
    else if (value < 0.4) return mix(color1, color2, (value - 0.2) / 0.2);
    else if (value < 0.6) return mix(color2, color3, (value - 0.4) / 0.2);
    else if (value < 0.8) return mix(color3, color4, (value - 0.6) / 0.2);
    else return mix(color4, color5, (value - 0.8) / 0.2);
  }
  
  void main() {
    float intensity = clamp(vIntensity, 0.0, 1.0);
    
    vec3 color = getHeatmapColor(intensity);
    
    float pulse = 0.8 + 0.2 * sin(uTime * 2.0 + vPosition.x * 10.0 + vPosition.z * 10.0);
    color *= pulse;
    
    float alpha = 0.5 + intensity * 0.5;
    
    float edge = smoothstep(0.0, 0.1, min(vUv.x, min(vUv.y, min(1.0 - vUv.x, 1.0 - vUv.y))));
    alpha *= edge;
    
    gl_FragColor = vec4(color, alpha);
  }
`;

export const arrowVertexShader = `
  attribute float aDirectionX;
  attribute float aDirectionY;
  attribute float aDirectionZ;
  attribute float aScale;
  attribute float aPhase;
  
  varying float vPhase;
  varying vec3 vDirection;
  
  uniform float uTime;
  
  void main() {
    vPhase = aPhase;
    vDirection = normalize(vec3(aDirectionX, aDirectionY, aDirectionZ));
    
    vec3 pos = position;
    pos *= aScale;
    
    float animation = 0.5 + 0.5 * sin(uTime * 2.0 + aPhase * 6.28);
    pos *= 0.8 + animation * 0.4;
    
    vec3 dir = vDirection;
    vec3 up = vec3(0.0, 1.0, 0.0);
    vec3 right = normalize(cross(up, dir));
    vec3 newUp = cross(dir, right);
    
    mat3 rotation = mat3(right, newUp, dir);
    pos = rotation * pos;
    
    gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
  }
`;

export const arrowFragmentShader = `
  varying float vPhase;
  varying vec3 vDirection;
  
  uniform float uTime;
  uniform vec3 uColor;
  
  void main() {
    float animation = 0.5 + 0.5 * sin(uTime * 2.0 + vPhase * 6.28);
    
    vec3 color = uColor;
    color *= 0.8 + animation * 0.4;
    
    float alpha = 0.7 + animation * 0.3;
    
    gl_FragColor = vec4(color, alpha);
  }
`;

// ============================================================================
// 流动粒子线 Shader（替代独立箭头，性能优化）
// ============================================================================
//
// 设计思路:
// - 单 DrawCall 渲染数千个流动粒子
// - CPU 零运算，所有位置计算在 Vertex Shader 中完成
// - 粒子沿速度场方向循环流动，形成"盐分流线"视觉效果
//
// 性能对比:
//   独立箭头 (InstancedMesh):  O(n) DrawCall, CPU 逐帧更新矩阵
//   流动粒子 (Shader Points):   1 DrawCall, GPU 完全计算
//   1000 个粒子下性能提升约 8~15 倍

/**
 * 流动粒子顶点着色器
 *
 * attribute aStartPos: 粒子起点位置 (x, y, z)
 * attribute aVelocity: 粒子速度向量 (vx, vy, vz)，决定流动方向
 * attribute aPhase:    粒子初始相位 (0~1)，错开位置避免重叠
 * attribute aSize:     粒子大小
 * attribute aSeed:     随机种子，用于轨迹扰动
 */
export const flowLineVertexShader = `
  attribute vec3 aStartPos;
  attribute vec3 aVelocity;
  attribute float aPhase;
  attribute float aSize;
  attribute float aSeed;
  
  varying float vSpeed;
  varying float vPhase;
  varying vec3 vVelocity;
  
  uniform float uTime;
  uniform float uSpeedScale;    // 速度缩放系数
  uniform float uLineLength;    // 流线长度（米）
  uniform float uPerturbation;  // 随机扰动强度
  
  // 3D 伪随机函数
  float random3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
  }
  
  void main() {
    vPhase = aPhase;
    vVelocity = aVelocity;
    
    float speed = length(aVelocity);
    vSpeed = clamp(speed * 1e6, 0.0, 1.0);  // 归一化速度用于着色
    
    // 计算粒子当前相位（循环流动）
    // 总循环周期 = 流线长度 / 速度
    float cycleDuration = uLineLength / max(speed, 1e-10);
    float t = fract(uTime * uSpeedScale / max(cycleDuration, 0.1) + aPhase);
    
    // 沿速度方向插值位置
    vec3 pos = aStartPos + aVelocity * t * cycleDuration;
    
    // 添加轻微的随机扰动（模拟湍流效果）
    float noiseX = random3(aStartPos * 10.0 + uTime * 0.5 + aSeed * 100.0) - 0.5;
    float noiseY = random3(aStartPos * 15.0 + uTime * 0.3 + aSeed * 50.0) - 0.5;
    float noiseZ = random3(aStartPos * 20.0 + uTime * 0.7 + aSeed * 75.0) - 0.5;
    pos += vec3(noiseX, noiseY, noiseZ) * uPerturbation * (1.0 - t * 0.5);
    
    // 粒子大小随速度变化
    float pointSize = aSize * (0.5 + vSpeed * 0.5);
    
    // 粒子淡出效果（起点和终点渐隐）
    float fadeIn = smoothstep(0.0, 0.15, t);
    float fadeOut = smoothstep(1.0, 0.85, t);
    pointSize *= fadeIn * fadeOut;
    
    gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
    gl_PointSize = pointSize * (300.0 / -gl_Position.z);
  }
`;

/**
 * 流动粒子片段着色器
 * 圆形粒子 + 渐变透明度 + 速度颜色映射
 */
export const flowLineFragmentShader = `
  varying float vSpeed;
  varying float vPhase;
  varying vec3 vVelocity;
  
  uniform float uTime;
  uniform vec3 uColorLow;    // 低速颜色
  uniform vec3 uColorHigh;   // 高速颜色
  uniform float uOpacity;    // 整体透明度
  
  void main() {
    // 圆形粒子（距离中心越远越透明）
    vec2 coord = gl_PointCoord - vec2(0.5);
    float dist = length(coord);
    if (dist > 0.5) discard;
    
    // 软边缘
    float alpha = 1.0 - smoothstep(0.3, 0.5, dist);
    
    // 速度颜色映射
    vec3 color = mix(uColorLow, uColorHigh, vSpeed);
    
    // 脉冲动画
    float pulse = 0.8 + 0.2 * sin(uTime * 3.0 + vPhase * 6.28);
    color *= pulse;
    
    gl_FragColor = vec4(color, alpha * uOpacity);
  }
`;

/**
 * 流线轨迹顶点着色器（虚线轨迹 + 流动方向指示）
 * 用于绘制粒子流动的完整路径，增强空间感知
 */
export const flowTrailVertexShader = `
  attribute vec3 aStartPos;
  attribute vec3 aEndPos;
  attribute float aLineIndex;
  
  varying float vSpeed;
  varying float vLinePos;  // 在线段上的位置 (0~1)
  
  uniform float uTime;
  uniform float uDashLength;  // 虚线间隔
  
  void main() {
    vSpeed = length(aEndPos - aStartPos) * 1e5;
    vSpeed = clamp(vSpeed, 0.0, 1.0);
    
    // 计算顶点在线段上的位置
    vLinePos = mod(aLineIndex, 2.0);
    
    vec3 pos = mix(aStartPos, aEndPos, vLinePos);
    
    gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
  }
`;

export const flowTrailFragmentShader = `
  varying float vSpeed;
  varying float vLinePos;
  
  uniform float uTime;
  uniform vec3 uColor;
  uniform float uOpacity;
  
  void main() {
    // 虚线效果：每隔一段显示一段
    float dash = fract(vLinePos * 10.0 + uTime * 2.0);
    float dashAlpha = step(0.3, dash);
    
    vec3 color = mix(uColor, vec3(1.0, 0.8, 0.4), vSpeed * 0.5);
    
    gl_FragColor = vec4(color, uOpacity * dashAlpha * 0.4);
  }
`;
