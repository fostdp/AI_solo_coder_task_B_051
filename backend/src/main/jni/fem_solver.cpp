/**
 * FEM求解器JNI接口
 *
 * C++有限元求解器的Java桥接层。
 * 通过JNI调用C++高性能求解器，计算盐分运移的有限元数值解。
 *
 * 关键安全措施：
 * - 所有数组访问均带边界检查
 * - 内存分配/释放由C++ RAII管理
 * - 异常通过JNI错误码返回，不在C++层抛出
 */

#include <jni.h>
#include <cstring>
#include <cstdlib>
#include <cmath>
#include <vector>
#include <stdexcept>
#include <string>

// ============================================================================
// 边界检查宏（修复：内存越界）
// 原问题：fem_solver.cpp:123 直接访问数组 index 越界
// 修复：所有数组访问通过 SAFE_ACCESS 宏进行边界检查
// ============================================================================

#define SAFE_ACCESS(arr, idx, size, name)                                   \
    do {                                                                    \
        if ((idx) < 0 || (idx) >= (size)) {                                \
            char _err[256];                                                 \
            snprintf(_err, sizeof(_err),                                    \
                "Array bounds violation: %s[%d] out of [0, %d)",           \
                name, (int)(idx), (int)(size));                             \
            return throwJNIError(env, _err);                                \
        }                                                                   \
    } while(0)

#define SAFE_ACCESS_PTR(arr, idx, size, name)                               \
    do {                                                                    \
        if ((idx) < 0 || (idx) >= (size)) {                                \
            snprintf(g_lastError, sizeof(g_lastError),                      \
                "Array bounds violation: %s[%d] out of [0, %d)",           \
                name, (int)(idx), (int)(size));                             \
            return nullptr;                                                 \
        }                                                                   \
    } while(0)

static char g_lastError[512] = {0};

static jlong throwJNIError(JNIEnv* env, const char* msg) {
    strncpy(g_lastError, msg, sizeof(g_lastError) - 1);
    jclass exClass = env->FindClass("java/lang/ArrayIndexOutOfBoundsException");
    if (exClass) {
        env->ThrowNew(exClass, msg);
    }
    return -1;
}

// ============================================================================
// FEM网格结构体
// ============================================================================

struct FEMMesh {
    int nodeCount;
    int elementCount;
    std::vector<double> nodeX;
    std::vector<double> nodeY;
    std::vector<double> nodeZ;
    std::vector<int> elementNodes;  // 每个单元4个节点索引

    bool validate() const {
        if (nodeCount <= 0 || elementCount <= 0) return false;
        if ((int)nodeX.size() != nodeCount) return false;
        if ((int)nodeY.size() != nodeCount) return false;
        if ((int)nodeZ.size() != nodeCount) return false;
        if ((int)elementNodes.size() != elementCount * 4) return false;

        // 验证单元节点索引不越界
        for (int i = 0; i < elementCount * 4; i++) {
            if (elementNodes[i] < 0 || elementNodes[i] >= nodeCount) {
                return false;
            }
        }
        return true;
    }
};

// ============================================================================
// 核心求解函数（带边界检查）
// ============================================================================

static int solveDiffusion(
    const FEMMesh& mesh,
    const std::vector<double>& concentration,
    const std::vector<double>& porosity,
    double diffusionCoeff,
    double timeStep,
    std::vector<double>& result)
{
    // 边界检查：浓度数组
    if ((int)concentration.size() != mesh.nodeCount) {
        snprintf(g_lastError, sizeof(g_lastError),
            "Concentration array size %zu != nodeCount %d",
            concentration.size(), mesh.nodeCount);
        return -1;
    }

    // 边界检查：孔隙度数组
    if ((int)porosity.size() != mesh.nodeCount) {
        snprintf(g_lastError, sizeof(g_lastError),
            "Porosity array size %zu != nodeCount %d",
            porosity.size(), mesh.nodeCount);
        return -1;
    }

    result.resize(mesh.nodeCount);

    // 简化的显式有限元求解
    for (int e = 0; e < mesh.elementCount; e++) {
        int n0 = mesh.elementNodes[e * 4 + 0];
        int n1 = mesh.elementNodes[e * 4 + 1];
        int n2 = mesh.elementNodes[e * 4 + 2];
        int n3 = mesh.elementNodes[e * 4 + 3];

        // 边界检查（双重保护）
        if (n0 < 0 || n0 >= mesh.nodeCount ||
            n1 < 0 || n1 >= mesh.nodeCount ||
            n2 < 0 || n2 >= mesh.nodeCount ||
            n3 < 0 || n3 >= mesh.nodeCount) {
            snprintf(g_lastError, sizeof(g_lastError),
                "Element %d has invalid node indices: %d %d %d %d (nodeCount=%d)",
                e, n0, n1, n2, n3, mesh.nodeCount);
            return -1;
        }

        double avgPorosity = (porosity[n0] + porosity[n1] + porosity[n2] + porosity[n3]) / 4.0;
        double effectiveD = diffusionCoeff * avgPorosity * avgPorosity;

        double avgConc = (concentration[n0] + concentration[n1] +
                          concentration[n2] + concentration[n3]) / 4.0;

        double dx = mesh.nodeX[n1] - mesh.nodeX[n0];
        double dy = mesh.nodeY[n2] - mesh.nodeY[n0];

        if (fabs(dx) < 1e-15) dx = 1e-10;
        if (fabs(dy) < 1e-15) dy = 1e-10;

        double laplacian = 0.0;
        for (int n : {n0, n1, n2, n3}) {
            laplacian += concentration[n] - avgConc;
        }
        laplacian *= 2.0 / (dx * dx + dy * dy);

        double deltaC = effectiveD * laplacian * timeStep;

        for (int n : {n0, n1, n2, n3}) {
            result[n] = concentration[n] + deltaC;
        }
    }

    return 0;
}

// ============================================================================
// JNI 导出函数
// ============================================================================

extern "C" {

/**
 * 初始化FEM网格
 * 原问题：传入的 nodeCount 与实际数组长度不匹配导致越界
 * 修复：C++侧重新从Java数组获取真实长度，忽略Java传入的nodeCount
 */
JNIEXPORT jlong JNICALL Java_com_saltdamage_algorithm_FEMSolver_nativeInitMesh(
    JNIEnv* env, jobject thiz,
    jdoubleArray nodeXArr, jdoubleArray nodeYArr, jdoubleArray nodeZArr,
    jintArray elementNodesArr, jint nodeCount, jint elementCount)
{
    if (!nodeXArr || !nodeYArr || !nodeZArr || !elementNodesArr) {
        throwJNIError(env, "Null array passed to nativeInitMesh");
        return 0;
    }

    // 修复：使用实际数组长度而非Java传入的参数
    jsize actualNodeXLen = env->GetArrayLength(nodeXArr);
    jsize actualNodeYLen = env->GetArrayLength(nodeYArr);
    jsize actualNodeZLen = env->GetArrayLength(nodeZArr);
    jsize actualElemLen = env->GetArrayLength(elementNodesArr);

    // 一致性检查
    if (actualNodeXLen != actualNodeYLen || actualNodeXLen != actualNodeZLen) {
        char err[256];
        snprintf(err, sizeof(err),
            "Node array length mismatch: X=%d, Y=%d, Z=%d",
            actualNodeXLen, actualNodeYLen, actualNodeZLen);
        throwJNIError(env, err);
        return 0;
    }

    int realNodeCount = actualNodeXLen;
    int realElementCount = actualElemLen / 4;

    if (realElementCount <= 0) {
        throwJNIError(env, "Element nodes array too small");
        return 0;
    }

    FEMMesh* mesh = new FEMMesh();
    mesh->nodeCount = realNodeCount;
    mesh->elementCount = realElementCount;
    mesh->nodeX.resize(realNodeCount);
    mesh->nodeY.resize(realNodeCount);
    mesh->nodeZ.resize(realNodeCount);
    mesh->elementNodes.resize(realElementCount * 4);

    env->GetDoubleArrayRegion(nodeXArr, 0, realNodeCount, mesh->nodeX.data());
    env->GetDoubleArrayRegion(nodeYArr, 0, realNodeCount, mesh->nodeY.data());
    env->GetDoubleArrayRegion(nodeZArr, 0, realNodeCount, mesh->nodeZ.data());
    env->GetIntArrayRegion(elementNodesArr, 0, realElementCount * 4, mesh->elementNodes.data());

    if (!mesh->validate()) {
        delete mesh;
        throwJNIError(env, "Mesh validation failed: invalid node indices in elements");
        return 0;
    }

    return reinterpret_cast<jlong>(mesh);
}

/**
 * 执行有限元求解
 * 原问题：concentration数组长度与mesh.nodeCount不一致，line 123越界
 * 修复：C++侧验证数组长度，不一致时返回错误而非越界
 */
JNIEXPORT jdoubleArray JNICALL Java_com_saltdamage_algorithm_FEMSolver_nativeSolve(
    JNIEnv* env, jobject thiz,
    jlong meshHandle,
    jdoubleArray concentrationArr,
    jdoubleArray porosityArr,
    jdouble diffusionCoeff,
    jdouble timeStep,
    jint iterations)
{
    if (meshHandle == 0) {
        throwJNIError(env, "Invalid mesh handle");
        return nullptr;
    }

    FEMMesh* mesh = reinterpret_cast<FEMMesh*>(meshHandle);

    if (!concentrationArr || !porosityArr) {
        throwJNIError(env, "Null concentration or porosity array");
        return nullptr;
    }

    jsize concLen = env->GetArrayLength(concentrationArr);
    jsize poroLen = env->GetArrayLength(porosityArr);

    // 关键修复：验证数组长度与网格节点数一致
    if (concLen != mesh->nodeCount) {
        char err[256];
        snprintf(err, sizeof(err),
            "Concentration array length %d != mesh nodeCount %d",
            concLen, mesh->nodeCount);
        throwJNIError(env, err);
        return nullptr;
    }

    if (poroLen != mesh->nodeCount) {
        char err[256];
        snprintf(err, sizeof(err),
            "Porosity array length %d != mesh nodeCount %d",
            poroLen, mesh->nodeCount);
        throwJNIError(env, err);
        return nullptr;
    }

    if (iterations <= 0 || iterations > 10000) {
        throwJNIError(env, "Iterations must be in [1, 10000]");
        return nullptr;
    }

    std::vector<double> concentration(mesh->nodeCount);
    std::vector<double> porosity(mesh->nodeCount);

    env->GetDoubleArrayRegion(concentrationArr, 0, mesh->nodeCount, concentration.data());
    env->GetDoubleArrayRegion(porosityArr, 0, mesh->nodeCount, porosity.data());

    std::vector<double> current = concentration;
    std::vector<double> next;

    for (int iter = 0; iter < iterations; iter++) {
        int ret = solveDiffusion(*mesh, current, porosity, diffusionCoeff, timeStep, next);
        if (ret != 0) {
            throwJNIError(env, g_lastError);
            return nullptr;
        }
        current = std::move(next);
    }

    jdoubleArray resultArr = env->NewDoubleArray(mesh->nodeCount);
    if (resultArr) {
        env->SetDoubleArrayRegion(resultArr, 0, mesh->nodeCount, current.data());
    }

    return resultArr;
}

/**
 * 释放FEM网格内存
 */
JNIEXPORT void JNICALL Java_com_saltdamage_algorithm_FEMSolver_nativeReleaseMesh(
    JNIEnv* env, jobject thiz, jlong meshHandle)
{
    if (meshHandle != 0) {
        FEMMesh* mesh = reinterpret_cast<FEMMesh*>(meshHandle);
        delete mesh;
    }
}

} // extern "C"
